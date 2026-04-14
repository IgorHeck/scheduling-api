package com.scheduling.api.appointment.service;

import com.scheduling.api.appointment.dto.AppointmentResponse;
import com.scheduling.api.appointment.dto.CalendarDayResponse;
import com.scheduling.api.appointment.dto.CreateAppointmentRequest;
import com.scheduling.api.appointment.dto.RescheduleRequest;
import com.scheduling.api.appointment.model.Appointment;
import com.scheduling.api.appointment.model.AppointmentStatus;
import com.scheduling.api.appointment.repository.AppointmentRepository;
import com.scheduling.api.company.model.Company;
import com.scheduling.api.company.service.CompanyService;
import com.scheduling.api.user.model.Role;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CompanyService companyService;
    private final UserService userService;

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest req, User currentUser) {
        Company company = companyService.findCompanyById(req.getCompanyId());

        if(!company.isAllowClienteBooking() && currentUser.getRole() == Role.CLIENT) {
            throw null;
        }

        List<Appointment> conflicts = appointmentRepository
                .findConflics(req.getProfessionalId(), req.getStartAt(), req.getEndAt());
        if(conflicts.isEmpty()) {
            throw null;
        }

        User professional = userService.findUserById(req.getProfessionalId());

        AppointmentStatus status = (currentUser.getRole() == Role.ADMIN
                || currentUser.getRole() == Role.MANAGER)
                ? AppointmentStatus.CONFIRMED
                : AppointmentStatus.PENDING;

        Appointment a = Appointment.builder()
                .company(company)
                .client(currentUser)
                .professional(professional)
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .status(status)
                .notes(req.getNotes())
                .build();
        return toResponse(appointmentRepository.save(a));
    }

    public List<AppointmentResponse> findByCurrentUser(Long userId) {
        return appointmentRepository.findByClientIdOrderByStartAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    public AppointmentResponse findById(Long id) {
        return toResponse(findAppointmentById(id));
    }

    public List<AppointmentResponse> findByCompany(Long companyId, LocalDateTime start, LocalDateTime end) {
        return appointmentRepository.
                findByCompanyIdAndStartAtBetweenOrderByStartAt(companyId, start, end)
                .stream().map(this::toResponse).toList();
    }

    public List<AppointmentResponse> findPendingByCompany(Long companyId) {
        return appointmentRepository
                .findByCompanyIdAndStatus(companyId, AppointmentStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AppointmentResponse confirm(Long id) {
        Appointment a = findAppointmentById(id);
        if (a.getStatus() != AppointmentStatus.PENDING) {
            throw null;
        }
        a.setStatus(AppointmentStatus.CONFIRMED);
        return toResponse(appointmentRepository.save(a));
    }

    @Transactional
    public AppointmentResponse cancel(Long id, String reason) {
        Appointment a = findAppointmentById(id);
        if (a.getStatus() == AppointmentStatus.COMPLETED) {
            throw null;
        }
        a.setStatus(AppointmentStatus.CANCELLED);
        a.setCancelReason(reason);
        return toResponse(appointmentRepository.save(a));
    }

    @Transactional
    public AppointmentResponse reschedule(Long id, RescheduleRequest req) {
        Appointment a = findAppointmentById(id);
        List<Appointment> conflicts = appointmentRepository
                .findConflics(a.getProfessional().getId(),req.getNewStartAt(),req.getNewEndAt());
        if(conflicts.isEmpty()) {
            throw null;
        }
        a.setStartAt(req.getNewStartAt());
        a.setEndAt(req.getNewEndAt());
        a.setStatus(AppointmentStatus.PENDING);
        return toResponse(appointmentRepository.save(a));
    }

    public List<CalendarDayResponse> getCalendarMonth(Long companyId, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atEndOfMonth().atTime(23,59);
        List<Object[]> rows = appointmentRepository.countByDayInMonth(companyId, start, end);
        return rows.stream().map(r -> CalendarDayResponse.builder()
                .date(((java.sql.Date) r[0]).toLocalDate())
                .totalAppointments((Long) r[1])
                .status("partial")
                .build()).toList();
    }

    private Appointment findAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .orElse(null);
    }

    private AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
                .id(a.getId())
                .companyId(a.getCompany().getId())
                .companyName(a.getCompany().getName())
                .clientId(a.getClient().getId())
                .clientName(a.getClient().getName())
                .professionalId(a.getProfessional().getId())
                .professionalName(a.getProfessional().getName())
                .startAt(a.getStartAt())
                .endAt(a.getEndAt())
                .status(a.getStatus())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .build();
    }

}
