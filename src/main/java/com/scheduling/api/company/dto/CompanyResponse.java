package com.scheduling.api.company.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CompanyResponse {
    private Long id;
    private String name;
    private String description;
    private String address;
    private String phone;
    private boolean allowCLientBooking;
    private boolean active;
    private LocalDateTime createdAt;

}
