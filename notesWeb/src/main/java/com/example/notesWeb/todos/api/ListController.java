package com.example.notesWeb.todos.api;


import com.example.notesWeb.auth.security.jwtProvider;
import com.example.notesWeb.auth.contract.AuthResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.example.notesWeb.todos.contract.ListRequest;
import com.example.notesWeb.auth.domain.User;
import com.example.notesWeb.todos.domain.ListTodo;
import com.example.notesWeb.auth.persistence.UserRepo;
import com.example.notesWeb.todos.application.TaskListService;
import com.example.notesWeb.todos.application.TodoListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/todo")
public class ListController {
    private final TodoListService listService;

    private final UserRepo userRepo;

    private final jwtProvider jwtProvider;

    private final TaskListService taskListService;

    //API Call back logic create To do lists
    @Operation(summary = "User create list todo")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/createList")
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String authoHeader,
            @RequestBody ListRequest listRequest){
        try{
            if(authoHeader == null || !authoHeader.startsWith("Bearer ")){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authorization header is missing or invalid.");
            }

            String token = authoHeader.substring(7);

            //Checking situation if token has expired
            if(jwtProvider.isTokenExpired(token)){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token expired. Please login again.");
            }

            String userName = jwtProvider.getUserFromJwt(token);
            User user = userRepo.findByUsername(userName)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found!"));

            UUID userId = user.getId();

            ListTodo created = listService.createList(listRequest, userId);
            return ResponseEntity.ok(created);
        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().body(e.getMessage() + "Can not create to do lists!");
        }
    }

    //API Call back logic update state to do lists
    @Operation(summary = "User updated state todo list")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/update/{todoID}")
    public ResponseEntity<?> updateLists(
            @PathVariable UUID todoID,
            @RequestHeader("Authorization") String authorHeader
    ){
        try{
            if(authorHeader == null || !authorHeader.startsWith("Bearer ")){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authorization header is missing or invalid.");
            }

            String token = authorHeader.substring(7);

            //Checking situation if token has expired
            if(jwtProvider.isTokenExpired(token)){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token expired. Please login again.");
            }

            String userName = jwtProvider.getUserFromJwt(token);
            User user = userRepo.findByUsername(userName)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found!"));

            UUID userid = user.getId();
            ListTodo updatedLists = listService.markDone(todoID, userid);
            return ResponseEntity.ok(updatedLists);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //API Get List to do of user
    @Operation(summary = "User get their own todo lists")
    @GetMapping("/listUser/{userId}")
    public ResponseEntity<?> getList(@PathVariable UUID userId, @AuthenticationPrincipal AuthResponse currentUser){
        if (!userId.equals(currentUser.getId())) return ResponseEntity.status(403).body("Todo access denied");
        try{
            List<ListTodo> todoList = taskListService.getAllTodoList(userId);
            return ResponseEntity.ok(todoList);
        }catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage() + "Can not find Lists!");
        }
    }

    //API Delete list to do of user
    @Operation(summary = "User delete their own todo lists")
    @DeleteMapping("/delete/{idList}")
    public ResponseEntity<?> deleteList(@PathVariable UUID idList,
            @RequestParam(required = false) UUID idUser, @AuthenticationPrincipal AuthResponse currentUser){
        if (idUser != null && !idUser.equals(currentUser.getId())) return ResponseEntity.status(403).body("Todo access denied");
        try{
            taskListService.deleteList(idList, currentUser.getId());
            return ResponseEntity.ok("List has been deleted successfully !");
        }catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage() + "Can not find Lists to delete!");
        }catch (AccessDeniedException e){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error: " + e.getMessage());
        }
    }

    //API Update to do lists of user
    @Operation(summary = "User updated todo list")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/listUpdate/{idList}")
    public ResponseEntity<?> updated(
            @PathVariable UUID idList,
            @RequestHeader("Authorization") String authorHeader,
            @RequestBody ListRequest listRequest
    ){
        try{
            if(authorHeader == null || !authorHeader.startsWith("Bearer ")){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authorization header is missing or invalid.");
            }

            String token = authorHeader.substring(7);

            if(jwtProvider.isTokenExpired(token)){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token expired. Please login again.");
            }

            String username = jwtProvider.getUserFromJwt(token);
            User user = userRepo.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found!"));
            UUID idUser = user.getId();

            ListTodo listUpdated = taskListService.updateLists(listRequest, idList, idUser);
            return ResponseEntity.ok(listUpdated);

        }catch (AccessDeniedException e){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage() + "User doesn't have authority to update List!");
        }catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage() + "List doesn't exist!");
        }catch (Exception e){
            return ResponseEntity.internalServerError().body(e.getMessage() + "Failed to update List!");
        }
    }


}
