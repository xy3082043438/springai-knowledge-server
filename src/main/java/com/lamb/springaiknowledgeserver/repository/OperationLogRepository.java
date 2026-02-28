package com.lamb.springaiknowledgeserver.repository;

import com.lamb.springaiknowledgeserver.entity.OperationLog;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    @Query("""
        select l from OperationLog l
        where (:userId is null or l.userId = :userId)
          and (:startTime is null or l.createdAt >= :startTime)
          and (:endTime is null or l.createdAt <= :endTime)
        order by l.createdAt desc
        """)
    Page<OperationLog> search(
        @Param("userId") Long userId,
        @Param("startTime") Instant from,
        @Param("endTime") Instant to,
        Pageable pageable
    );
}
