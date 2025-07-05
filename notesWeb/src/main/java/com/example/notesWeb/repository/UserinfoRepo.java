package com.example.notesWeb.repository;

import com.example.notesWeb.model.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserinfoRepo extends JpaRepository<UserInfo, Long> {
}
