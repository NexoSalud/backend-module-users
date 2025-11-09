package com.reactive.nexo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserWithAttributesDTO {
    private Integer id;
    private String name;
    private String identificationNumber;
    private String identificationType;
    private List<AttributeWithValuesDTO> attributes;
}
