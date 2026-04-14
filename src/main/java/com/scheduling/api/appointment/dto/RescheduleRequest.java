package com.scheduling.api.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RescheduleRequest {

    @NotNull
    private LocalDateTime newStartAt;

    @NotNull
    private LocalDateTime newEndAt;
}
