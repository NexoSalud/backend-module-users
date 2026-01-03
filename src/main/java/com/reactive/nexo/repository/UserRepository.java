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
    @Query("select id,identification_number,identification_type,names,lastnames from users order by id asc limit $1 offset $2")
    Flux<User> findAllWithPagination(int limit, int offset);
    @Query("select count(*) from users")
    Mono<Long> countAll();
    
    // Búsqueda por atributos dinámicos con relación EQUAL
    @Query("select DISTINCT u.id, u.identification_number, u.identification_type, u.names, u.lastnames " +
           "from users u " +
           "INNER JOIN attribute_user au ON u.id = au.user_id " +
           "INNER JOIN value_attribute_user vau ON au.id = vau.attribute_id " +
           "WHERE au.name_attribute = $1 AND vau.value_attribute = $2")
    Flux<User> findByAttributeEquals(String attributeName, String attributeValue);
    
    // Búsqueda por atributos dinámicos con relación LESS THAN
    @Query("select DISTINCT u.id, u.identification_number, u.identification_type, u.names, u.lastnames " +
           "from users u " +
           "INNER JOIN attribute_user au ON u.id = au.user_id " +
           "INNER JOIN value_attribute_user vau ON au.id = vau.attribute_id " +
           "WHERE au.name_attribute = $1 AND vau.value_attribute < $2")
    Flux<User> findByAttributeLessThan(String attributeName, String attributeValue);
    
    // Búsqueda por atributos dinámicos con relación GREATER THAN
    @Query("select DISTINCT u.id, u.identification_number, u.identification_type, u.names, u.lastnames " +
           "from users u " +
           "INNER JOIN attribute_user au ON u.id = au.user_id " +
           "INNER JOIN value_attribute_user vau ON au.id = vau.attribute_id " +
           "WHERE au.name_attribute = $1 AND vau.value_attribute > $2")
    Flux<User> findByAttributeGreaterThan(String attributeName, String attributeValue);
    
    // Consulta para obtener todos los nombres de atributos disponibles
    @Query("select DISTINCT au.name_attribute from attribute_user au ORDER BY au.name_attribute")
    Flux<String> findAllAttributeNames();
    
    // Consulta para obtener todos los valores de un atributo específico
    @Query("select DISTINCT vau.value_attribute " +
           "from attribute_user au " +
           "INNER JOIN value_attribute_user vau ON au.id = vau.attribute_id " +
           "WHERE au.name_attribute = $1 ORDER BY vau.value_attribute")
    Flux<String> findAllValuesForAttribute(String attributeName);
}
