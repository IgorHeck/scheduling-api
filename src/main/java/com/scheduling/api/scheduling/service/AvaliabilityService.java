package com.scheduling.api.scheduling.service;

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

    public List<AvailableSlotResponse> getAvailableSlots(Long companyId, LocalDate date) {
        List<Schedule> schedules= scheduleRepository
                .findByCompanyIdAndActiveTrue(companyId).stream()
                .filter(s -> s.getDayOfWeek() == date.getDayOfWeek())
                .toList();

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(23, 59);
        List<ScheduleBlock> blocks = blockRepository.findOverLapping(companyId, dayStart, dayEnd);

        List<AvailableSlotResponse> slots = new ArrayList<>();
        for (Schedule schedule : schedules) {
            LocalDateTime cursor = date.atTime(schedule.getStartTime());
            LocalDateTime end = date.atTime(schedule.getEndTime());
            while(cursor.plusMinutes(schedule.getSlotDurationMinutes()).compareTo(end) < 0) {
                LocalDateTime slotsEnd = cursor.plusMinutes(schedule.getSlotDurationMinutes());
                boolean blocked = isBlocked(cursor, slotsEnd, blocks);
                slots.add(AvailableSlotResponse.builder()
                        .startAt(cursor)
                        .endAt(slotsEnd)
                        .professionalId(schedule.getProfessional().getId())
                        .professionalName(schedule.getProfessional().getName())
                        .available(!blocked)
                        .build());
                cursor = slotsEnd;

            }
        }
        return slots;
    }


    private boolean isBlocked(LocalDateTime start, LocalDateTime end, List<ScheduleBlock> blocks) {
        return blocks.stream().anyMatch(b ->
                b.getStartAt().isBefore(end) && b.getEndAt().isAfter(start));
    }
}
