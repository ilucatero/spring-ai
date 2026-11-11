package com.ilucatero.springai.chat_cs_adv.app.service;

import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.OrderWorkflowContext;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderDeliveranceTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.stereotype.Service;

@Service
public class OrderWorkflowService {

    private final OrderStatusTool orderStatusTool;
    private final OrderDeliveranceTool orderDeliveranceTool;
    private final OrderResponseService orderResponseService;
    private final Executor workflowExecutor;

    public OrderWorkflowService(
            OrderStatusTool orderStatusTool,
            OrderDeliveranceTool orderDeliveranceTool,
            OrderResponseService orderResponseService,
            Executor workflowExecutor) {

        this.orderStatusTool = orderStatusTool;
        this.orderDeliveranceTool = orderDeliveranceTool;
        this.orderResponseService = orderResponseService;
        this.workflowExecutor = workflowExecutor;
    }

    public OrderWorkflowContext execute(String orderId) {

        // Note : The workflow is designed to be sequential, meaning that each step
        // depends on the successful completion of the previous one. If any step fails
        // (e.g., due to an exception), the workflow will stop, and no further steps
        // will be executed. This ensures that we do not attempt to retrieve delivery
        // details for an order if we cannot first confirm its status.
        // If real parallelism is needed, we can use CompletableFuture.allOf() to run
        // independent steps concurrently and then combine their results. However, in
        // this case, the steps are dependent on each other, so sequential execution is
        // appropriate. This is a simplified example to illustrate parellel execution
        // and workflow concepts. In a real-world scenario, we would also implement
        // error handling, logging, and possibly retries for robustness.

        // Also notice that, it uses Tools to call the Service/Repository containing the
        // business logic. A better approach would be to use the dedicated Service
        // directly, but this is a simplified example to illustrate the workflow
        // concept.

        // Step 1: Get order status
        CompletableFuture<Order.Status> orderStatusFuture = CompletableFuture.supplyAsync(
                () -> orderStatusTool.getOrderStatus(orderId),
                workflowExecutor);

        // Step 2: Wait for status and conditionally get delivery details
        return orderStatusFuture
                .thenCompose(orderStatus -> {
                    if (orderStatus == Order.Status.SHIPPED) {
                        // Step 3: Get delivery details if the order is shipped
                        return CompletableFuture.supplyAsync(
                                () -> orderDeliveranceTool.getDeliveryDetails(orderId), workflowExecutor)
                                // Step 4: Create and return the workflow context with delivery details
                                .thenApply(deliveryDetails -> new OrderWorkflowContext(
                                        orderId,
                                        orderStatus,
                                        deliveryDetails));
                    }

                    // If the order is not shipped, return the workflow context without delivery
                    return CompletableFuture.completedFuture(
                            new OrderWorkflowContext(
                                    orderId,
                                    orderStatus,
                                    null));
                })
                .join();
    }

    public String executeAndRespond(String orderId) {

        var context = execute(orderId);

        return orderResponseService.generateResponse(context);
    }
}
