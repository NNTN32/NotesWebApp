package com.example.notesWeb.todos.application;


import com.example.notesWeb.todos.contract.ListRequest;
import com.example.notesWeb.auth.domain.User;
import com.example.notesWeb.todos.domain.ListTodo;
import com.example.notesWeb.todos.domain.State;
import com.example.notesWeb.shared.id.IdGenerateRepo;
import com.example.notesWeb.auth.persistence.UserRepo;
import com.example.notesWeb.todos.persistence.TodoRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TodoListService {
    private final UserRepo userRepo;
    private final TodoRepo todoRepo;
    private final IdGenerateRepo idGenerateRepo;

    //Logic create list to do for users
    public ListTodo createList(ListRequest listRequest, UUID userId){
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID:" + userId));

        ListTodo listTodo = new ListTodo();
        listTodo.setIdList(idGenerateRepo.nextId());
        listTodo.setHeading(listRequest.getHeading());
        listTodo.setPurport(listRequest.getPurport());
        listTodo.setUser(user);

        listTodo.setState(State.FAIL);
        return todoRepo.save(listTodo);
    }

    //Logic update status of to do list
    public ListTodo markDone(UUID todoID, UUID userid){
        ListTodo todo = todoRepo.findById(todoID)
                .orElseThrow(() -> new IllegalArgumentException("Todo not found" + todoID));

        if(!todo.getUser().getId().equals(userid)){
            throw new AccessDeniedException("Not your todo");
        }

        todo.setState(State.CORRECT);
        return todoRepo.save(todo);
    }

}
