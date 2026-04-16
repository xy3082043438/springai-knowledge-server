package com.lamb.springaiknowledgeserver.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Custom Business Exception for handling expected service-level errors.
 */
@Getter
public class ServiceException extends RuntimeException {

    private final HttpStatus status;

    public ServiceException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public ServiceException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
