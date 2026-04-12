package com.scheduling.api.scheduling.dto;

import com.scheduling.api.scheduling.model.ScheduleBlock;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class ScheduleBlockRequest {

    @NotNull
    private Long companyId;

    @NotNull
    private LocalTime startAt;

    @NotNull
    private LocalTime endAt;

    private String reason;
}
