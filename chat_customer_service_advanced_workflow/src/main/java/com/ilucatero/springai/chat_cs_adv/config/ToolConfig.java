package com.ilucatero.springai.chat_cs_adv.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ilucatero.springai.chat_cs_adv.app.model.OrderToolSet;
import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;
import com.ilucatero.springai.chat_cs_adv.app.service.DeliveryAssessmentService;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderCancelationTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderDeliveranceTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

@Configuration
class ToolConfig {

    @Bean
    ToolExecutionInfo.Recorder toolExecutionRecorder() {
        return new ToolExecutionInfo.Recorder();
    }

    @Bean
    OrderStatusTool orderStatusTool(
            ToolExecutionInfo.Recorder recorder,
            OrderRepository orderRepository) {

        return new OrderStatusTool(recorder, orderRepository);
    }

    @Bean
    OrderDeliveranceTool orderDeliveranceTool(
            ToolExecutionInfo.Recorder recorder,
            OrderRepository orderRepository,
            DeliveryAssessmentService deliveryAssessmentService) {

        return new OrderDeliveranceTool(recorder, orderRepository, deliveryAssessmentService);
    }

    @Bean
    OrderCancelationTool orderCancelationTool(
            ToolExecutionInfo.Recorder toolExecutionRecorder,
            OrderRepository orderRepository) {

        return new OrderCancelationTool(toolExecutionRecorder, orderRepository);
    }

    @Bean
    OrderToolSet orderToolSet(
            ToolExecutionInfo.Recorder toolExecutionRecorder,
            OrderStatusTool orderStatusTool,
            OrderDeliveranceTool orderDeliveranceTool,
            OrderCancelationTool orderCancelationTool) {

        return new OrderToolSet(
                toolExecutionRecorder,
                orderStatusTool,
                orderDeliveranceTool,
                orderCancelationTool);
    }
}
