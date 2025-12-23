package com.example.notesWeb.service.todoLists;


import com.example.notesWeb.dtos.TodoListDto.ListRequest;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.model.todoLists.State;
import com.example.notesWeb.repository.IdGenerateRepo;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.repository.todoRepo.TodoRepo;
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
