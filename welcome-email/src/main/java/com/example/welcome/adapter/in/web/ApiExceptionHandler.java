package com.example.welcome.adapter.in.web;

import com.example.welcome.application.EmailAlreadyRegistered;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record Error(String code, String message) {}

    @ExceptionHandler(EmailAlreadyRegistered.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Error duplicate() {
        return new Error("EMAIL_ALREADY_REGISTERED", "Email already registered");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Error invalidInput() {
        return new Error("INVALID_INPUT", "Provide a valid email and a non-empty name (max 100 characters)");
    }
}
