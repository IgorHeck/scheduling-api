package com.scheduling.api.user.dto;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String name;
    private String phone;
}
