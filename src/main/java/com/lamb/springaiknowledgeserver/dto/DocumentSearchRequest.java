package com.lamb.springaiknowledgeserver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentSearchRequest {

    @NotBlank
    private String query;
}
