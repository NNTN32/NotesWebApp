package com.example.notesWeb.dtos.TodoListDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListRequest {
    private String heading;
    private String purport;
}
