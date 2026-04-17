package com.lamb.springaiknowledgeserver.core.config;

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

    public static final String DOCUMENT_SUGGESTION_TEMPLATE = """
        你是一个文档分析专家。请根据提供的文档内容，生成3个用户最可能感兴趣的高质量提问。
        要求：
        1. 问题简练、专业且具有代表性。
        2. 严禁编造，问题必须能从文档中找到答案。
        3. 直接返回 JSON 数组格式，如：["问题1", "问题2", "问题3"]。
        
        文档内容:
        {content}
        """;

    private PromptTemplates() {
    }
}


