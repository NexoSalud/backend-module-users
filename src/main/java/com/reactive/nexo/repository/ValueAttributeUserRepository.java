package com.reactive.nexo.repository;

import com.reactive.nexo.model.ValueAttributeUser;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ValueAttributeUserRepository extends ReactiveCrudRepository<ValueAttributeUser,Integer> {
    Flux<ValueAttributeUser> findByAttributeId(Integer attributeId);
}
