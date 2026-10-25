package com.ilucatero.springai.chat_cs_adv.app.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;

import java.util.List;

public class OrderStatusTool {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusTool.class);

    private final ToolExecutionInfo.Recorder toolExecutionRecorder;
    private final OrderRepository orderRepository;

    public OrderStatusTool(ToolExecutionInfo.Recorder toolExecutionRecorder, OrderRepository orderRepository) {
        this.toolExecutionRecorder = toolExecutionRecorder;
        this.orderRepository = orderRepository;
    }

    @Tool(description = "Get the status of an order by its ID")
    public Order.Status getOrderStatus(@ToolParam(description = "Order identifier") String orderId) {
        return toolExecutionRecorder.record("checkOrderStatus",
                List.of("orderId=" + orderId),
                () -> {
                    log.info("EXECUTED : Checking order status for ID: {}", orderId);
                    return orderRepository.get(orderId).status;
                });
    }

}
