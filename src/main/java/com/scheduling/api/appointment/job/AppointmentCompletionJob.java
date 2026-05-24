package com.scheduling.api.appointment.job;

import com.scheduling.api.appointment.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job que varre agendamentos CONFIRMED com endAt no passado
 * e os marca automaticamente como COMPLETED.
 * Executa a cada 5 minutos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentCompletionJob {

    private final AppointmentService appointmentService;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void run() {
        int completed = appointmentService.autoComplete();
        if (completed > 0) {
            log.info("[AppointmentCompletionJob] {} agendamento(s) concluído(s) automaticamente.", completed);
        }
    }
}
