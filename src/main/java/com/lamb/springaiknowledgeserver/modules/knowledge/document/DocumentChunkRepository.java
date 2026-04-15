package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentChunk;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    void deleteByDocumentId(Long documentId);
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(Long documentId);

    @Query(value = """
        select c.id as chunkId,
               c.content as content,
               c.page_number as pageNumber,
               c.chunk_index as chunkIndex,
               c.start_offset as startOffset,
               c.end_offset as endOffset,
               d.id as documentId,
               d.title as title,
               d.file_name as fileName,
               d.content_type as contentType,
               ts_rank_cd(
                   to_tsvector(cast(:config as regconfig), c.content),
                   plainto_tsquery(cast(:config as regconfig), :query)
               ) as score
        from app_document_chunk c
        join app_document d on d.id = c.document_id
        join app_document_role dr on dr.document_id = d.id
        join app_role r on r.id = dr.role_id
        where r.name in (:roleNames)
          and d.status = 'READY'
          and to_tsvector(cast(:config as regconfig), c.content)
              @@ plainto_tsquery(cast(:config as regconfig), :query)
        order by score desc
        limit :limit
        """, nativeQuery = true)
    List<KeywordChunkRow> searchChunksByKeyword(
        @Param("roleNames") Collection<String> roleNames,
        @Param("config") String config,
        @Param("query") String query,
        @Param("limit") int limit
    );
}



