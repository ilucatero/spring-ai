package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;

import com.ilucatero.springai.chat_cs_adv.app.model.IntentType;
import com.ilucatero.springai.chat_cs_adv.app.model.UserIntent;
import com.ilucatero.springai.chat_cs_adv.app.repository.SystemPromptRepository;

@ExtendWith(MockitoExtension.class)
class IntentServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClientRequestSpec requestSpec;
    @Mock
    private CallResponseSpec responseSpec;

    private IntentService intentService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(SystemPromptRepository.getUserIntentPrompt())).thenReturn(requestSpec);
        when(requestSpec.messages(List.of())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        intentService = new IntentService(chatClientBuilder);
    }

    @ParameterizedTest
    @CsvSource({
            "'What is the status of order 123?', ORDER_STATUS",
            "'Is order 123 shipped?', ORDER_STATUS",
            "'Where is my order 123?', ORDER_STATUS",
            "'Give me all details about order 123', GENERAL_QUESTION",
            "'Show me the information about order 123', GENERAL_QUESTION",
            "'What is the delivery address of order 123?', GENERAL_QUESTION",
            "'I want to cancel order 123', CANCEL_ORDER"
    })
    void shouldDetectExpectedIntentForMessage(String message, IntentType expectedType) {

        var expectedIntent = new UserIntent(expectedType, "123", null);
        when(requestSpec.user(message)).thenReturn(requestSpec);
        when(responseSpec.entity(UserIntent.class)).thenReturn(expectedIntent);

        var intent = intentService.detectIntent(message, List.of());

        assertEquals(expectedIntent, intent);
        assertEquals("123", intent.orderId());
        verify(chatClientBuilder).build();
        verify(chatClient).prompt();
        verify(requestSpec).system(SystemPromptRepository.getUserIntentPrompt());
        verify(requestSpec).user(message);
        verify(requestSpec).messages(List.of());
        verify(requestSpec).call();
        verify(responseSpec).entity(UserIntent.class);
        verifyNoMoreInteractions(chatClientBuilder, chatClient, requestSpec, responseSpec);
    }
}
