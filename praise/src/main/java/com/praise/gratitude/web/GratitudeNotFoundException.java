package com.praise.gratitude.web;

public class GratitudeNotFoundException extends RuntimeException {

    public GratitudeNotFoundException(Long id) {
        super("Gratitude entry " + id + " was not found");
    }
}
