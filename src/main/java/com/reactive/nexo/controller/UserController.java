package com.reactive.nexo.controller;

import com.reactive.nexo.model.User;
import com.reactive.nexo.service.UserService;
import com.reactive.nexo.dto.UserWithAttributesDTO;
import com.reactive.nexo.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/users")
@Slf4j
@Tag(name = "Users", description = "API para gestión de usuarios")
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
    public Mono<ResponseEntity<PagedResponse<UserWithAttributesDTO>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String attributes){
        
        Set<String> attributeSet = null;
        if(attributes != null && !attributes.trim().isEmpty()){
            // Parse attributes: "age,gender" -> {age, gender}
            attributeSet = Arrays.stream(attributes.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
        
        return userService.getAllUsersWithPagination(page, size, attributeSet)
                .map(ResponseEntity::ok);
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

    @Operation(
        summary = "Actualización parcial de usuario",
        description = "Permite actualizar solo los campos especificados del usuario. Los campos no incluidos en el request no se modificarán."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Usuario actualizado exitosamente"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflicto - identificación ya existe para otro usuario"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @PatchMapping("/{userId}")
    public Mono<ResponseEntity<User>> patchUserById(
            @Parameter(description = "ID del usuario a actualizar", required = true)
            @PathVariable Integer userId, 
            @Parameter(description = "Campos del usuario a actualizar (solo los campos no nulos se actualizarán)", required = true)
            @RequestBody com.reactive.nexo.dto.CreateUserRequest request) {
        
        log.info("Iniciando actualización parcial del usuario con ID: {}", userId);
        
        return userService.partialUpdateUser(userId, request)
                .map(updatedUser -> {
                    log.info("Usuario actualizado exitosamente: {}", updatedUser.getId());
                    return ResponseEntity.ok(updatedUser);
                })
                .onErrorResume(err -> {
                    if (err instanceof ResponseStatusException) {
                        ResponseStatusException rsException = (ResponseStatusException) err;
                        log.warn("Error en actualización parcial del usuario {}: {}", userId, rsException.getReason());
                        return Mono.just(ResponseEntity.status(rsException.getStatusCode()).build());
                    }
                    log.error("Error inesperado en actualización parcial del usuario {}", userId, err);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
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
    
    @Operation(
        summary = "Buscar usuarios por atributo dinámico y valor",
        description = "Permite buscar usuarios filtrando por atributos dinámicos creados en attribute_user/value_attribute_user " +
                     "con diferentes tipos de relación (eq=igual, lt=menor que, gt=mayor que). " +
                     "Use /api/v1/users/attributes para ver qué atributos están disponibles."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Búsqueda exitosa"),
        @ApiResponse(responseCode = "400", description = "Relación incorrecta o error en parámetros"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/by/{attribute_name}/{attribute_value}")
    public Mono<ResponseEntity<Flux<User>>> findUsersByAttribute(
            @Parameter(description = "Nombre del atributo dinámico (ej: edad, género, departamento)", required = true)
            @PathVariable String attribute_name,
            
            @Parameter(description = "Valor a buscar", required = true)
            @PathVariable String attribute_value,
            
            @Parameter(description = "Tipo de relación (eq, lt, gt)", example = "eq")
            @RequestParam(defaultValue = "eq") String relation) {

        log.info("Buscando usuarios por atributo dinámico: {}, valor: {}, relación: {}", attribute_name, attribute_value, relation);

        try {
            Flux<User> result = userService.findUsersByAttribute(attribute_name, attribute_value, relation);
            return Mono.just(ResponseEntity.ok(result));
        } catch (IllegalArgumentException e) {
            log.error("Error en parámetros de búsqueda: {}", e.getMessage());
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
        } catch (Exception e) {
            log.error("Error interno al buscar usuarios por atributo dinámico", e);
            return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor"));
        }
    }
    
    @Operation(
        summary = "Buscar usuarios por atributo dinámico con relación explícita",
        description = "Versión extendida del endpoint de búsqueda que incluye el parámetro de relación en la URL"
    )
    @GetMapping("/by/{attribute_name}/{attribute_value}/{relation}")
    public Mono<ResponseEntity<Flux<User>>> findUsersByAttributeWithRelation(
            @Parameter(description = "Nombre del atributo dinámico", required = true)
            @PathVariable String attribute_name,
            
            @Parameter(description = "Valor a buscar", required = true)
            @PathVariable String attribute_value,
            
            @Parameter(description = "Tipo de relación (eq, lt, gt)", required = true)
            @PathVariable String relation) {

        log.info("Buscando usuarios por atributo dinámico: {}, valor: {}, relación: {}", attribute_name, attribute_value, relation);

        try {
            Flux<User> result = userService.findUsersByAttribute(attribute_name, attribute_value, relation);
            return Mono.just(ResponseEntity.ok(result));
        } catch (IllegalArgumentException e) {
            log.error("Error en parámetros de búsqueda: {}", e.getMessage());
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
        } catch (Exception e) {
            log.error("Error interno al buscar usuarios por atributo dinámico", e);
            return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor"));
        }
    }
    
    @Operation(
        summary = "Obtener todos los nombres de atributos disponibles",
        description = "Devuelve la lista de todos los nombres de atributos dinámicos disponibles en el sistema"
    )
    @GetMapping("/attributes")
    public Mono<ResponseEntity<Flux<String>>> getAllAttributeNames() {
        log.info("Obteniendo todos los nombres de atributos dinámicos");
        
        Flux<String> attributes = userService.getAllAttributeNames();
        return Mono.just(ResponseEntity.ok(attributes));
    }
    
    @Operation(
        summary = "Obtener valores disponibles para un atributo específico",
        description = "Devuelve todos los valores únicos disponibles para un atributo dinámico específico"
    )
    @GetMapping("/attributes/{attribute_name}/values")
    public Mono<ResponseEntity<Flux<String>>> getAllValuesForAttribute(
            @Parameter(description = "Nombre del atributo", required = true)
            @PathVariable String attribute_name) {
        
        log.info("Obteniendo valores disponibles para el atributo: {}", attribute_name);
        
        Flux<String> values = userService.getAllValuesForAttribute(attribute_name);
        return Mono.just(ResponseEntity.ok(values));
    }
}
