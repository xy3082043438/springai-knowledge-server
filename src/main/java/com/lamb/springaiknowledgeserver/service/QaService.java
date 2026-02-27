package com.lamb.springaiknowledgeserver.service;

import com.lamb.springaiknowledgeserver.dto.DocumentResponse;
import com.lamb.springaiknowledgeserver.dto.QaResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QaService {

    private final DocumentService documentService;

    public QaResponse answer(String roleName, String question) {
        List<DocumentResponse> documents = documentService.searchVisible(roleName, question).stream()
            .map(DocumentResponse::from)
            .toList();
        String answer = documents.isEmpty()
            ? "???????????"
            : "??????????????????????????";
        return new QaResponse(answer, documents);
    }
}
