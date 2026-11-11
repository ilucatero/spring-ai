package com.ilucatero.springai.chat_cs_adv.app.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ilucatero.springai.chat_cs_adv.app.model.Order;

@Repository
public class OrderRepository {

    // Simulated in-memory order status storage
    private static final List<Order> ORDER_MAP = List.of(
            new Order("123", Order.Status.PROCESSING, "2024-06-15",
                    "Cheking Order"),
            new Order("321", Order.Status.READY_TO_SHIP, "2024-06-20",
                    "Order checked and ready for shipping"),
            new Order("456", Order.Status.SHIPPING, "2024-06-20",
                    "Order its queued for shipping"),
            new Order("654", Order.Status.SHIPPED, "2024-06-20",
                    "Order shipped and ready for pickup"),
            new Order("789", Order.Status.READY_FOR_PICKUP, "2024-06-20",
                    "Order ready for pickup by UPS on 2024-06-20. Tracking number: 987654321."),
            new Order("987", Order.Status.DELIVERING, "2024-06-20",
                    "Delivering by FEDDEX on 2024-06-20. Tracking number: 987654321."),
            new Order("9877", Order.Status.DELIVERING, "2024-06-20",
                    "Delivered by UPS on 2024-06-20. Tracking number: 987654321."),
            new Order("999", Order.Status.READY_FOR_PICKUP, "2099-06-20",
                    "Order ready for pickup by UPS on 2024-06-20. Tracking number: 987654321."));

    public Order get(String orderId) {
        return ORDER_MAP.stream()
                .filter(order -> order.orderId.equals(orderId))
                .findFirst()
                // See : spring.ai.tools.throw-exception-on-error=true
                // .orElseThrow();
                // .orElseThrow(() -> new RuntimeException("Technical error"));
                .orElse(new Order(orderId, Order.Status.ORDER_NOT_FOUND, null, null));
    }

    public Order.Status cancel(String orderId) {
        // Simulate order cancellation logic
        if (ORDER_MAP.stream()
                .anyMatch(order -> order.orderId.equals(orderId) && !Order.Status.CANCELLED.equals(order.status))) {
            // In a real implementation, you would update the order status in the database
            return Order.Status.CANCELLED; // Indicate that the order was successfully cancelled
        }

        var order = get(orderId);
        order.status = Order.Status.CANCELLED;

        return order.status;
    }

    public String getDeliveryDetails(String orderId) {
        // Simulate fetching delivery details from a database or external service
        return get(orderId).deliveryDetails;
    }
}
