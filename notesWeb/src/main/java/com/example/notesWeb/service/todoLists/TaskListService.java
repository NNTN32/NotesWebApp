package com.example.notesWeb.service.todoLists;


import com.example.notesWeb.model.User;
import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.repository.todoRepo.TodoRepo;
import lombok.RequiredArgsConstructor;
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
}
