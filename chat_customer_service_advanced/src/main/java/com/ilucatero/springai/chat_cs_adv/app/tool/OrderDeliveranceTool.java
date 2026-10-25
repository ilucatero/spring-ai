package com.ilucatero.springai.chat_cs_adv.app.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;

import java.util.List;

public class OrderDeliveranceTool {

    private static final Logger log = LoggerFactory.getLogger(OrderDeliveranceTool.class);

    private final ToolExecutionInfo.Recorder toolExecutionRecorder;
    private final OrderRepository orderRepository;

    public OrderDeliveranceTool(ToolExecutionInfo.Recorder toolExecutionRecorder, OrderRepository orderRepository) {
        this.toolExecutionRecorder = toolExecutionRecorder;
        this.orderRepository = orderRepository;
    }

    @Tool(description = "Get the programmed or delivered date, dependeing on its status, for an order by its ID")
    public String getDeliveryDate(@ToolParam(description = "Order identifier") String orderId) {
        return toolExecutionRecorder.record("getDeliveryDate",
                List.of("orderId=" + orderId),
                () -> {
                    log.info("EXECUTED : Getting delivery date for order ID: {}", orderId);
                    return orderRepository.get(orderId).deliveryDate;
                });
    }

    @Tool(description = "Get the shipping details of delivery for an order by its ID")
    public String getDeliveryDetails(@ToolParam(description = "Order identifier") String orderId) {
        return toolExecutionRecorder.record("getDeliveryDetails",
                List.of("orderId=" + orderId),
                () -> {
                    log.info("EXECUTED : Getting delivery details for order ID: {}", orderId);
                    // Simulate delivery details retrieval (in production, call real API)
                    return orderRepository.get(orderId).deliveryDetails;
                });
    }

}
