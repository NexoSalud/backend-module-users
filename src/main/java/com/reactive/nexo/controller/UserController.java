package com.reactive.nexo.controller;

import com.reactive.nexo.model.User;
import com.reactive.nexo.service.UserService;
import com.reactive.nexo.dto.UserWithAttributesDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
@Autowired
private UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> create(@RequestBody com.reactive.nexo.dto.CreateUserRequest request){
        // create user and attributes if provided
        return userService.createUserWithAttributes(request);
    }

    @GetMapping
    public Flux<User> getAllUsers(){
        return userService.getAllUsers();
    }

    @GetMapping("/{userId}")
    public Mono<ResponseEntity<UserWithAttributesDTO>> getUserById(@PathVariable Integer userId){
        Mono<UserWithAttributesDTO> user = userService.getUserWithAttributes(userId);
        return user.map( u -> ResponseEntity.ok(u))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-identification/{identificationType}/{identificationNumber}")
    public Mono<ResponseEntity<UserWithAttributesDTO>> getUserByIdentificationNumber(@PathVariable String identificationType, @PathVariable String identificationNumber){
        Mono<UserWithAttributesDTO> user = userService.getUserWithAttributesByIdentification(identificationType.toUpperCase(), identificationNumber);
        return user.map( u -> ResponseEntity.ok(u))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{userId}")
    public Mono<ResponseEntity<User>> updateUserById(@PathVariable Integer userId, @RequestBody com.reactive.nexo.dto.CreateUserRequest request){
        return userService.updateUserWithAttributes(userId, request)
                .map(updatedUser -> ResponseEntity.ok(updatedUser))
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @DeleteMapping("/{userId}")
    public Mono<ResponseEntity<Void>> deleteUserById(@PathVariable Integer userId){
        return userService.deleteUser(userId)
                .map( r -> ResponseEntity.ok().<Void>build())
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/search/id")
    public Flux<User> fetchUsersByIds(@RequestBody List<Integer> ids) {
        return userService.fetchUsers(ids);
    }
}
