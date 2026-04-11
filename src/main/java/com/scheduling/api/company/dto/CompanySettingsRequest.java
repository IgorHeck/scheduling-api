package com.scheduling.api.company.dto;

import lombok.Data;

@Data
public class CompanySettingsRequest {
    private Boolean allowClientBooking;
    private Boolean active;

}
