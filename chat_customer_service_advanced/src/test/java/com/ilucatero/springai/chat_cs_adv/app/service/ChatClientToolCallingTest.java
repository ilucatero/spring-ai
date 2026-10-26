package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;

import com.ilucatero.springai.chat_cs_adv.app.support.FakeReturnPolicyTool;
import com.ilucatero.springai.chat_cs_adv.app.support.ScriptedChatModel;
import com.ilucatero.springai.chat_cs_adv.app.support.ScriptedChatResponses;

class ChatClientToolCallingTest {

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
}
