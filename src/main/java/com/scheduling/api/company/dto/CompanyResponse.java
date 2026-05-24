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
    private String logoUrl;
    private boolean allowClientBooking;
    private boolean active;
    private LocalDateTime createdAt;

}
