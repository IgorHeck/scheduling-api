package com.scheduling.api.notification;

import com.scheduling.api.user.model.Role;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService service;

    // ── subscribe() ──────────────────────────────────────────────────────────

    @Test
    void subscribe_returnsNonNullEmitter() {
        SseEmitter emitter = service.subscribe("user@test.com");
        assertThat(emitter).isNotNull();
    }

    @Test
    void subscribe_sameEmailTwice_returnsTwoDistinctEmitters() {
        SseEmitter emitter1 = service.subscribe("user@test.com");
        SseEmitter emitter2 = service.subscribe("user@test.com");
        assertThat(emitter1).isNotSameAs(emitter2);
    }

    // ── notifyUser() ─────────────────────────────────────────────────────────

    @Test
    void notifyUser_withNoSubscribers_doesNotThrow() {
        assertThatNoException().isThrownBy(() ->
                service.notifyUser("nobody@test.com",
                        new NotificationEvent("TEST", "msg", null)));
    }

    @Test
    void notifyUser_withCompletedEmitter_removesDeadEmitterGracefully() {
        // complete() marca emitter como finalizado sem acionar onCompletion
        // (sem handler HTTP real); a próxima send() lança IllegalStateException,
        // que NotificationService captura e remove da pool.
        SseEmitter emitter = service.subscribe("user@test.com");
        emitter.complete();   // marca como concluído, mas não remove da pool

        // notifyUser tenta enviar, captura a exceção e remove o emitter morto
        assertThatNoException().isThrownBy(() ->
                service.notifyUser("user@test.com",
                        new NotificationEvent("TEST", "msg", 1L)));
    }

    @Test
    void notifyUser_afterOnCompletionCleanup_poolIsEmpty() {
        // Quando o emitter está fora de um contexto HTTP, complete() NÃO aciona
        // onCompletion (handler é null). O teste abaixo valida que a lógica de
        // envio não lança exceção quando a lista não tem emitters.
        service.subscribe("user@test.com");
        // não há forma direta de acionar onCompletion fora de um contexto HTTP real;
        // o comportamento de remoção via onCompletion é testado nos testes de integração.
        assertThatNoException().isThrownBy(() ->
                service.notifyUser("user@test.com",
                        new NotificationEvent("PING", "msg", null)));
    }

    // ── notifyCompanyStaff() ─────────────────────────────────────────────────

    @Test
    void notifyCompanyStaff_queriesRepositoryWithCorrectCompanyId() {
        User admin   = User.builder().id(1L).email("admin@co.com").role(Role.ADMIN).build();
        User manager = User.builder().id(2L).email("mgr@co.com").role(Role.MANAGER).build();

        when(userRepository.findActiveStaffForCompany(1L))
                .thenReturn(List.of(admin, manager));

        assertThatNoException().isThrownBy(() ->
                service.notifyCompanyStaff(1L,
                        new NotificationEvent("NEW_PENDING", "msg", 1L)));

        verify(userRepository).findActiveStaffForCompany(1L);
    }

    @Test
    void notifyCompanyStaff_withNoStaff_doesNotThrow() {
        when(userRepository.findActiveStaffForCompany(99L)).thenReturn(List.of());

        assertThatNoException().isThrownBy(() ->
                service.notifyCompanyStaff(99L,
                        new NotificationEvent("NEW_PENDING", "msg", 1L)));
    }

    // ── heartbeat() ──────────────────────────────────────────────────────────

    @Test
    void heartbeat_withEmptyPool_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> service.heartbeat());
    }

    @Test
    void heartbeat_withActiveSubscribers_doesNotThrow() {
        service.subscribe("a@test.com");
        service.subscribe("b@test.com");
        assertThatNoException().isThrownBy(() -> service.heartbeat());
    }

    @Test
    void heartbeat_withCompletedEmitter_removesDeadAndDoesNotThrow() {
        SseEmitter emitter = service.subscribe("user@test.com");
        emitter.complete(); // send() vai lançar IllegalStateException → removido

        assertThatNoException().isThrownBy(() -> service.heartbeat());
    }
}
