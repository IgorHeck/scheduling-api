package com.scheduling.api.scheduling.service;

import com.scheduling.api.appointment.model.Appointment;
import com.scheduling.api.appointment.model.AppointmentStatus;
import com.scheduling.api.appointment.repository.AppointmentRepository;
import com.scheduling.api.scheduling.dto.AvailableSlotResponse;
import com.scheduling.api.scheduling.model.Schedule;
import com.scheduling.api.scheduling.model.ScheduleBlock;
import com.scheduling.api.scheduling.repository.ScheduleBlockRepository;
import com.scheduling.api.scheduling.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvaliabilityService {

    private final ScheduleBlockRepository blockRepository;
    private final ScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;

    public List<AvailableSlotResponse> getAvailableSlots(Long companyId, LocalDate date) {
        List<Schedule> schedules = scheduleRepository
                .findByCompanyIdAndActiveTrue(companyId).stream()
                .filter(s -> s.getDayOfWeek() == date.getDayOfWeek())
                .toList();

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd   = date.atTime(23, 59);

        List<ScheduleBlock> blocks = blockRepository.findOverLapping(companyId, dayStart, dayEnd);

        List<Appointment> appointments = appointmentRepository
                .findByCompanyIdAndStartAtBetweenOrderByStartAt(companyId, dayStart, dayEnd)
                .stream()
                .filter(a -> a.getStatus() == AppointmentStatus.PENDING
                        || a.getStatus() == AppointmentStatus.CONFIRMED)
                .toList();

        List<AvailableSlotResponse> slots = new ArrayList<>();

        for (Schedule schedule : schedules) {
            LocalDateTime cursor = date.atTime(schedule.getStartTime());
            LocalDateTime end    = date.atTime(schedule.getEndTime());

            LocalDateTime lunchStart = schedule.getLunchStart() != null
                    ? date.atTime(schedule.getLunchStart()) : null;
            LocalDateTime lunchEnd = schedule.getLunchEnd() != null
                    ? date.atTime(schedule.getLunchEnd()) : null;

            while (cursor.plusMinutes(schedule.getSlotDurationMinutes()).compareTo(end) <= 0) {
                LocalDateTime slotEnd = cursor.plusMinutes(schedule.getSlotDurationMinutes());

                boolean duringLunch = lunchStart != null && lunchEnd != null
                        && cursor.isBefore(lunchEnd) && slotEnd.isAfter(lunchStart);

                boolean blocked    = isBlocked(cursor, slotEnd, blocks);
                boolean hasBooking = hasAppointment(cursor, slotEnd, appointments);

                if (!duringLunch) {
                    slots.add(AvailableSlotResponse.builder()
                            .startAt(cursor)
                            .endAt(slotEnd)
                            .professionalId(schedule.getProfessional().getId())
                            .professionalName(schedule.getProfessional().getName())
                            .available(!blocked && !hasBooking)
                            .build());
                }

                cursor = slotEnd;
            }
        }

        return slots;
    }

    private boolean isBlocked(LocalDateTime start, LocalDateTime end, List<ScheduleBlock> blocks) {
        return blocks.stream().anyMatch(b ->
                b.getStartAt().isBefore(end) && b.getEndAt().isAfter(start));
    }

    private boolean hasAppointment(LocalDateTime start, LocalDateTime end, List<Appointment> appointments) {
        return appointments.stream().anyMatch(a ->
                a.getStartAt().isBefore(end) && a.getEndAt().isAfter(start));
    }
}