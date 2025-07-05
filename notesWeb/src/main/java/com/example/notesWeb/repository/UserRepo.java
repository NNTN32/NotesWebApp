package com.example.notesWeb.repository;

import com.example.notesWeb.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<User, Long> {
}
