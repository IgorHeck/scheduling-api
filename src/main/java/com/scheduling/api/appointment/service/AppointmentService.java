package com.scheduling.api.appointment.service;

import com.scheduling.api.appointment.dto.*;
import com.scheduling.api.appointment.model.Appointment;
import com.scheduling.api.appointment.model.AppointmentStatus;
import com.scheduling.api.appointment.repository.AppointmentRepository;
import com.scheduling.api.company.model.Company;
import com.scheduling.api.company.service.CompanyService;
import com.scheduling.api.exception.BusinessException;
import com.scheduling.api.exception.ResourceNotFoundException;
import com.scheduling.api.scheduling.repository.ScheduleRepository;
import com.scheduling.api.scheduling.service.AvaliabilityService;
import com.scheduling.api.user.model.Role;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.repository.UserRepository;
import com.scheduling.api.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CompanyService companyService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AvaliabilityService avaliabilityService;
    private final ScheduleRepository scheduleRepository;

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest req, User currentUser) {
        Company company = companyService.findCompanyById(req.getCompanyId());

        if(!company.isAllowClienteBooking() && currentUser.getRole() == Role.CLIENT) {
            throw new BusinessException("Agendamentos públicos estão desativados para esta empresa.");
        }

        List<Appointment> conflicts = appointmentRepository
                .findConflics(req.getProfessionalId(), req.getStartAt(), req.getEndAt());
        if(!conflicts.isEmpty()) {
            throw new BusinessException("Horário indisponível. Por favor escolha outro slot.");
        }

        User professional = userService.findUserById(req.getProfessionalId());
        User client = (req.getClientId() != null)
                ? userService.findUserById(req.getClientId())
                : currentUser;

        AppointmentStatus status = (currentUser.getRole() == Role.ADMIN
                || currentUser.getRole() == Role.MANAGER)
                ? AppointmentStatus.CONFIRMED
                : AppointmentStatus.PENDING;

        Appointment a = Appointment.builder()
                .company(company)
                .client(client)
                .professional(professional)
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .status(status)
                .notes(req.getNotes())
                .build();
        return toResponse(appointmentRepository.save(a));
    }

    @Transactional
    public AppointmentResponse createPublic(PublicAppointmentRequest req) {
        Company company = companyService.findCompanyById(req.getCompanyId());

        if (!company.isAllowClienteBooking()) {
            throw new BusinessException("Esta empresa não está aceitando agendamentos no momento.");
        }

        List<Appointment> conflicts = appointmentRepository
                .findConflics(req.getProfessionalId(), req.getStartAt(), req.getEndAt());
        if (!conflicts.isEmpty()) {
            throw new BusinessException("Horário indisponível. Por favor escolha outro slot.");
        }

        User client = userRepository.findByEmail(req.getClientEmail())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .name(req.getClientName())
                            .email(req.getClientEmail())
                            .phone(req.getClientPhone())
                            .password(passwordEncoder.encode(
                                    java.util.UUID.randomUUID().toString()
                            ))
                            .role(Role.CLIENT)
                            .active(true)
                            .build();
                    return userRepository.save(newUser);
                });

        User professional = userService.findUserById(req.getProfessionalId());

        Appointment a = Appointment.builder()
                .company(company)
                .client(client)
                .professional(professional)
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .status(AppointmentStatus.PENDING)
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
            throw new BusinessException("Apenas agendamentos PENDENTES podem ser confirmados.");
        }
        a.setStatus(AppointmentStatus.CONFIRMED);
        return toResponse(appointmentRepository.save(a));
    }

    @Transactional
    public AppointmentResponse cancel(Long id, String reason) {
        Appointment a = findAppointmentById(id);
        if (a.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessException("Agendamentos concluídos não podem ser cancelados.");
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
        if(!conflicts.isEmpty()) {
            throw new BusinessException("Novo horário indisponível.");
        }
        a.setStartAt(req.getNewStartAt());
        a.setEndAt(req.getNewEndAt());
        a.setStatus(AppointmentStatus.PENDING);
        return toResponse(appointmentRepository.save(a));
    }

    public List<CalendarDayResponse> getCalendarMonth(Long companyId, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end   = month.atEndOfMonth().atTime(23, 59);

        List<Object[]> rows = appointmentRepository.countByDayInMonth(companyId, start, end);
        Map<LocalDate, Long> appointmentsByDay = rows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        r -> ((java.sql.Date) r[0]).toLocalDate(),
                        r -> (Long) r[1]
                ));

        List<CalendarDayResponse> result = new ArrayList<>();
        int daysInMonth = month.lengthOfMonth();

        for (int day = 1; day <= daysInMonth; day++) {
            java.time.LocalDate date = month.atDay(day);

            if (date.isBefore(java.time.LocalDate.now())) continue;

            List<com.scheduling.api.scheduling.dto.AvailableSlotResponse> slots =
                    avaliabilityService.getAvailableSlots(companyId, date);

            if (slots.isEmpty()) continue;

            long totalSlots        = slots.size();
            long availableSlots    = slots.stream().filter(s -> s.isAvailable()).count();
            long totalAppointments = appointmentsByDay.getOrDefault(date, 0L);

            String status;
            if (availableSlots == 0) {
                status = "full";
            } else if (totalAppointments == 0) {
                status = "available";
            } else {
                status = "partial";
            }

            result.add(CalendarDayResponse.builder()
                    .date(date)
                    .totalAppointments(totalAppointments)
                    .status(status)
                    .build());
        }

        return result;
    }

    private Appointment findAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado: " + id));
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
