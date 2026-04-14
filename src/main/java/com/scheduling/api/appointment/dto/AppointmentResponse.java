package com.scheduling.api.appointment.dto;

import com.scheduling.api.appointment.model.AppointmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AppointmentResponse {
    private Long id;
    private Long companyId;
    private String companyName;
    private Long clientId;
    private String clientName;
    private Long professionalId;
    private String professionalName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private AppointmentStatus status;
    private String notes;
    private LocalDateTime createdAt;
}
