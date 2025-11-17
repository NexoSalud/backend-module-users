package com.reactive.nexo.repository;

import com.reactive.nexo.model.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<User,Integer> {
    @Query("select id,identification_number,identification_type,names,lastnames from users where identification_number like $1")
    Flux<User> findByIdentificationNumber(String identificationNumber);
    @Query("select id,identification_number,identification_type,names,lastnames from users where identification_type = $1 and identification_number = $2 limit 1")
    Mono<User> findByIdentificationTypeAndNumber(String identificationType, String identificationNumber);
}
