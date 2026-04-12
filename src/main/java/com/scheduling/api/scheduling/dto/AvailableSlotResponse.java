package com.scheduling.api.scheduling.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AvailableSlotResponse {

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Long professionalId;

    private String professionalName;

    private boolean available;
}
