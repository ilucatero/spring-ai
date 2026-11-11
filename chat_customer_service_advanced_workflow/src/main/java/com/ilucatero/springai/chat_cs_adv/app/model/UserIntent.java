package com.ilucatero.springai.chat_cs_adv.app.model;

public record UserIntent(
    IntentType intent,
    String orderId,
    String reason
) {}
