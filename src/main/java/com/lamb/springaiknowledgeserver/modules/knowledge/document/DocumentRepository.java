package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import com.lamb.springaiknowledgeserver.modules.knowledge.document.Document;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("select distinct d from Document d join d.allowedRoles r where r.name in :roleNames")
    List<Document> findVisibleByRoles(@Param("roleNames") Collection<String> roleNames);

    @Query("select distinct d from Document d join d.allowedRoles r where r.name in :roleNames and d.status = 'READY' order by d.updatedAt desc")
    List<Document> findVisibleByRolesOrderByUpdatedAtDesc(@Param("roleNames") Collection<String> roleNames, org.springframework.data.domain.Pageable pageable);

    @Query("""
        select distinct d from Document d
        join d.allowedRoles r
        where r.name in :roleNames
          and (lower(d.title) like lower(concat('%', :query, '%'))
               or lower(d.content) like lower(concat('%', :query, '%')))
        """)
    List<Document> searchVisibleByRoles(
        @Param("roleNames") Collection<String> roleNames,
        @Param("query") String query
    );

    @Query("""
        select count(distinct d) from Document d
        join d.allowedRoles r
        where r.id = :roleId
        """)
    long countByAllowedRoleId(@Param("roleId") Long roleId);

    @Query("select d from Document d where d.status = :status")
    List<Document> findByStatus(@Param("status") DocumentStatus status);

    @Query("""
        select d.contentType as contentType, d.fileName as fileName
        from Document d
        """)
    List<DocumentTypeStatRow> findAllTypeStats();
}


