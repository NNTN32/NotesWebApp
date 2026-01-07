package com.example.notesWeb.service;

//create custom exception for system retry by catching infrastructure, network, database, Redis, cloud
public class SystemException extends RuntimeException {
    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
