package com.ilucatero.springai.chat_cs_adv_mcp.app.mcp.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import com.ilucatero.springai.chat_cs_adv_mcp.app.service.ReturnPolicyService;

@Component
public class ReturnPolicyTools {

    public static final String TOOL_NAME_GET_RETURN_POLICY = "getReturnPolicy";

    private static final Logger log = LoggerFactory.getLogger(ReturnPolicyTools.class);

    private final ReturnPolicyService service;

    public ReturnPolicyTools(ReturnPolicyService service) {
        this.service = service;
    }

    @McpTool(name = TOOL_NAME_GET_RETURN_POLICY, description = "Get the return policy for a product category")
    public String getReturnPolicy(String category) {
        log.info("Requested '{}' avec category: {}", TOOL_NAME_GET_RETURN_POLICY, category);

        var policy = service.getPolicy(category);
        if (policy == null) {
            return "No return policy found for category: " + category;
        }

        return """
                Return period: %d days
                Conditions: %s
                """.formatted(policy.returnPeriodDays(), String.join(", ", policy.conditions()));
    }
}
