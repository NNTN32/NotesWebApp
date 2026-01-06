package com.example.notesWeb.service;

//create custom exception for auth
public class FailedException extends RuntimeException{
    public FailedException(String message) {
        super(message);
    }
}
