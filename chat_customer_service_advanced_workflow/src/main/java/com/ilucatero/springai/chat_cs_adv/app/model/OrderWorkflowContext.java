package com.ilucatero.springai.chat_cs_adv.app.model;

public record OrderWorkflowContext(
        String orderId,
        Order.Status orderStatus,
        String deliveryDetails) {
}
