package com.scheduling.api.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
public class ScheduleRequest {
    @NotNull private Long companyId;
    @NotNull private Long professionalId;
    @NotNull private DayOfWeek dayOfWeek;
    @NotNull private LocalTime startTime;
    @NotNull private LocalTime endTime;
    @Min(15)  private int slotDurationMinutes = 60;
}