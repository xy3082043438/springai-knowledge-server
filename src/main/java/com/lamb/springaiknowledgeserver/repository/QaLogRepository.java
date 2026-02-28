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
          and (:from is null or l.createdAt >= :from)
          and (:to is null or l.createdAt <= :to)
        order by l.createdAt desc
        """)
    Page<QaLog> search(
        @Param("userId") Long userId,
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable
    );
}
