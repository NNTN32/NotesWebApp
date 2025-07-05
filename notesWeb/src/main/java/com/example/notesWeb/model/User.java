package com.example.notesWeb.model;


import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String email;
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private Status status;

    // Tự động lưu UserInfo khi lưu User
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "userInfo_id", referencedColumnName = "id")
    private UserInfo userInfo;
}
