package com.scheduling.api.user.dto;

import lombok.Data;

@Data
public class AssignCompanyRequest {
    /** ID da empresa a vincular. Null para desvincular. */
    private Long companyId;
}
