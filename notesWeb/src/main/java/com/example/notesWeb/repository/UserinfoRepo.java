package com.example.notesWeb.repository;

import com.example.notesWeb.model.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserinfoRepo extends JpaRepository<UserInfo, UUID> {
}
