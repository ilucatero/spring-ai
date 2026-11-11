package com.ilucatero.springai.chat_cs_adv_mcp.app.model;

import java.util.List;

public record ReturnPolicy(
        int returnPeriodDays,
        List<String> conditions
) {}
