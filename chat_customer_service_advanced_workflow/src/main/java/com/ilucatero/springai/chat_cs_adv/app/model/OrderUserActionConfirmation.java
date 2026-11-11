package com.ilucatero.springai.chat_cs_adv.app.model;

public record OrderUserActionConfirmation(
                String confirmationId,
                String orderId,
                String status,
                String details) {
}
