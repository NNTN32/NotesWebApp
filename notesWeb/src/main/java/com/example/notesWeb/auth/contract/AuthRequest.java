package com.example.notesWeb.auth.contract;

import com.example.notesWeb.auth.domain.Role;
import com.example.notesWeb.auth.domain.Status;
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
