package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatDbInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        log.info("Ensuring chat session database schema exists...");
        try {
            // Create app_chat_session table if not exists (Postgres syntax)
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_chat_session (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    title VARCHAR(255),
                    latest_question VARCHAR(512),
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // Add session_id column to app_qa_log if not exists
            jdbcTemplate.execute("""
                DO $$ 
                BEGIN 
                    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                                   WHERE table_name='app_qa_log' AND column_name='session_id') THEN 
                        ALTER TABLE app_qa_log ADD COLUMN session_id BIGINT;
                        CREATE INDEX idx_qalog_session ON app_qa_log (session_id);
                    END IF; 
                END $$;
            """);
            
            log.info("Chat session database schema ensured.");
        } catch (Exception e) {
            log.error("Failed to initialize chat schema manually", e);
        }
    }
}
