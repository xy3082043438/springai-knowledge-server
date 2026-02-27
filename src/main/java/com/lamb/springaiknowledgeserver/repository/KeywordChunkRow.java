package com.lamb.springaiknowledgeserver.repository;

public interface KeywordChunkRow {
    Long getChunkId();
    Long getDocumentId();
    String getContent();
    Integer getPageNumber();
    Integer getChunkIndex();
    String getTitle();
    String getFileName();
    String getContentType();
    Double getScore();
}
