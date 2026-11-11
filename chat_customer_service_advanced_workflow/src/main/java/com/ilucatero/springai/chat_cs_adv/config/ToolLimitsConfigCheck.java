package com.ilucatero.springai.chat_cs_adv.config;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** This class logs some properties to check correctness */
@Component
public class ToolLimitsConfigCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolLimitsConfigCheck.class);

    private final Environment environment;
    private final ApplicationContext context;

    public ToolLimitsConfigCheck(
            Environment environment,
            ApplicationContext context) {
        this.environment = environment;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        var managers = context.getBeansOfType(ToolCallingManager.class);
        var toolCallingManagerClass = managers.isEmpty()
                ? "Aucun ToolCallingManager trouvé."
                : managers.entrySet().stream()
                        .map(entry -> (entry.getKey() + " -> " + entry.getValue().getClass().getName()))
                        .collect(Collectors.joining());

        log.info("""
                \n========== SPRING AI TOOL CONFIG ==========
                    max-total-tool-calls = {}
                    on-limit-exceeded = {}
                    throw-exception-on-error = {}
                    --- ToolCallingManager beans ---
                    {}
                ===========================================
                """,
                environment.getProperty("spring.ai.tools.limits.max-total-tool-calls"),
                environment.getProperty("spring.ai.tools.limits.on-limit-exceeded"),
                environment.getProperty("spring.ai.tools.throw-exception-on-error"),
                toolCallingManagerClass);

    }
}
