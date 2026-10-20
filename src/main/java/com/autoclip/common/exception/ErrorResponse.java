package com.autoclip.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String message,
        List<ValidationDetail> details
) {

    public record ValidationDetail(String field, String message) {
    }

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(Instant.now(), status, message, List.of());
    }

    public static ErrorResponse of(int status, String message, List<ValidationDetail> details) {
        return new ErrorResponse(Instant.now(), status, message, details);
    }
}
