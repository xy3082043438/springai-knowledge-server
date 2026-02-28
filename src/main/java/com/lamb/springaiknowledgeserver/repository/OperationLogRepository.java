package com.lamb.springaiknowledgeserver.repository;

import com.lamb.springaiknowledgeserver.entity.OperationLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    @Query("""
        select l from OperationLog l
        where (:userId is null or l.userId = :userId)
          and (:from is null or l.createdAt >= :from)
          and (:to is null or l.createdAt <= :to)
        order by l.createdAt desc
        """)
    List<OperationLog> search(
        @Param("userId") Long userId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );
}
