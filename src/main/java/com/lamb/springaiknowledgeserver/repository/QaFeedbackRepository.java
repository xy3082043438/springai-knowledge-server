package com.lamb.springaiknowledgeserver.repository;

import com.lamb.springaiknowledgeserver.entity.QaFeedback;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QaFeedbackRepository extends JpaRepository<QaFeedback, Long> {

    @Query("""
        select f from QaFeedback f
        where (:userId is null or f.userId = :userId)
          and (:from is null or f.createdAt >= :from)
          and (:to is null or f.createdAt <= :to)
        order by f.createdAt desc
        """)
    List<QaFeedback> search(
        @Param("userId") Long userId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );
}
