package com.ilucatero.springai.chat_cs_adv.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.ilucatero.springai.chat_cs_adv.app.model.WorkflowStatus;
import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.repository.CancellationWorkflowRepository;
import com.ilucatero.springai.chat_cs_adv.app.repository.entity.CancellationWorkflowEntity;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

@Service
public class CancellationWorkflowRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(CancellationWorkflowRecoveryService.class);

    private final CancellationWorkflowRepository workflowRepository;
    private final CancellationWorkflowStateService stateService;
    private final OrderStatusTool orderStatusTool;

    public CancellationWorkflowRecoveryService(
            CancellationWorkflowRepository workflowRepository,
            CancellationWorkflowStateService stateService,
            OrderStatusTool orderStatusTool) {
        this.workflowRepository = workflowRepository;
        this.stateService = stateService;
        this.orderStatusTool = orderStatusTool;
    }

    /**
     * Recovers pending cancellation workflows on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingWorkflows() {

        var workflows = workflowRepository.findAllByWorkflowStatus(WorkflowStatus.CANCELLING);

        log.info("Found {} cancellation workflows to recover", workflows.size());

        for (var workflow : workflows) {
            recoverWorkflow(workflow);
        }
    }

    private void recoverWorkflow(CancellationWorkflowEntity workflow) {
        try {
            var orderStatus = orderStatusTool.getOrderStatus(workflow.getOrderId());

            if (orderStatus == Order.Status.CANCELLED) {

                stateService.updateFinalStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED);

                log.info("Recovered cancellation workflow: {}", workflow.getWorkflowId());

            } else {
                log.warn(
                        "Cancellation workflow {} requires manual investigation. Order {} has status {}",
                        workflow.getWorkflowId(),
                        workflow.getOrderId(),
                        orderStatus);
            }

        } catch (RuntimeException e) {
            log.error("Unable to recover cancellation workflow {}", workflow.getWorkflowId(), e);
        }
    }
}
