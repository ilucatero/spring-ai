package com.ilucatero.springai.chat_cs_adv.app.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.ilucatero.springai.chat_cs_adv.app.model.CancellationWorkflowContext;
import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.WorkflowStatus;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderCancelationTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

@Service
public class OrderCancellationWorkflowService {

    private static final List<Order.Status> CANCELLABLE_STATUS = List.of(
            Order.Status.PROCESSING, Order.Status.READY_TO_SHIP, Order.Status.SHIPPING, Order.Status.SHIPPED);

    private final OrderStatusTool orderStatusTool;
    private final OrderCancelationTool orderCancelationTool;

    private final Map<String, CancellationWorkflowContext> workflows = new ConcurrentHashMap<>();

    public OrderCancellationWorkflowService(
            OrderStatusTool orderStatusTool,
            OrderCancelationTool orderCancelationTool) {
        this.orderStatusTool = orderStatusTool;
        this.orderCancelationTool = orderCancelationTool;
    }

    /**
     * Step 1: Start cancellation workflow.
     */
    public CancellationWorkflowContext startCancellation(String orderId) {

        // Check order status
        var orderStatus = orderStatusTool.getOrderStatus(orderId);

        // Verify cancellation eligibility
        if (!CANCELLABLE_STATUS.contains(orderStatus)) {
            throw new IllegalStateException("Order cannot be cancelled. Current status: " + orderStatus);
        }

        // Create workflow context
        var workflowId = UUID.randomUUID().toString();

        var context = new CancellationWorkflowContext(
                workflowId,
                orderId,
                orderStatus,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);

        workflows.put(workflowId, context);

        return context;
    }

    /**
     * Step 2: Resume workflow after human confirmation.
     */
    public synchronized CancellationWorkflowContext confirmCancellation(String workflowId, boolean confirmed) {

        var context = workflows.get(workflowId);
        if (context == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        if (context.workflowStatus() != WorkflowStatus.WAITING_FOR_CONFIRMATION) {
            throw new IllegalStateException("Workflow is not waiting for confirmation");
        }

        // User rejected cancellation
        if (!confirmed) {
            context.setWorkflowStatus(WorkflowStatus.CANCELLED);
            return context;
        }

        // User confirmed cancellation

        try {
            // Re-check order status before cancellation
            var currentStatus = orderStatusTool.getOrderStatus(context.orderId());
            if (!CANCELLABLE_STATUS.contains(currentStatus)) {
                context.setWorkflowStatus(WorkflowStatus.FAILED);
                throw new IllegalStateException("Order can no longer be cancelled. Current status: " + currentStatus);
            }

            // Proceed with cancellation
            orderCancelationTool.cancelOrder(context.orderId());

            context.setWorkflowStatus(WorkflowStatus.COMPLETED);

        } catch (RuntimeException e) {
            context.setWorkflowStatus(WorkflowStatus.FAILED);
            throw new IllegalStateException("Unable to cancel order: " + context.orderId(), e);
        }

        return context;
    }
}
