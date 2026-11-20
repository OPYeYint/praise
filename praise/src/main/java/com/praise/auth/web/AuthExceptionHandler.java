package com.praise.auth.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail rejected(InvalidCredentialsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(UsernameAlreadyTakenException.class)
    ProblemDetail usernameTaken(UsernameAlreadyTakenException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }
}
