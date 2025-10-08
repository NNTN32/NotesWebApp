package com.example.notesWeb.dtos.TodoListDto;

import com.example.notesWeb.model.todoLists.State;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListRequest {
    private String heading;
    private String purport;
    private State state;
}
