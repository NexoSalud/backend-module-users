package com.reactive.nexo.repository;

import com.reactive.nexo.model.AttributeUser;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface AttributeUserRepository extends ReactiveCrudRepository<AttributeUser,Integer> {
    // A user can have multiple attributes, so return a Flux
    Flux<AttributeUser> findByUserId(Integer userId);
}
