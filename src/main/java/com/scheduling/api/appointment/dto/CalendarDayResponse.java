package com.scheduling.api.appointment.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class CalendarDayResponse {
    private LocalDate date;

    private String status;

    private long totalAppointments;
}
