package com.ilucatero.springai.chat_cs_adv.app.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;
import com.ilucatero.springai.chat_cs_adv.app.service.DeliveryAssessmentService;

import java.util.List;

public class OrderDeliveranceTool {

    public static final String TOOL_NAME_GET_DELIVERY_DATE = "getDeliveryDate";
    public static final String TOOL_NAME_GET_DELIVERY_DETAILS = "getDeliveryDetails";

    private static final Logger log = LoggerFactory.getLogger(OrderDeliveranceTool.class);

    private final ToolExecutionInfo.Recorder toolExecutionRecorder;
    private final OrderRepository orderRepository;
    private final DeliveryAssessmentService deliveryAssessmentService;

    public OrderDeliveranceTool(ToolExecutionInfo.Recorder toolExecutionRecorder, OrderRepository orderRepository,
            DeliveryAssessmentService deliveryAssessmentService) {
        this.toolExecutionRecorder = toolExecutionRecorder;
        this.orderRepository = orderRepository;
        this.deliveryAssessmentService = deliveryAssessmentService;
    }

    @Tool(name = TOOL_NAME_GET_DELIVERY_DATE, description = "Get the scheduled delivery date and assess whether that date has passed, for an order by its ID.")
    public String getDeliveryDate(@ToolParam(description = "Order identifier") String orderId) {
        return toolExecutionRecorder.record(TOOL_NAME_GET_DELIVERY_DATE,
                List.of("orderId=" + orderId),
                () -> {
                    log.info("EXECUTED : Getting delivery date for order ID: {}", orderId);
                    var deliveryDate = orderRepository.get(orderId).deliveryDate;
                    var assessment = deliveryAssessmentService.assess(deliveryDate);
                    log.info("EXECUTED : order assesment: {}", assessment);
                    return """
                            Scheduled delivery date: %s
                            Delivery assessment: %s
                            """.formatted(deliveryDate, assessment);
                });
    }

    @Tool(name = TOOL_NAME_GET_DELIVERY_DETAILS, description = "Get the shipping details of delivery for an order by its ID")
    public String getDeliveryDetails(@ToolParam(description = "Order identifier") String orderId) {
        return toolExecutionRecorder.record(TOOL_NAME_GET_DELIVERY_DETAILS,
                List.of("orderId=" + orderId),
                () -> {
                    log.info("EXECUTED : Getting delivery details for order ID: {}", orderId);
                    // Simulate delivery details retrieval (in production, call real API)
                    return orderRepository.get(orderId).deliveryDetails;
                });
    }

}
