package com.lamb.springaiknowledgeserver.config;

import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAIAutoConfigurationUtil;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AiConfig {

    @Value("${app.openai.connect-timeout-ms:3000}")
    private long openAiConnectTimeoutMs;

    @Value("${app.openai.read-timeout-ms:20000}")
    private long openAiReadTimeoutMs;

    @Bean
    public OpenAiApi openAiApi(
        OpenAiConnectionProperties connectionProperties,
        OpenAiChatProperties chatProperties,
        ObjectProvider<RestClient.Builder> restClientBuilderProvider,
        ObjectProvider<WebClient.Builder> webClientBuilderProvider,
        ObjectProvider<ResponseErrorHandler> responseErrorHandlerProvider
    ) {
        var resolved = OpenAIAutoConfigurationUtil.resolveConnectionProperties(
            connectionProperties,
            chatProperties,
            "chat"
        );
        RestClient.Builder restClientBuilder = restClientBuilderProvider
            .getIfAvailable(RestClient::builder)
            .clone()
            .requestFactory(clientHttpRequestFactory(openAiConnectTimeoutMs, openAiReadTimeoutMs));
        return OpenAiApi.builder()
            .baseUrl(resolved.baseUrl())
            .apiKey(new SimpleApiKey(resolved.apiKey()))
            .headers(resolved.headers())
            .completionsPath(chatProperties.getCompletionsPath())
            .embeddingsPath("/v1/embeddings")
            .restClientBuilder(restClientBuilder)
            .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
            .responseErrorHandler(responseErrorHandlerProvider.getIfAvailable(DefaultResponseErrorHandler::new))
            .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    private SimpleClientHttpRequestFactory clientHttpRequestFactory(long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        if (connectTimeoutMs > 0) {
            factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        }
        if (readTimeoutMs > 0) {
            factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        }
        return factory;
    }
}
