package com.example.notesWeb.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "userInfo")
public class UserInfo {
    @Id
    @Column(columnDefinition = "uuid DEFAULT get_uuid_v7()")
    @GeneratedValue
    private UUID idInfo;

    private String avatar;
    private String phoneNumb;
    private String address;

}
