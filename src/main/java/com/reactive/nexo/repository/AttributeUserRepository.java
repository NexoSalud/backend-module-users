package com.reactive.nexo.repository;

import com.reactive.nexo.model.AttributeUser;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Mono;

public interface AttributeUserRepository extends ReactiveCrudRepository<AttributeUser,Integer> {
    // A user can have multiple attributes, so return a Flux
    Flux<AttributeUser> findByUserId(Integer userId);

    @Query("select id,user_id,name_attribute,multiple from attribute_user where user_id = $1 and name_attribute = $2 limit 1")
    Mono<AttributeUser> findByUserIdAndName(Integer userId, String nameAttribute);

    @Query("MERGE INTO attribute_user (user_id, name_attribute, multiple) KEY (user_id, name_attribute) VALUES ($1, $2, $3)")
    Mono<Integer> upsertByUserIdAndName(Integer userId, String nameAttribute, Boolean multiple);
}
