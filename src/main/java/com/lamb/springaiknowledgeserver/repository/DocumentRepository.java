package com.lamb.springaiknowledgeserver.repository;

import com.lamb.springaiknowledgeserver.entity.Document;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("select distinct d from Document d join d.allowedRoles r where r.name in :roleNames")
    List<Document> findVisibleByRoles(@Param("roleNames") Collection<String> roleNames);

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
}
