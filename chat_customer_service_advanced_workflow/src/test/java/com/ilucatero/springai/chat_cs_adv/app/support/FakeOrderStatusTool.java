package com.ilucatero.springai.chat_cs_adv.app.support;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

public class FakeOrderStatusTool implements ToolCallback {

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name("getOrderStatus")
                .description("Get the status of an order")
                .inputSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "orderId": {
                              "type": "string"
                            }
                          },
                          "required": ["orderId"]
                        }
                        """)
                .build();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return ToolMetadata.builder().build();
    }

    @Override
    public String call(String input) {
        System.out.println("FakeOrderStatusTool called with: " + input);
        return "Order shipped";
    }

    @Override
    public String call(String input, ToolContext context) {
        return call(input);
    }
}
