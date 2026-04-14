package com.scheduling.api.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateAppointmentRequest {
    @NotNull
    private Long companyId;

    @NotNull
    private Long professionalId;

    @NotNull
    private LocalDateTime startAt;

    @NotNull
    private LocalDateTime endAt;

    private String notes;
}
