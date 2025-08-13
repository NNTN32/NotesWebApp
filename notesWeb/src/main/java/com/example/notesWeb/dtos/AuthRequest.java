package com.example.notesWeb.dtos;

import com.example.notesWeb.model.Role;
import com.example.notesWeb.model.Status;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AuthRequest {
    private String email;
    private String username;
    private String password;
    private Role role;
    private Status status;
    private String sessionID;

    //Constructor call object
    public AuthRequest(String username, String password, String sessionID){
        this.username = username;
        this.password = password;
        this.sessionID = sessionID;
    }
}
