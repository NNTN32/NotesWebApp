package com.example.notesWeb.service.todoLists;


import com.example.notesWeb.model.User;
import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.repository.todoRepo.TodoRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskListService {
    private final UserRepo userRepo;
    private final TodoRepo todoRepo;

    //Logic get list to do of users
    public List<ListTodo> getAllTodoList(Long userId){
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
}
