package com.ilucatero.springai.chat_cs_adv_mcp.app.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.ilucatero.springai.chat_cs_adv_mcp.app.model.ReturnPolicy;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class ReturnPolicyService {

    private final Map<String, ReturnPolicy> policies;

    public ReturnPolicyService(JsonMapper jsonMapper) throws IOException {
        try (var input = new ClassPathResource("data/return-policies.json").getInputStream()) {
            this.policies = jsonMapper.readValue(
                    input,
                    new TypeReference<Map<String, ReturnPolicy>>() {
                    });
        }
    }

    /**
     *
     * @param category -
     * @return the found return policy based on the passed ategory, or {@code null}
     */
    public ReturnPolicy getPolicy(String category) {
        return policies.get(category.toLowerCase());
    }
}
