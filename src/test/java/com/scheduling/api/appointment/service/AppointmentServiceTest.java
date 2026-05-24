package com.scheduling.api.appointment.service;

import com.scheduling.api.appointment.dto.AppointmentResponse;
import com.scheduling.api.appointment.dto.CreateAppointmentRequest;
import com.scheduling.api.appointment.dto.RescheduleRequest;
import com.scheduling.api.appointment.model.Appointment;
import com.scheduling.api.appointment.model.AppointmentStatus;
import com.scheduling.api.appointment.repository.AppointmentRepository;
import com.scheduling.api.company.model.Company;
import com.scheduling.api.company.service.CompanyService;
import com.scheduling.api.exception.BusinessException;
import com.scheduling.api.mail.MailService;
import com.scheduling.api.notification.NotificationEvent;
import com.scheduling.api.notification.NotificationService;
import com.scheduling.api.scheduling.repository.ScheduleRepository;
import com.scheduling.api.scheduling.service.AvaliabilityService;
import com.scheduling.api.user.model.Role;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.repository.UserRepository;
import com.scheduling.api.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private CompanyService        companyService;
    @Mock private UserService           userService;
    @Mock private UserRepository        userRepository;
    @Mock private PasswordEncoder       passwordEncoder;
    @Mock private AvaliabilityService   avaliabilityService;
    @Mock private ScheduleRepository    scheduleRepository;
    @Mock private MailService           mailService;
    @Mock private NotificationService   notificationService;

    @InjectMocks
    private AppointmentService service;

    // ── fixtures ─────────────────────────────────────────────────────────────

    private Company company;
    private User    adminUser;
    private User    clientUser;
    private User    professional;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .id(1L).name("Clínica Test")
                .allowClienteBooking(true).active(true)
                .build();

        adminUser = User.builder()
                .id(1L).name("Admin").email("admin@test.com")
                .password("hashed").role(Role.ADMIN).active(true)
                .build();

        clientUser = User.builder()
                .id(2L).name("Cliente Silva").email("client@test.com")
                .password("hashed").role(Role.CLIENT).active(true)
                .build();

        professional = User.builder()
                .id(3L).name("Dr. Smith").email("prof@test.com")
                .password("hashed").role(Role.MANAGER).active(true)
                .company(company)
                .build();
    }

    private Appointment buildAppointment(AppointmentStatus status) {
        return Appointment.builder()
                .id(10L).company(company)
                .client(clientUser).professional(professional)
                .startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(1).plusHours(1))
                .status(status)
                .build();
    }

    private CreateAppointmentRequest buildCreateRequest() {
        CreateAppointmentRequest req = new CreateAppointmentRequest();
        req.setCompanyId(1L);
        req.setProfessionalId(3L);
        req.setStartAt(LocalDateTime.now().plusDays(1));
        req.setEndAt(LocalDateTime.now().plusDays(1).plusHours(1));
        return req;
    }

    // ── create() ─────────────────────────────────────────────────────────────

    @Test
    void create_asClient_createsPendingAndNotifiesCompanyStaff() {
        Appointment saved = buildAppointment(AppointmentStatus.PENDING);

        when(companyService.findCompanyById(1L)).thenReturn(company);
        when(appointmentRepository.findConflics(anyLong(), any(), any())).thenReturn(List.of());
        when(userService.findUserById(3L)).thenReturn(professional);
        when(appointmentRepository.save(any())).thenReturn(saved);

        AppointmentResponse result = service.create(buildCreateRequest(), clientUser);

        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        verify(mailService).sendAppointmentPending(saved);
        verify(notificationService).notifyCompanyStaff(eq(1L),
                argThat(e -> "NEW_PENDING".equals(e.type())));
        verify(notificationService, never()).notifyUser(any(), any());
    }

    @Test
    void create_asAdmin_createsConfirmedAndNotifiesClient() {
        Appointment saved = buildAppointment(AppointmentStatus.CONFIRMED);

        when(companyService.findCompanyById(1L)).thenReturn(company);
        when(appointmentRepository.findConflics(anyLong(), any(), any())).thenReturn(List.of());
        when(userService.findUserById(3L)).thenReturn(professional);
        when(appointmentRepository.save(any())).thenReturn(saved);

        AppointmentResponse result = service.create(buildCreateRequest(), adminUser);

        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(mailService).sendAppointmentConfirmed(saved);
        verify(notificationService).notifyUser(eq("client@test.com"),
                argThat(e -> "APPOINTMENT_CONFIRMED".equals(e.type())));
        verify(notificationService, never()).notifyCompanyStaff(any(), any());
    }

    @Test
    void create_withConflict_throwsBusinessExceptionAndDoesNotSave() {
        Appointment conflicting = buildAppointment(AppointmentStatus.CONFIRMED);

        when(companyService.findCompanyById(1L)).thenReturn(company);
        when(appointmentRepository.findConflics(anyLong(), any(), any()))
                .thenReturn(List.of(conflicting));

        assertThatThrownBy(() -> service.create(buildCreateRequest(), clientUser))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("indisponível");

        verify(appointmentRepository, never()).save(any());
        verifyNoInteractions(notificationService, mailService);
    }

    @Test
    void create_professionalFromAnotherCompany_throwsBusinessException() {
        Company otherCompany = Company.builder().id(99L).name("Outro").build();
        User foreignPro = User.builder()
                .id(99L).name("Foreign Pro").email("foreign@test.com")
                .role(Role.MANAGER).active(true).company(otherCompany)
                .build();

        CreateAppointmentRequest req = buildCreateRequest();
        req.setProfessionalId(99L);

        when(companyService.findCompanyById(1L)).thenReturn(company);
        when(appointmentRepository.findConflics(anyLong(), any(), any())).thenReturn(List.of());
        when(userService.findUserById(99L)).thenReturn(foreignPro);

        assertThatThrownBy(() -> service.create(req, adminUser))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pertence à empresa");

        verify(appointmentRepository, never()).save(any());
    }

    // ── confirm() ────────────────────────────────────────────────────────────

    @Test
    void confirm_pendingAppointment_setsConfirmedAndNotifiesClient() {
        Appointment pending = buildAppointment(AppointmentStatus.PENDING);

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(appointmentRepository.save(any())).thenReturn(pending);

        service.confirm(10L, adminUser);

        assertThat(pending.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(mailService).sendAppointmentConfirmed(pending);
        verify(notificationService).notifyUser(eq("client@test.com"),
                argThat(e -> "APPOINTMENT_CONFIRMED".equals(e.type())));
    }

    @Test
    void confirm_alreadyConfirmedAppointment_throwsBusinessException() {
        Appointment confirmed = buildAppointment(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.confirm(10L, adminUser))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PENDENTES");
    }

    // ── cancel() ─────────────────────────────────────────────────────────────

    @Test
    void cancel_byAdmin_cancelsAndNotifiesClient() {
        Appointment a = buildAppointment(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(a));
        when(appointmentRepository.save(any())).thenReturn(a);

        service.cancel(10L, "Motivo teste", adminUser);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getCancelReason()).isEqualTo("Motivo teste");
        verify(notificationService).notifyUser(eq("client@test.com"),
                argThat(e -> "APPOINTMENT_CANCELLED".equals(e.type())));
    }

    @Test
    void cancel_byClient_cancelsOwnAppointmentWithoutSseToSelf() {
        Appointment a = buildAppointment(AppointmentStatus.CONFIRMED);
        // clientUser (id=2) is the client of this appointment

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(a));
        when(appointmentRepository.save(any())).thenReturn(a);

        service.cancel(10L, "Não vou mais", clientUser);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(notificationService, never()).notifyUser(any(), any());
    }

    @Test
    void cancel_completedAppointment_throwsBusinessException() {
        Appointment completed = buildAppointment(AppointmentStatus.COMPLETED);

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.cancel(10L, "motivo", adminUser))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("concluídos não podem ser cancelados");
    }

    @Test
    void cancel_byClientOnAnotherClientsAppointment_throwsAccessDeniedException() {
        User intruder = User.builder()
                .id(99L).name("Intruso").email("intruder@test.com")
                .role(Role.CLIENT).build();
        Appointment a = buildAppointment(AppointmentStatus.CONFIRMED);
        // a.getClient() is clientUser (id=2), intruder (id=99) tries to cancel

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.cancel(10L, "hack", intruder))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancel_byManagerFromAnotherCompany_throwsAccessDeniedException() {
        Company otherCompany = Company.builder().id(99L).name("Outra").build();
        User foreignManager = User.builder()
                .id(5L).name("Mgr Outro").email("mgr@other.com")
                .role(Role.MANAGER).company(otherCompany).build();

        Appointment a = buildAppointment(AppointmentStatus.CONFIRMED);
        // appointment.company.id = 1L, foreignManager.company.id = 99L

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.cancel(10L, "motivo", foreignManager))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── reschedule() ─────────────────────────────────────────────────────────

    @Test
    void reschedule_byAdmin_sendsBothSSENotifications() {
        Appointment a = buildAppointment(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(a));
        when(appointmentRepository.findConflics(anyLong(), any(), any())).thenReturn(List.of());
        when(appointmentRepository.save(any())).thenReturn(a);

        RescheduleRequest req = new RescheduleRequest();
        req.setNewStartAt(LocalDateTime.now().plusDays(3));
        req.setNewEndAt(LocalDateTime.now().plusDays(3).plusHours(1));

        service.reschedule(10L, req, adminUser);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        verify(notificationService).notifyCompanyStaff(eq(1L),
                argThat(e -> "NEW_PENDING".equals(e.type())));
        verify(notificationService).notifyUser(eq("client@test.com"),
                argThat(e -> "APPOINTMENT_RESCHEDULED".equals(e.type())));
    }

    @Test
    void reschedule_byClient_sendsOnlyNewPendingToStaff() {
        Appointment a = buildAppointment(AppointmentStatus.CONFIRMED);
        // clientUser (id=2) is the client

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(a));
        when(appointmentRepository.findConflics(anyLong(), any(), any())).thenReturn(List.of());
        when(appointmentRepository.save(any())).thenReturn(a);

        RescheduleRequest req = new RescheduleRequest();
        req.setNewStartAt(LocalDateTime.now().plusDays(3));
        req.setNewEndAt(LocalDateTime.now().plusDays(3).plusHours(1));

        service.reschedule(10L, req, clientUser);

        verify(notificationService).notifyCompanyStaff(eq(1L),
                argThat(e -> "NEW_PENDING".equals(e.type())));
        // CLIENT que remarcou NÃO recebe APPOINTMENT_RESCHEDULED para si mesmo
        verify(notificationService, never()).notifyUser(eq("client@test.com"),
                argThat(e -> "APPOINTMENT_RESCHEDULED".equals(e.type())));
    }

    @Test
    void reschedule_withConflict_throwsBusinessException() {
        Appointment a = buildAppointment(AppointmentStatus.CONFIRMED);
        Appointment conflicting = buildAppointment(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(a));
        when(appointmentRepository.findConflics(anyLong(), any(), any()))
                .thenReturn(List.of(conflicting));

        RescheduleRequest req = new RescheduleRequest();
        req.setNewStartAt(LocalDateTime.now().plusDays(3));
        req.setNewEndAt(LocalDateTime.now().plusDays(3).plusHours(1));

        assertThatThrownBy(() -> service.reschedule(10L, req, adminUser))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("indisponível");
    }

    // ── autoComplete() ───────────────────────────────────────────────────────

    @Test
    void autoComplete_completesAllExpiredConfirmedAppointments() {
        Appointment expired1 = buildAppointment(AppointmentStatus.CONFIRMED);
        Appointment expired2 = buildAppointment(AppointmentStatus.CONFIRMED);
        expired2.setId(11L);

        when(appointmentRepository.findByStatusAndEndAtBefore(
                eq(AppointmentStatus.CONFIRMED), any()))
                .thenReturn(List.of(expired1, expired2));

        int count = service.autoComplete();

        assertThat(count).isEqualTo(2);
        assertThat(expired1.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(expired2.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        verify(appointmentRepository).saveAll(anyList());
    }

    @Test
    void autoComplete_withNoExpired_returnsZeroAndDoesNotSave() {
        when(appointmentRepository.findByStatusAndEndAtBefore(any(), any()))
                .thenReturn(List.of());

        int count = service.autoComplete();

        assertThat(count).isEqualTo(0);
        verify(appointmentRepository, never()).saveAll(any());
    }
}
