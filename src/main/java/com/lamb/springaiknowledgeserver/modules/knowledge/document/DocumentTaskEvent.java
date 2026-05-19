package com.lamb.springaiknowledgeserver.modules.knowledge.document;

public record DocumentTaskEvent(Long documentId, String action, String contentType, String fileName) {
}
