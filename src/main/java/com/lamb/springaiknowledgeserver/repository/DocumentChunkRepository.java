package com.lamb.springaiknowledgeserver.repository;

import com.lamb.springaiknowledgeserver.entity.DocumentChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    void deleteByDocumentId(Long documentId);
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(Long documentId);
}
