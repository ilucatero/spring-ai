package com.ilucatero.springai.chat_cs_adv.app.model;

public record OrderUserActionConfirmation(
        String confirmationId,
        String workflowId,
        String orderId,
        String orderStatus,
        String deliveryDetails) {
}
