package com.reactive.nexo.service;

import com.reactive.nexo.model.ValueAttributeUser;
import com.reactive.nexo.repository.AttributeUserRepository;
import com.reactive.nexo.repository.ValueAttributeUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class ValueAttributeService {

    private final ValueAttributeUserRepository valueAttributeUserRepository;
    private final AttributeUserRepository attributeUserRepository;

    /**
     * Save a value for attribute. If the attribute.multiple == false and there is already a value
     * present for the attribute, reject with 409 Conflict.
     */
    public Mono<ValueAttributeUser> saveValue(ValueAttributeUser value){
        Integer attributeId = value.getAttributeId();
        return attributeUserRepository.findById(attributeId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Attribute not found")))
                .flatMap(attr -> {
                    Boolean multiple = attr.getMultiple() == null ? Boolean.FALSE : attr.getMultiple();
                    if(Boolean.FALSE.equals(multiple)){
                        // For non-multiple attributes, replace any existing value(s) with the new one.
                        // This makes update flows idempotent and avoids races where two flows
                        // try to insert/replace the single value concurrently.
                        log.info("saveValue: attributeId={} multiple={} - replacing existing values if any", attributeId, multiple);
                        return valueAttributeUserRepository.findByAttributeId(attributeId)
                                .flatMap(valueAttributeUserRepository::delete)
                                .then(valueAttributeUserRepository.save(value));
                    }
                    return valueAttributeUserRepository.save(value);
                });
    }
}
