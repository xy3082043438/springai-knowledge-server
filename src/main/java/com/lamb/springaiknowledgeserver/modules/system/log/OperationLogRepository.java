package com.lamb.springaiknowledgeserver.modules.system.log;

import com.lamb.springaiknowledgeserver.modules.system.log.OperationLog;
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
          and (l.createdAt >= coalesce(:startTime, l.createdAt))
          and (l.createdAt <= coalesce(:endTime, l.createdAt))
        order by l.createdAt desc
        """)
    Page<OperationLog> search(
        @Param("userId") Long userId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        Pageable pageable
    );

    void deleteAllByCreatedAtBefore(Instant time);
}


