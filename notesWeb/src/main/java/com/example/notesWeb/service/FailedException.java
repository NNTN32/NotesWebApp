package com.example.notesWeb.service;

//create custom exception for business ack by catching invalid data
public class FailedException extends RuntimeException{
    public FailedException(String message) {
        super(message);
    }
}
