package com.lamb.springaiknowledgeserver.dto;

import com.lamb.springaiknowledgeserver.entity.Document;
import com.lamb.springaiknowledgeserver.entity.DocumentChunk;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentChunkPreviewResponse {

    private Long chunkId;
    private Long documentId;
    private String title;
    private String fileName;
    private int pageNumber;
    private int chunkIndex;
    private int startOffset;
    private int endOffset;
    private String content;

    public static DocumentChunkPreviewResponse from(DocumentChunk chunk) {
        Document document = chunk.getDocument();
        return new DocumentChunkPreviewResponse(
            chunk.getId(),
            document == null ? null : document.getId(),
            document == null ? null : document.getTitle(),
            document == null ? null : document.getFileName(),
            chunk.getPageNumber(),
            chunk.getChunkIndex(),
            chunk.getStartOffset(),
            chunk.getEndOffset(),
            chunk.getContent()
        );
    }
}
