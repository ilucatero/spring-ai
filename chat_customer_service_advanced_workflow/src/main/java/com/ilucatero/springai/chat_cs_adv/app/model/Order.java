package com.ilucatero.springai.chat_cs_adv.app.model;

public class Order {

    public enum Status {
        ORDER_NOT_FOUND, UNKNOWN,
        PROCESSING, READY_TO_SHIP, SHIPPING, SHIPPED, READY_FOR_PICKUP, DELIVERING, DELIVERED,
        CANCELLED
    }

    public String orderId;
    public Status status;
    public String deliveryDate;
    public String deliveryDetails;

    public Order(String orderId, Status status, String deliveryDate, String deliveryDetails) {
        this.orderId = orderId;
        this.status = status;
        this.deliveryDate = deliveryDate;
        this.deliveryDetails = deliveryDetails;
    }
}
