package com.scheduling.api.scheduling.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ScheduleBlockResponse {
    private Long id;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String reason;
}
