package com.lamb.springaiknowledgeserver.repository;

import com.lamb.springaiknowledgeserver.entity.QaLog;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QaLogRepository extends JpaRepository<QaLog, Long> {

    @Query("""
        select l from QaLog l
        where (:userId is null or l.userId = :userId)
          and (:startTime is null or l.createdAt >= :startTime)
          and (:endTime is null or l.createdAt <= :endTime)
        order by l.createdAt desc
        """)
    Page<QaLog> search(
        @Param("userId") Long userId,
        @Param("startTime") Instant from,
        @Param("endTime") Instant to,
        Pageable pageable
    );
}
