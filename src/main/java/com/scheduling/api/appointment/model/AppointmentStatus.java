package com.scheduling.api.appointment.model;

public enum AppointmentStatus {
    PENDING,    // cliente solicitou, aguarda confirmação do dono
    CONFIRMED,  // dono confirmou
    CANCELLED,  // cancelado por qualquer parte
    COMPLETED,  // aconteceu, finalizado
    NO_SHOW     // cliente não apareceu
}
