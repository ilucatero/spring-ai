package com.ilucatero.springai.chat_cs_adv.app.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;

import java.util.List;

public class OrderCancelationTool {

    public static final String TOOL_NAME_CANCEL_ORDER = "cancelOrder";

    private static final Logger log = LoggerFactory.getLogger(OrderCancelationTool.class);

    private final ToolExecutionInfo.Recorder toolExecutionRecorder;
    private final OrderRepository orderRepository;

    public OrderCancelationTool(ToolExecutionInfo.Recorder toolExecutionRecorder, OrderRepository orderRepository) {
        this.toolExecutionRecorder = toolExecutionRecorder;
        this.orderRepository = orderRepository;
    }

    @Tool(name = TOOL_NAME_CANCEL_ORDER, description = "Request cancellation of an order by ID. The application verifies its status and requires explicit user confirmation before it is cancelled.")
    public String cancelOrder(@ToolParam(description = "Order identifier") String orderId) {
        return toolExecutionRecorder.record(TOOL_NAME_CANCEL_ORDER,
                List.of("orderId=" + orderId),
                () -> {
                    log.info("EXECUTED : Cancelling order for ID: {}", orderId);
                    return orderRepository.cancel(orderId).name();
                });
    }

}
