package com.ilucatero.springai.chat_cs_adv.app.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;

import java.util.List;

public class OrderCancelationTool {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelationTool.class);

    private final ToolExecutionInfo.Recorder toolExecutionRecorder;

    public OrderCancelationTool(ToolExecutionInfo.Recorder toolExecutionRecorder) {
        this.toolExecutionRecorder = toolExecutionRecorder;
    }

    @Tool(description = "Request cancellation of an order by ID. The application verifies its status and requires explicit user confirmation before it is cancelled.")
    public String cancelOrder(@ToolParam(description = "Order identifier") String orderId) {
        return toolExecutionRecorder.record("cancelOrder",
                List.of("orderId=" + orderId),
                () -> {
                    log.info("Cancelling order for ID: {}", orderId);
                    return switch (orderId) {
                        case "123" -> "CANCELLED";
                        case "456" -> "DELIVERED";
                        default -> "ORDER_NOT_FOUND";
                    };
                });
    }

}
