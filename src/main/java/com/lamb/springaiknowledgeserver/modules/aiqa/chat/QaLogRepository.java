package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QaLogRepository extends JpaRepository<QaLog, Long> {

    @Query("""
        select l from QaLog l
        where (:userId is null or l.userId = :userId)
          and (l.createdAt >= coalesce(:startTime, l.createdAt))
          and (l.createdAt <= coalesce(:endTime, l.createdAt))
        order by l.createdAt desc
        """)
    Page<QaLog> search(
        @Param("userId") Long userId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        Pageable pageable
    );

    @Query("""
        select l.createdAt as createdAt, l.question as question
        from QaLog l
        where l.createdAt >= :startTime
        order by l.createdAt asc
        """)
    List<QaQuestionActivityRow> findQuestionActivitiesSince(@Param("startTime") Instant startTime);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant startTime, Instant endTime);
}


