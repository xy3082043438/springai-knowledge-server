package com.lamb.springaiknowledgeserver.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QaSourceResponse {

    private Long chunkId;
    private Long documentId;
    private String title;
    private String fileName;
    private int pageNumber;
    private int chunkIndex;
    private int startOffset;
    private int endOffset;
    private double score;
    private String content;
}
