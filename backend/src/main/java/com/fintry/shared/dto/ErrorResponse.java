package com.fintry.shared.dto;

import java.time.Instant;


public record ErrorResponse(
        Instant timestamp,
        Integer status,
        String error,
        String message
) {
}