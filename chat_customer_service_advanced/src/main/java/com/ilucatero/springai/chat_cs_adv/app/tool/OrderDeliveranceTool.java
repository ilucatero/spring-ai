package com.ilucatero.springai.chat_cs_adv.app.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;

import java.util.List;

public class OrderDeliveranceTool {

    private static final Logger log = LoggerFactory.getLogger(OrderDeliveranceTool.class);

    private final ToolExecutionInfo.Recorder toolExecutionRecorder;

    public OrderDeliveranceTool(ToolExecutionInfo.Recorder toolExecutionRecorder) {
        this.toolExecutionRecorder = toolExecutionRecorder;
    }

    @Tool(description = "Get the date of delivery for an order by its ID")
    public String getDeliveryDate(@ToolParam(description = "Order identifier") String orderId) {
        return toolExecutionRecorder.record("getDeliveryDate",
                List.of("orderId=" + orderId),
                () -> {
                    log.info("Getting delivery date for order ID: {}", orderId);
                    // Simulate delivery date retrieval (in production, call real API)
                    return switch (orderId) {
                        case "123" -> "2024-06-15";
                        case "456" -> "2024-06-20";
                        default -> "DELIVERY_DATE_NOT_FOUND";
                    };
                });
    }

    @Tool(description = "Get the details of delivery for an order by its ID")
    public String getDeliveryDetails(@ToolParam(description = "Order identifier") String orderId) {
        return toolExecutionRecorder.record("getDeliveryDetails",
                List.of("orderId=" + orderId),
                () -> {
                    log.info("Getting delivery details for order ID: {}", orderId);
                    // Simulate delivery details retrieval (in production, call real API)
                    return switch (orderId) {
                        case "123" -> "Order 123 will be delivered by FedEx on 2024-06-15. Tracking number: 123456789.";
                        case "456" -> "Order 456 will be delivered by UPS on 2024-06-20. Tracking number: 987654321.";
                        case "999" ->
                            "Order 999 is ready for pickup at the store. Please bring your ID and order confirmation.";
                        default -> "DELIVERY_DETAILS_NOT_FOUND";
                    };
                });
    }

}
