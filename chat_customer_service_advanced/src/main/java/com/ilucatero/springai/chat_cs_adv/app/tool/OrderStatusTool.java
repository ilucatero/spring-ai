package com.ilucatero.springai.chat_cs_adv.app.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;

import java.util.List;

public class OrderStatusTool {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusTool.class);

    private final ToolExecutionInfo.Recorder toolExecutionRecorder;

    public OrderStatusTool(ToolExecutionInfo.Recorder toolExecutionRecorder) {
        this.toolExecutionRecorder = toolExecutionRecorder;
    }

    @Tool(description = "Get the status of an order by its ID")
    public String getOrderStatus(@ToolParam(description = "Order identifier") String orderId) {
        return toolExecutionRecorder.record("checkOrderStatus",
                List.of("orderId=" + orderId),
                () -> {
                    log.info("Checking order status for ID: {}", orderId);
                    return switch (orderId) {
                        case "123" -> "SHIPPED";
                        case "456" -> "DELIVERED";
                        case "999" -> "READY_FOR_PICKUP";
                        default -> "ORDER_NOT_FOUND";
                    };
                });
    }

}
