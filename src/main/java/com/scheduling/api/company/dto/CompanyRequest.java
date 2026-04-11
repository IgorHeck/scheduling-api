package com.scheduling.api.company.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyRequest {
    @NotBlank
    private String name;

    private String description;

    private String phone;

    private String address;
}
