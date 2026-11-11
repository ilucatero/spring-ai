package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.ilucatero.springai.chat_cs_adv.app.support.FakeOrderStatusTool;
import com.ilucatero.springai.chat_cs_adv.app.support.FakeReturnPolicyTool;
import com.ilucatero.springai.chat_cs_adv.app.support.ScriptedChatModel;
import com.ilucatero.springai.chat_cs_adv.app.support.ScriptedChatResponses;

class ChatClientToolCallingTest {

    @Test
    void shouldReturnDirectAnswerWithoutCallingTool() {
        var model = new ScriptedChatModel(ScriptedChatResponses.finalAnswer("Bonjour !"));

        var chatClient = ChatClient.builder(model).build();

        var answer = chatClient.prompt()
                .user("Dis bonjour.")
                .call()
                .content();

        assertThat(answer).isEqualTo("Bonjour !");
    }

    /**
     * Checks the execution flow :
     * 1. ScriptedChatModel returns a call to getReturnPolicy.
     * 2. ToolCallingAdvisor detects the call and runs FakeReturnPolicyTool.
     * 3. The tool output is passed to the model.
     * 4. The model returns the final answer.
     * 5. The test verifies that the answer is correct.
     */
    @Test
    void shouldExecuteToolAndReturnFinalAnswer() {
        var model = new ScriptedChatModel(
                ScriptedChatResponses.toolCall("getReturnPolicy", "{\"category\":\"electronics\"}"),
                ScriptedChatResponses.finalAnswer("Electronics can be returned within 30 days."));

        var chatClient = ChatClient.builder(model).build();

        var tool = new FakeReturnPolicyTool();

        var answer = chatClient.prompt()
                .user("What is the return policy for electronics?")
                .tools(tool)
                .advisors(advisors -> System.out.println("Advisors :" + advisors))
                .call()
                .content();

        assertThat(answer).isEqualTo("Electronics can be returned within 30 days.");
    }

    @Test
    void shouldExecuteTwoToolsSuccessively() {
        var returnPolicyTool = new FakeReturnPolicyTool();
        var orderStatusTool = new FakeOrderStatusTool();

        var model = new ScriptedChatModel(
                ScriptedChatResponses.toolCall("getReturnPolicy", "{\"category\":\"electronics\"}"),
                ScriptedChatResponses.toolCall("getOrderStatus", "{\"orderId\":\"123\"}"),
                ScriptedChatResponses.finalAnswer("Order shipped. Electronics can be returned within 30 days."));

        var chatClient = ChatClient.builder(model).build();

        var answer = chatClient.prompt()
                .user("Check order 123 and tell me the return policy for electronics.")
                .tools(returnPolicyTool, orderStatusTool)
                .call()
                .content();

        assertThat(answer).isEqualTo("Order shipped. Electronics can be returned within 30 days.");
    }

    @Test
    void shouldFailWhenRequestedToolDoesNotExist() {
        var model = new ScriptedChatModel(
                ScriptedChatResponses.toolCall("unknownTool", "{\"value\":\"test\"}"));

        var chatClient = ChatClient.builder(model).build();

        assertThatThrownBy(() -> chatClient.prompt()
                .user("Use unknownTool.")
                .call()
                .content())
                .isInstanceOf(RuntimeException.class);
    }
}
