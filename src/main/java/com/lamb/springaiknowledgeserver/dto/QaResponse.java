package com.lamb.springaiknowledgeserver.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QaResponse {

    private String answer;
    private List<DocumentResponse> documents;
    private List<QaSourceResponse> sources;
    private Long qaLogId;
}
