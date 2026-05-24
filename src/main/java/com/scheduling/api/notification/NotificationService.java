package com.scheduling.api.notification;

import com.scheduling.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserRepository userRepository;

    /** email → lista de emitters ativos (um por aba aberta) */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    // ── Inscrição ────────────────────────────────────────────────────────────

    /**
     * Registra um novo emitter SSE para o usuário identificado pelo e-mail.
     * Retorna o emitter pronto para ser usado pelo Spring MVC.
     */
    public SseEmitter subscribe(String email) {
        SseEmitter emitter = new SseEmitter(0L); // sem timeout — mantido por heartbeat

        emitters.computeIfAbsent(email, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.debug("SSE subscribe: {} (total emitters: {})", email, countTotal());

        Runnable cleanup = () -> {
            CopyOnWriteArrayList<SseEmitter> list = emitters.get(email);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) emitters.remove(email);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Evento inicial para confirmar a conexão
        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("ok"));
        } catch (IOException e) {
            cleanup.run();
        }

        return emitter;
    }

    // ── Envio ────────────────────────────────────────────────────────────────

    /** Envia um evento para todos os emitters de um e-mail específico. */
    public void notifyUser(String email, NotificationEvent event) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(email);
        if (list == null || list.isEmpty()) return;

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.type())
                        .data(event));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        list.removeAll(dead);
    }

    /**
     * Notifica todos os ADMINs ativos + todos os MANAGERs ativos da empresa.
     * Usado quando um novo agendamento PENDING é criado.
     */
    public void notifyCompanyStaff(Long companyId, NotificationEvent event) {
        userRepository.findActiveStaffForCompany(companyId)
                .forEach(u -> notifyUser(u.getEmail(), event));
    }

    // ── Heartbeat ────────────────────────────────────────────────────────────

    /**
     * Envia um ping a cada 25 s para manter as conexões vivas
     * (proxies e navegadores fecham conexões ociosas).
     */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        if (emitters.isEmpty()) return;
        emitters.forEach((email, list) -> {
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("PING").data("ping"));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            list.removeAll(dead);
        });
    }

    // ── Utils ────────────────────────────────────────────────────────────────

    private int countTotal() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }
}
