package com.ilucatero.springai.chat_cs_adv.app.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ilucatero.springai.chat_cs_adv.app.model.CancellationWorkflowContext;
import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.WorkflowStatus;
import com.ilucatero.springai.chat_cs_adv.app.repository.CancellationWorkflowRepository;
import com.ilucatero.springai.chat_cs_adv.app.repository.entity.CancellationWorkflowEntity;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderCancelationTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

import jakarta.transaction.Transactional;

@Service
public class OrderCancellationWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(OrderCancellationWorkflowService.class);

    private static final List<Order.Status> CANCELLABLE_STATUS = List.of(
            Order.Status.PROCESSING, Order.Status.READY_TO_SHIP, Order.Status.SHIPPING, Order.Status.SHIPPED);

    private final OrderStatusTool orderStatusTool;
    private final OrderCancelationTool orderCancelationTool;
    private final CancellationWorkflowStateService stateService;
    private final CancellationWorkflowRepository workflowRepository;

    public OrderCancellationWorkflowService(
            OrderStatusTool orderStatusTool,
            OrderCancelationTool orderCancelationTool,
            CancellationWorkflowStateService stateService,
            CancellationWorkflowRepository workflowRepository) {
        this.orderStatusTool = orderStatusTool;
        this.orderCancelationTool = orderCancelationTool;
        this.stateService = stateService;
        this.workflowRepository = workflowRepository;
    }

    @Transactional
    public CancellationWorkflowContext getConfirmation(String sessionId, String confirmationId) {

        log.info("Looking for confirmation: sessionId={}, confirmationId={}",
                sessionId, confirmationId);

        var entity = workflowRepository
                .findBySessionIdAndConfirmationId(sessionId, confirmationId)
                .orElseThrow(() -> {
                    log.warn("No workflow found for confirmationId={} and sessionId={}",
                            confirmationId, sessionId);
                    return new IllegalArgumentException("Confirmation not found");
                });

        log.info("Workflow found: workflowId={}, status={}",
                entity.getWorkflowId(), entity.getWorkflowStatus());

        if (entity.getWorkflowStatus() != WorkflowStatus.WAITING_FOR_CONFIRMATION) {
            throw new IllegalStateException("Confirmation is no longer valid");
        }

        return toContext(entity);
    }

    /**
     * Step 1: Start cancellation workflow.
     */
    @Transactional
    public CancellationWorkflowContext startCancellation(String orderId, String sessionId) {

        // Check order status
        var orderStatus = orderStatusTool.getOrderStatus(orderId);

        // Verify cancellation eligibility
        if (!CANCELLABLE_STATUS.contains(orderStatus)) {
            throw new IllegalStateException("Order cannot be cancelled. Current status: " + orderStatus);
        }

        // Create workflow context
        var workflowId = UUID.randomUUID().toString();
        var confirmationId = UUID.randomUUID().toString();

        var entity = new CancellationWorkflowEntity(
                workflowId,
                sessionId,
                confirmationId,
                orderId,
                orderStatus,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);
        workflowRepository.save(entity);

        log.info("Cancellation workflow started: workflowId={}, orderId={}, sessionId={}, confirmationId={}",
                workflowId, orderId, sessionId, confirmationId);

        return toContext(entity);
    }

    /**
     * Step 2: Resume workflow after human confirmation.
     */
    public CancellationWorkflowContext confirmCancellation(String workflowId, boolean confirmed) {

        var entity = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        if (entity.getWorkflowStatus() != WorkflowStatus.WAITING_FOR_CONFIRMATION) {
            throw new IllegalStateException("Workflow is not waiting for confirmation");
        }

        // User rejected cancellation
        if (!confirmed) {
            boolean transitioned = stateService.transition(
                    workflowId,
                    WorkflowStatus.WAITING_FOR_CONFIRMATION,
                    WorkflowStatus.CANCELLED);
            if (!transitioned) {
                throw new IllegalStateException("Workflow is already being processed");
            }

            return toContext(workflowRepository.findById(workflowId).orElseThrow());
        }

        // 1. Atomically claim the workflow
        boolean claimed = stateService.transition(
                workflowId,
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING);
        if (!claimed) {
            throw new IllegalStateException("Workflow is already being processed");
        }

        // 2. Re-check order status
        var orderStatus = orderStatusTool.getOrderStatus(entity.getOrderId());
        if (!CANCELLABLE_STATUS.contains(orderStatus)) {
            stateService.updateFinalStatus(workflowId, WorkflowStatus.FAILED);

            throw new IllegalStateException("Order cannot be cancelled. Current status: " + orderStatus);
        }

        try {
            // 3. Execute cancellation
            orderCancelationTool.cancelOrder(entity.getOrderId());

            // 4. Persist success
            stateService.updateFinalStatus(workflowId, WorkflowStatus.COMPLETED);

        } catch (RuntimeException e) {

            // Persist failure in a separate transaction
            stateService.updateFinalStatus(workflowId, WorkflowStatus.FAILED);

            throw new IllegalStateException("Cancellation failed for order: " + entity.getOrderId(), e);
        }

        return toContext(workflowRepository.findById(workflowId).orElseThrow());
    }

    @Transactional
    public void cancelPendingWorkflow(String sessionId) {

        var pendingWorkflows = workflowRepository.findAllBySessionIdAndWorkflowStatus(
                sessionId,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);

        for (var entity : pendingWorkflows) {
            entity.setWorkflowStatus(WorkflowStatus.CANCELLED);
        }
    }

    private CancellationWorkflowContext toContext(CancellationWorkflowEntity entity) {
        return new CancellationWorkflowContext(
                entity.getWorkflowId(),
                entity.getOrderId(),
                entity.getOrderStatus(),
                entity.getWorkflowStatus(),
                entity.getConfirmationId());
    }
}
