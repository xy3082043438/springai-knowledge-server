package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QaRequest {

    @NotBlank
    private String question;
}


