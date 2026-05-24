package com.scheduling.api.notification;

/**
 * Payload enviado via SSE para o cliente.
 *
 * @param type          Tipo do evento (NEW_PENDING, APPOINTMENT_CONFIRMED, etc.)
 * @param message       Mensagem legível pelo usuário
 * @param appointmentId ID do agendamento relacionado (pode ser null)
 */
public record NotificationEvent(
        String type,
        String message,
        Long   appointmentId
) {}
