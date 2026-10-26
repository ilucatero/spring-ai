package com.ilucatero.springai.chat_cs_adv.app.support;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;

public class FakeReturnPolicyTool implements ToolCallback {

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name("getReturnPolicy")
                .description("Get the return policy for a product category")
                .inputSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "category": {
                              "type": "string"
                            }
                          },
                          "required": ["category"]
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
        System.out.println("FakeReturnPolicyTool called with: " + input);

        if (input.contains("electronics")) {
            return "Return period: 30 days";
        }

        return "No return policy found";
    }

    @Override
    public String call(String input, ToolContext context) {
        return call(input);
    }
}
