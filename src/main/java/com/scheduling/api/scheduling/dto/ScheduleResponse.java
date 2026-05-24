package com.scheduling.api.scheduling.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleResponse {
    private Long id;
    private String dayOfWeek;
    private String startTime;
    private String endTime;
    private String lunchStart;
    private String lunchEnd;
    private int slotDurationMinutes;
    private boolean active;
    private ProfessionalDto professional;

    @Data
    @Builder
    public static class ProfessionalDto {
        private Long id;
        private String name;
    }
}
