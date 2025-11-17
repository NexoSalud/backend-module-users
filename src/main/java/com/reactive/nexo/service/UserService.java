package com.reactive.nexo.service;

//import com.reactive.nexo.dto.UserDepartmentDTO;
import com.reactive.nexo.model.AttributeUser;
import com.reactive.nexo.model.User;
import com.reactive.nexo.model.ValueAttributeUser;
import com.reactive.nexo.repository.AttributeUserRepository;
import com.reactive.nexo.repository.UserRepository;
import com.reactive.nexo.repository.ValueAttributeUserRepository;
import com.reactive.nexo.dto.AttributeWithValuesDTO;
import com.reactive.nexo.dto.UserWithAttributesDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.BiFunction;

@Service
@Slf4j
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttributeUserRepository attributeUserRepository;

    @Autowired
    private ValueAttributeUserRepository valueAttributeUserRepository;

    public Mono<User> createUser(User user){
        // enforce uniqueness of (identification_type, identification_number)
        return userRepository.findByIdentificationTypeAndNumber(user.getIdentification_type(), user.getIdentification_number())
                .flatMap(existing -> Mono.<User>error(new ResponseStatusException(HttpStatus.CONFLICT, "User with same identification already exists")))
                .switchIfEmpty(userRepository.save(user));
    }

    public Flux<User> getAllUsers(){
        return userRepository.findAll();
    }

    public Mono<User> findById(Integer userId){
        return userRepository.findById(userId);
    }

    public Mono<UserWithAttributesDTO> getUserWithAttributes(Integer userId){
    return userRepository.findById(userId)
        .flatMap(user ->
            attributeUserRepository.findByUserId(userId)
                .flatMap(attribute ->
                    valueAttributeUserRepository.findByAttributeId(attribute.getId())
                        .map(ValueAttributeUser::getValueAttribute)
                        .collectList()
                        .map(values -> new AttributeWithValuesDTO(attribute.getName_attribute(), values))
                )
                .collectList()
                .map(attrs -> new UserWithAttributesDTO(user.getId(), user.getNames(), user.getLastnames(), user.getIdentification_type(), user.getIdentification_number(), attrs))
        );
    }

    public Mono<User> updateUser(Integer userId,  User user){
        return userRepository.findById(userId)
                .flatMap(dbUser ->
                    // check if another user already has the requested identification pair
                    userRepository.findByIdentificationTypeAndNumber(user.getIdentification_type(), user.getIdentification_number())
                        .flatMap(conflict -> {
                            if(conflict.getId().equals(userId)){
                                // same record — allow
                                return userRepository.save(user);
                            }
                            return Mono.<User>error(new ResponseStatusException(HttpStatus.CONFLICT, "Another user with same identification exists"));
                        })
                        .switchIfEmpty(userRepository.save(user))
                );
    }

    public Mono<User> deleteUser(Integer userId){
        return userRepository.findById(userId)
                .flatMap(existingUser -> userRepository.delete(existingUser)
                .then(Mono.just(existingUser)));
    }

    public Flux<User> findUsersByIdentificationNumber(String identificationNumber){
        return userRepository.findByIdentificationNumber(identificationNumber);
    }

    public Mono<UserWithAttributesDTO> getUserWithAttributesByIdentification(String identificationType, String identificationNumber){
    return userRepository.findByIdentificationTypeAndNumber(identificationType, identificationNumber)
        .flatMap(user ->
            attributeUserRepository.findByUserId(user.getId())
                .flatMap(attribute ->
                    valueAttributeUserRepository.findByAttributeId(attribute.getId())
                        .map(ValueAttributeUser::getValueAttribute)
                        .collectList()
                        .map(values -> new AttributeWithValuesDTO(attribute.getName_attribute(), values))
                )
                .collectList()
                .map(attrs -> new UserWithAttributesDTO(user.getId(), user.getNames(), user.getLastnames(), user.getIdentification_type(), user.getIdentification_number(), attrs))
        );
    }

    public Flux<User> fetchUsers(List<Integer> userIds) {
        return Flux.fromIterable(userIds)
                .parallel()
                .runOn(Schedulers.boundedElastic())
                .flatMap(i -> findById(i))
                .ordered((u1, u2) -> u2.getId() - u1.getId());
    }
}
