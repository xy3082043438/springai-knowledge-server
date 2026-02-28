package com.lamb.springaiknowledgeserver.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QaFeedbackRequest {

    @NotNull
    private Long qaLogId;

    @NotNull
    private Boolean helpful;

    @Size(max = 1000)
    private String comment;
}
