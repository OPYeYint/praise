package com.praise.auth.web;

public class UsernameAlreadyTakenException extends RuntimeException {

    public UsernameAlreadyTakenException(String message) {
        super(message);
    }
}
