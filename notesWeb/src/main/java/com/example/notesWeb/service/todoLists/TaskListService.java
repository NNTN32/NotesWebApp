package com.example.notesWeb.service.todoLists;


import com.example.notesWeb.dtos.TodoListDto.ListRequest;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.model.todoLists.State;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.repository.todoRepo.TodoRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.example.notesWeb.model.todoLists.State.FAIL;

@Service
@RequiredArgsConstructor
public class  TaskListService {
    private final UserRepo userRepo;
    private final TodoRepo todoRepo;

    //Logic get list to do of users
    public List<ListTodo> getAllTodoList(UUID userId){
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID:" + userId));
        try{
            return todoRepo.findByUserId(user.getId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //Logic delete list to do of users
    public void deleteList(Long idList, Long idUser){
        ListTodo listTodo = todoRepo.findById(idList)
                .orElseThrow(() -> new IllegalArgumentException("List doesn't exist! " + idList));

        if(!listTodo.getUser().getId().equals(idUser)){
            throw new AccessDeniedException("You are not authorized to delete this note!");
        }

        todoRepo.delete(listTodo);
    }

    //Logic update to do lists
    public ListTodo updateLists(ListRequest listRequest, Long idList, Long idUser){
        User user = userRepo.findById(idUser)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID:" + idUser));

        ListTodo todo = todoRepo.findById(idList)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID:" + idList));

        if(todo.getState() == State.CORRECT){
            throw new IllegalStateException("This list is marked as CORRECT and cannot be updated!");
        }

        if (!todo.getUser().getId().equals(user.getId())){
            throw new AccessDeniedException("You're not authorized to update this Lists!");
        }

        try{
            if(listRequest.getHeading() != null && !listRequest.getHeading().isBlank()){
                todo.setHeading(listRequest.getHeading());
            }
            if(listRequest.getPurport() != null && !listRequest.getPurport().isBlank()){
                todo.setPurport(listRequest.getPurport());
            }
            todo.setUpdatedAt(LocalDateTime.now());
            return todoRepo.save(todo);
        }catch (Exception e){
            throw new RuntimeException("Failed to update Notes! " + e.getMessage());
        }
    }
}
