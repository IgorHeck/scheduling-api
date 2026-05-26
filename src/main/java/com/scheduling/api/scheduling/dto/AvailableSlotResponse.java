package com.scheduling.api.scheduling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableSlotResponse {

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Long professionalId;

    private String professionalName;

    private boolean available;
}
