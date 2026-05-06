package com.scheduling.api.appointment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PublicAppointmentRequest {
    @NotNull  private Long companyId;
    @NotNull  private Long professionalId;
    @NotNull  private LocalDateTime startAt;
    @NotNull  private LocalDateTime endAt;
    @NotBlank private String clientName;
    @Email @NotBlank private String clientEmail;
    private String clientPhone;
    private String notes;
}