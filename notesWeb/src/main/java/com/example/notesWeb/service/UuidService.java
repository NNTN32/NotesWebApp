package com.example.notesWeb.service;

import com.example.notesWeb.repository.IdGenerateRepo;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidService implements IdGenerateRepo {

    @Override
    public UUID nextId() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
