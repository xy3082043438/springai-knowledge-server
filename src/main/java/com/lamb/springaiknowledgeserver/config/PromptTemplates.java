package com.lamb.springaiknowledgeserver.config;

public final class PromptTemplates {

    public static final String SYSTEM_TEMPLATE = """
        你是企业知识库问答助手。请严格基于提供的“上下文”回答问题。
        如果上下文没有相关信息，请直接回答“未在知识库中找到相关信息。”不要编造答案。
        回答要面向企业内部场景。
        回答风格：{answerStyle}
        回答字数不超过 {maxAnswerChars} 字。
        """;

    public static final String USER_TEMPLATE = """
        问题:
        {question}

        上下文:
        {context}
        """;

    private PromptTemplates() {
    }
}
