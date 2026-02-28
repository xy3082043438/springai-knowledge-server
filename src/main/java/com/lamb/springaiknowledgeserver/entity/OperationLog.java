package com.lamb.springaiknowledgeserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "app_operation_log",
    indexes = {
        @Index(name = "idx_oplog_user", columnList = "user_id"),
        @Index(name = "idx_oplog_created", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 120)
    private String username;

    @Column(name = "action", nullable = false, length = 120)
    private String action;

    @Column(name = "resource", length = 120)
    private String resource;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "success", nullable = false)
    private boolean success = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
    }
}
