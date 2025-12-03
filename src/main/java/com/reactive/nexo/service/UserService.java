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
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.Set;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import com.reactive.nexo.dto.PagedResponse;

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

    @Autowired
    private com.reactive.nexo.service.ValueAttributeService valueAttributeService;

    public Mono<User> createUser(User user){
        // enforce uniqueness of (identification_type, identification_number)
        return userRepository.findByIdentificationTypeAndNumber(user.getIdentification_type(), user.getIdentification_number())
                .flatMap(existing -> Mono.<User>error(new ResponseStatusException(HttpStatus.CONFLICT, "User with same identification already exists")))
                .switchIfEmpty(userRepository.save(user));
    }

    public Flux<User> getAllUsers(){
        return userRepository.findAll();
    }

    public Mono<PagedResponse<UserWithAttributesDTO>> getAllUsersWithPagination(int page, int size, Set<String> attributes){
        int finalPage = page < 0 ? 0 : page;
        int finalSize = size <= 0 ? 10 : size;
        final int offset = finalPage * finalSize;
        final Set<String> finalAttributes = attributes;
        
        return userRepository.countAll()
                .flatMap(totalElements -> 
                    userRepository.findAllWithPagination(finalSize, offset)
                        .flatMap(user -> 
                            finalAttributes == null || finalAttributes.isEmpty() 
                                ? Mono.just(new UserWithAttributesDTO(user.getId(), user.getNames(), user.getLastnames(), user.getIdentification_type(), user.getIdentification_number(), Collections.emptyList()))
                                : getUserWithFilteredAttributes(user.getId(), finalAttributes)
                        )
                        .collectList()
                        .map(content -> {
                            long totalPages = (totalElements + finalSize - 1) / finalSize;
                            boolean isLast = finalPage >= totalPages - 1;
                            return new PagedResponse<>(content, finalPage, finalSize, totalElements, totalPages, isLast);
                        })
                );
    }

    private Mono<UserWithAttributesDTO> getUserWithFilteredAttributes(Integer userId, Set<String> attributeNames){
        return userRepository.findById(userId)
            .flatMap(user ->
                attributeUserRepository.findByUserId(userId)
                    .filter(attribute -> attributeNames.contains(attribute.getName_attribute()))
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

    public Mono<User> createUserWithAttributes(com.reactive.nexo.dto.CreateUserRequest request){
        User toSave = new User(null, request.getNames(), request.getLastnames(), request.getIdentification_type(), request.getIdentification_number());
        return createUser(toSave).flatMap(savedUser -> {
            Map<String, List<String>> attrs = request.getAttributes();
            if(attrs == null || attrs.isEmpty()){
                return Mono.just(savedUser);
            }
            return Flux.fromIterable(attrs.entrySet())
                    .flatMap(e -> {
                        String attrName = e.getKey();
                        List<String> values = e.getValue() == null ? Collections.emptyList() : e.getValue();
                        AttributeUser attr = new AttributeUser(null, attrName, values.size() > 1, savedUser.getId());
                        return attributeUserRepository.save(attr)
                                .flatMap(savedAttr -> Flux.fromIterable(values)
                                        .flatMap(v -> valueAttributeService.saveValue(new ValueAttributeUser(null, savedAttr.getId(), v)))
                                        .then(Mono.just(savedAttr)));
                    })
                    .collectList()
                    .then(Mono.just(savedUser));
        });
    }

    public Mono<User> updateUserWithAttributes(Integer userId, com.reactive.nexo.dto.CreateUserRequest request){
        return userRepository.findById(userId)
                .flatMap(dbUser ->
                    // check identification uniqueness
                    userRepository.findByIdentificationTypeAndNumber(request.getIdentification_type(), request.getIdentification_number())
                        .flatMap(conflict -> {
                log.info("updateUserWithAttributes - found user by identification: id={} for type={} number={} (updating userId={})",
                    conflict.getId(), request.getIdentification_type(), request.getIdentification_number(), userId);
                if(conflict.getId().equals(userId)){
                                dbUser.setNames(request.getNames());
                                dbUser.setLastnames(request.getLastnames());
                                dbUser.setIdentification_type(request.getIdentification_type());
                                dbUser.setIdentification_number(request.getIdentification_number());
                                return userRepository.save(dbUser);
                            }
                            log.info("updateUserWithAttributes - conflict with other user id={}", conflict.getId());
                            return Mono.<User>error(new ResponseStatusException(HttpStatus.CONFLICT, "Another user with same identification exists"));
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            dbUser.setNames(request.getNames());
                            dbUser.setLastnames(request.getLastnames());
                            dbUser.setIdentification_type(request.getIdentification_type());
                            dbUser.setIdentification_number(request.getIdentification_number());
                            return userRepository.save(dbUser);
                        }))
                ).flatMap(savedUser -> {
                    Map<String, List<String>> attrs = request.getAttributes();
                    final Map<String, List<String>> attrsLocal = (attrs == null) ? Collections.emptyMap() : attrs;

                            // upsert provided attributes using a single safe MERGE (upsert) then load the attribute id
                            Mono<Void> upserts = Flux.fromIterable(attrsLocal.entrySet())
                                    .concatMap(e -> {
                                        String name = e.getKey();
                                        List<String> values = e.getValue() == null ? Collections.emptyList() : e.getValue();
                                        log.info("updateUserWithAttributes - upserting attribute name='{}' values={} for userId={}", name, values, savedUser.getId());

                                        // Use repository MERGE to avoid duplicate insert races. After MERGE, fetch the attribute
                                        // and then replace/insert values as required.
                                        return attributeUserRepository.upsertByUserIdAndName(savedUser.getId(), name, values.size() > 1)
                                                .then(attributeUserRepository.findByUserIdAndName(savedUser.getId(), name))
                                                .flatMap(foundAttr -> {
                                                    log.info("updateUserWithAttributes - attribute id={} ready for values update", foundAttr.getId());
                                                    // delete existing values then insert new ones (replacement semantics for non-multiple)
                                                    return valueAttributeUserRepository.findByAttributeId(foundAttr.getId())
                                                            .flatMap(valueAttributeUserRepository::delete)
                                                            .thenMany(Flux.fromIterable(values))
                                                            .flatMap(v -> valueAttributeService.saveValue(new ValueAttributeUser(null, foundAttr.getId(), v)))
                                                            .then();
                                                });
                                    })
                                    .then();

                    // delete attributes that are not present in request
            Mono<Void> deletions = attributeUserRepository.findByUserId(savedUser.getId())
                .filter(a -> !attrsLocal.containsKey(a.getName_attribute()))
                            .flatMap(a -> valueAttributeUserRepository.findByAttributeId(a.getId()).flatMap(valueAttributeUserRepository::delete).then(attributeUserRepository.delete(a)))
                            .then();

                    // Run upserts first, then deletions sequentially to avoid races where
                    // a deletion may remove a just-created attribute and cause a duplicate
                    // insert attempt. Doing them sequentially ensures stable, idempotent
                    // upsert behavior for each provided attribute.
                    return upserts.then(deletions).then(Mono.just(savedUser));
                });
    }
    
    /**
     * Busca usuarios por nombre de atributo dinámico y valor con diferentes tipos de relación
     */
    public Flux<User> findUsersByAttribute(String attributeName, String attributeValue, String relation) {
        // Validar relación
        if (!relation.equals("eq") && !relation.equals("lt") && !relation.equals("gt")) {
            return Flux.error(new IllegalArgumentException("Relación no válida. Use: eq, lt, gt"));
        }
        
        // Buscar usuarios por atributo dinámico
        switch (relation.toLowerCase()) {
            case "eq":
                return userRepository.findByAttributeEquals(attributeName, attributeValue);
            case "lt":
                return userRepository.findByAttributeLessThan(attributeName, attributeValue);
            case "gt":
                return userRepository.findByAttributeGreaterThan(attributeName, attributeValue);
            default:
                return Flux.error(new IllegalArgumentException("Relación no válida"));
        }
    }
    
    /**
     * Obtiene todos los nombres de atributos disponibles en el sistema
     */
    public Flux<String> getAllAttributeNames() {
        return userRepository.findAllAttributeNames();
    }
    
    /**
     * Obtiene todos los valores disponibles para un atributo específico
     */
    public Flux<String> getAllValuesForAttribute(String attributeName) {
        return userRepository.findAllValuesForAttribute(attributeName);
    }
}
