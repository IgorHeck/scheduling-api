package com.scheduling.api.appointment.service;

import com.scheduling.api.appointment.dto.*;
import com.scheduling.api.appointment.model.Appointment;
import com.scheduling.api.appointment.model.AppointmentStatus;
import com.scheduling.api.appointment.repository.AppointmentRepository;
import com.scheduling.api.company.model.Company;
import com.scheduling.api.company.service.CompanyService;
import com.scheduling.api.exception.BusinessException;
import com.scheduling.api.exception.ResourceNotFoundException;
import com.scheduling.api.mail.MailService;
import com.scheduling.api.notification.NotificationEvent;
import com.scheduling.api.notification.NotificationService;
import com.scheduling.api.scheduling.repository.ScheduleRepository;
import com.scheduling.api.scheduling.service.AvaliabilityService;
import com.scheduling.api.user.model.Role;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.repository.UserRepository;
import com.scheduling.api.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
    private final MailService mailService;
    private final NotificationService notificationService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    // ── Criação ─────────────────────────────────────────────────────────────

    @CacheEvict(value = "slots", allEntries = true)
    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest req, User currentUser) {
        Company company = companyService.findCompanyById(req.getCompanyId());

        if (!company.isAllowClienteBooking() && currentUser.getRole() == Role.CLIENT) {
            throw new BusinessException("Agendamentos públicos estão desativados para esta empresa.");
        }

        List<Appointment> conflicts = appointmentRepository
                .findConflics(req.getProfessionalId(), req.getStartAt(), req.getEndAt());
        if (!conflicts.isEmpty()) {
            throw new BusinessException("Horário indisponível. Por favor escolha outro slot.");
        }

        User professional = userService.findUserById(req.getProfessionalId());
        assertProfessionalBelongsToCompany(professional, company.getId());

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
        Appointment saved = appointmentRepository.save(a);
        if (status == AppointmentStatus.CONFIRMED) {
            mailService.sendAppointmentConfirmed(saved);
            notificationService.notifyUser(saved.getClient().getEmail(), new NotificationEvent(
                    "APPOINTMENT_CONFIRMED",
                    "Seu agendamento com " + saved.getProfessional().getName()
                            + " foi confirmado para " + saved.getStartAt().format(DT_FMT),
                    saved.getId()
            ));
        } else {
            mailService.sendAppointmentPending(saved);
            notificationService.notifyCompanyStaff(saved.getCompany().getId(), new NotificationEvent(
                    "NEW_PENDING",
                    saved.getClient().getName() + " solicitou um agendamento para "
                            + saved.getStartAt().format(DT_FMT),
                    saved.getId()
            ));
        }
        return toResponse(saved);
    }

    @CacheEvict(value = "slots", allEntries = true)
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

        User professional = userService.findUserById(req.getProfessionalId());
        assertProfessionalBelongsToCompany(professional, company.getId());

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

        Appointment a = Appointment.builder()
                .company(company)
                .client(client)
                .professional(professional)
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .status(AppointmentStatus.PENDING)
                .notes(req.getNotes())
                .build();

        Appointment saved = appointmentRepository.save(a);
        mailService.sendAppointmentPending(saved);
        notificationService.notifyCompanyStaff(saved.getCompany().getId(), new NotificationEvent(
                "NEW_PENDING",
                saved.getClient().getName() + " solicitou um agendamento para "
                        + saved.getStartAt().format(DT_FMT),
                saved.getId()
        ));
        return toResponse(saved);
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    public Page<AppointmentResponse> findByCurrentUser(Long userId, Pageable pageable) {
        return appointmentRepository.findByClientIdOrderByStartAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    public AppointmentResponse findById(Long id) {
        return toResponse(findAppointmentById(id));
    }

    public Page<AppointmentResponse> findByCompany(Long companyId, LocalDateTime start,
                                                    LocalDateTime end, Pageable pageable,
                                                    User currentUser) {
        assertCompanyAccess(currentUser, companyId);
        return appointmentRepository
                .findByCompanyIdAndStartAtBetweenOrderByStartAt(companyId, start, end, pageable)
                .map(this::toResponse);
    }

    public List<AppointmentResponse> findPendingByCompany(Long companyId, User currentUser) {
        assertCompanyAccess(currentUser, companyId);
        return appointmentRepository
                .findByCompanyIdAndStatus(companyId, AppointmentStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    // ── Transições de status ─────────────────────────────────────────────────

    @Transactional
    public AppointmentResponse confirm(Long id, User currentUser) {
        Appointment a = findAppointmentById(id);
        assertCompanyAccess(currentUser, a.getCompany().getId());
        if (a.getStatus() != AppointmentStatus.PENDING) {
            throw new BusinessException("Apenas agendamentos PENDENTES podem ser confirmados.");
        }
        a.setStatus(AppointmentStatus.CONFIRMED);
        Appointment saved = appointmentRepository.save(a);
        mailService.sendAppointmentConfirmed(saved);
        notificationService.notifyUser(saved.getClient().getEmail(), new NotificationEvent(
                "APPOINTMENT_CONFIRMED",
                "Seu agendamento com " + saved.getProfessional().getName()
                        + " foi confirmado para " + saved.getStartAt().format(DT_FMT),
                saved.getId()
        ));
        return toResponse(saved);
    }

    @CacheEvict(value = "slots", allEntries = true)
    @Transactional
    public AppointmentResponse cancel(Long id, String reason, User currentUser) {
        Appointment a = findAppointmentById(id);

        switch (currentUser.getRole()) {
            case CLIENT -> {
                if (!a.getClient().getId().equals(currentUser.getId())) {
                    throw new AccessDeniedException("Você só pode cancelar seus próprios agendamentos.");
                }
            }
            case MANAGER -> assertCompanyAccess(currentUser, a.getCompany().getId());
            // ADMIN: pode cancelar qualquer agendamento
        }

        if (a.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessException("Agendamentos concluídos não podem ser cancelados.");
        }
        a.setStatus(AppointmentStatus.CANCELLED);
        a.setCancelReason(reason);
        Appointment saved = appointmentRepository.save(a);
        mailService.sendAppointmentCancelled(saved, reason);
        // Notifica o cliente apenas quando cancelado por outra pessoa (ADMIN/MANAGER)
        if (currentUser.getRole() != Role.CLIENT) {
            notificationService.notifyUser(saved.getClient().getEmail(), new NotificationEvent(
                    "APPOINTMENT_CANCELLED",
                    "Seu agendamento de " + saved.getStartAt().format(DT_FMT) + " foi cancelado.",
                    saved.getId()
            ));
        }
        return toResponse(saved);
    }

    @Transactional
    public AppointmentResponse complete(Long id, User currentUser) {
        Appointment a = findAppointmentById(id);
        assertCompanyAccess(currentUser, a.getCompany().getId());
        if (a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("Apenas agendamentos CONFIRMADOS podem ser concluídos.");
        }
        a.setStatus(AppointmentStatus.COMPLETED);
        return toResponse(appointmentRepository.save(a));
    }

    @CacheEvict(value = "slots", allEntries = true)
    @Transactional
    public AppointmentResponse reschedule(Long id, RescheduleRequest req, User currentUser) {
        Appointment a = findAppointmentById(id);

        switch (currentUser.getRole()) {
            case CLIENT -> {
                if (!a.getClient().getId().equals(currentUser.getId())) {
                    throw new AccessDeniedException("Você só pode remarcar seus próprios agendamentos.");
                }
            }
            case MANAGER -> assertCompanyAccess(currentUser, a.getCompany().getId());
        }

        List<Appointment> conflicts = appointmentRepository
                .findConflics(a.getProfessional().getId(), req.getNewStartAt(), req.getNewEndAt());
        if (!conflicts.isEmpty()) {
            throw new BusinessException("Novo horário indisponível.");
        }
        a.setStartAt(req.getNewStartAt());
        a.setEndAt(req.getNewEndAt());
        a.setStatus(AppointmentStatus.PENDING);
        Appointment saved = appointmentRepository.save(a);
        mailService.sendAppointmentRescheduled(saved);
        notificationService.notifyCompanyStaff(saved.getCompany().getId(), new NotificationEvent(
                "NEW_PENDING",
                saved.getClient().getName() + " remarcou o agendamento para "
                        + saved.getStartAt().format(DT_FMT),
                saved.getId()
        ));
        // Se o CLIENT remarcou, o próprio cliente não precisa de notificação browser
        // Se ADMIN/MANAGER remarcou, notifica o cliente
        if (currentUser.getRole() != Role.CLIENT) {
            notificationService.notifyUser(saved.getClient().getEmail(), new NotificationEvent(
                    "APPOINTMENT_RESCHEDULED",
                    "Seu agendamento foi remarcado para " + saved.getStartAt().format(DT_FMT),
                    saved.getId()
            ));
        }
        return toResponse(saved);
    }

    // ── Job de conclusão automática ──────────────────────────────────────────

    /** Chamado pelo job agendado — conclui em lote todos os CONFIRMED com endAt no passado. */
    @Transactional
    public int autoComplete() {
        List<Appointment> expired = appointmentRepository
                .findByStatusAndEndAtBefore(AppointmentStatus.CONFIRMED, LocalDateTime.now());
        expired.forEach(a -> a.setStatus(AppointmentStatus.COMPLETED));
        if (!expired.isEmpty()) appointmentRepository.saveAll(expired);
        return expired.size();
    }

    // ── Calendário ───────────────────────────────────────────────────────────

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
            LocalDate date = month.atDay(day);
            if (date.isBefore(LocalDate.now())) continue;

            List<com.scheduling.api.scheduling.dto.AvailableSlotResponse> slots =
                    avaliabilityService.getAvailableSlots(companyId, date);
            if (slots.isEmpty()) continue;

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

    // ── Guardas de autorização ───────────────────────────────────────────────

    /**
     * ADMIN → passa sempre.
     * MANAGER → companyId deve coincidir com a empresa vinculada ao manager.
     */
    private void assertCompanyAccess(User currentUser, Long companyId) {
        if (currentUser.getRole() == Role.ADMIN) return;
        if (currentUser.getRole() == Role.MANAGER) {
            Long managerCompany = currentUser.getCompany() != null
                    ? currentUser.getCompany().getId() : null;
            if (!companyId.equals(managerCompany)) {
                throw new AccessDeniedException(
                        "Acesso negado: você só pode gerenciar agendamentos da sua empresa.");
            }
            return;
        }
        // CLIENT não chama rotas com essa guarda — sinaliza configuração incorreta
        throw new AccessDeniedException("Acesso negado.");
    }

    /**
     * Verifica que o profissional pertence à empresa informada no agendamento.
     */
    private void assertProfessionalBelongsToCompany(User professional, Long companyId) {
        if (professional.getCompany() == null
                || !Objects.equals(professional.getCompany().getId(), companyId)) {
            throw new BusinessException(
                    "O profissional selecionado não pertence à empresa informada.");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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
