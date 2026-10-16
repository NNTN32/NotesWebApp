package com.example.notesWeb.todos.application;


import com.example.notesWeb.todos.contract.ListRequest;
import com.example.notesWeb.auth.domain.User;
import com.example.notesWeb.todos.domain.ListTodo;
import com.example.notesWeb.todos.domain.State;
import com.example.notesWeb.auth.persistence.UserRepo;
import com.example.notesWeb.todos.persistence.TodoRepo;
import com.example.notesWeb.shared.error.FailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class  TaskListService {
    private final UserRepo userRepo;
    private final TodoRepo todoRepo;

    //Logic get list to do of users
    public List<ListTodo> getAllTodoList(UUID userId){
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new FailedException("User not found with ID:" + userId));
        return todoRepo.findByUserId(user.getId());
    }

    //Logic delete list to do of users
    public void deleteList(UUID idList, UUID idUser){
        ListTodo listTodo = todoRepo.findById(idList)
                .orElseThrow(() -> new FailedException("List doesn't exist! " + idList));

        if(!listTodo.getUser().getId().equals(idUser)){
            throw new AccessDeniedException("You are not authorized to delete this note!");
        }

        todoRepo.delete(listTodo);
    }

    //Logic update to do lists
    public ListTodo updateLists(ListRequest listRequest, UUID idList, UUID idUser){
        User user = userRepo.findById(idUser)
                .orElseThrow(() -> new FailedException("User not found with ID:" + idUser));

        ListTodo todo = todoRepo.findById(idList)
                .orElseThrow(() -> new FailedException("User not found with ID:" + idList));

        if(todo.getState() == State.CORRECT){
            throw new FailedException("This list is marked as CORRECT and cannot be updated!");
        }

        if (!todo.getUser().getId().equals(user.getId())){
            throw new FailedException("You're not authorized to update this Lists!");
        }
        if(listRequest.getHeading() != null && !listRequest.getHeading().isBlank()){
            todo.setHeading(listRequest.getHeading());
        }
        if(listRequest.getPurport() != null && !listRequest.getPurport().isBlank()){
            todo.setPurport(listRequest.getPurport());
        }
        todo.setUpdatedAt(Instant.now());
        return todoRepo.save(todo);
    }
}
