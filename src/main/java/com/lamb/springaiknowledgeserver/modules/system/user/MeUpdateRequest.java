package com.lamb.springaiknowledgeserver.modules.system.user;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeUpdateRequest {

    @Size(min = 3, max = 64)
    private String username;
}


