package com.ilucatero.springai.chat_cs_adv.app.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.ilucatero.springai.chat_cs_adv.app.model.WorkflowStatus;
import com.ilucatero.springai.chat_cs_adv.app.repository.CancellationWorkflowRepository;

import jakarta.transaction.Transactional;

@Service
public class CancellationWorkflowStateService {

    private final CancellationWorkflowRepository workflowRepository;

    CancellationWorkflowStateService(CancellationWorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @Transactional
    public boolean transition(String workflowId, WorkflowStatus expectedStatus, WorkflowStatus newStatus) {
        return workflowRepository.transitionStatus(
                workflowId,
                expectedStatus,
                newStatus,
                LocalDateTime.now()) == 1;
    }

    @Transactional
    public void updateFinalStatus(String workflowId, WorkflowStatus newStatus) {

        var workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        if (workflow.getWorkflowStatus() != WorkflowStatus.CANCELLING) {
            throw new IllegalStateException("Workflow is not in CANCELLING state");
        }

        workflow.setWorkflowStatus(newStatus);
        workflowRepository.save(workflow);
    }
}
