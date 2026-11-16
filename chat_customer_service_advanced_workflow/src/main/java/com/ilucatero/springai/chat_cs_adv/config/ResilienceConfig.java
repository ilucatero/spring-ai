package com.ilucatero.springai.chat_cs_adv.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration
@EnableResilientMethods // active les @Retryable
public class ResilienceConfig {
}
