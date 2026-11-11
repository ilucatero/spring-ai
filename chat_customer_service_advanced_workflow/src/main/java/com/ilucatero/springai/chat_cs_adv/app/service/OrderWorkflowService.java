package com.ilucatero.springai.chat_cs_adv.app.service;

import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.OrderWorkflowContext;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderDeliveranceTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

import org.springframework.stereotype.Service;

@Service
public class OrderWorkflowService {

    private final OrderStatusTool orderStatusTool;
    private final OrderDeliveranceTool orderDeliveranceTool;
    private final OrderResponseService orderResponseService;

    public OrderWorkflowService(
            OrderStatusTool orderStatusTool,
            OrderDeliveranceTool orderDeliveranceTool,
            OrderResponseService orderResponseService) {

        this.orderStatusTool = orderStatusTool;
        this.orderDeliveranceTool = orderDeliveranceTool;
        this.orderResponseService = orderResponseService;
    }

    public OrderWorkflowContext execute(String orderId) {

        // Note : The workflow is designed to be sequential, meaning that each step depends on the successful
        //  completion of the previous one. If any step fails (e.g., due to an exception), the workflow will stop,
        //  and no further steps will be executed. This ensures that we do not attempt to retrieve delivery details
        //  for an order if we cannot first confirm its status.
        // Also notice that, it uses Tools to call the Service/Repository containing the
        //  business logic. A better approach would be to use the dedicated Service directly, but this is a
        //  simplified example to illustrate the workflow concept.

        // Step 1: Check order status
        var orderStatus = orderStatusTool.getOrderStatus(orderId);

        // Step 2: Get delivery details
        String deliveryDetails = null;
        if (Order.Status.SHIPPED.equals(orderStatus)) {
            deliveryDetails = orderDeliveranceTool.getDeliveryDetails(orderId);
        }

        // Step 3: Build workflow context
        return new OrderWorkflowContext(
                orderId,
                orderStatus,
                deliveryDetails);
    }

    public String executeAndRespond(String orderId) {

        var context = execute(orderId);

        return orderResponseService.generateResponse(context);
    }
}
