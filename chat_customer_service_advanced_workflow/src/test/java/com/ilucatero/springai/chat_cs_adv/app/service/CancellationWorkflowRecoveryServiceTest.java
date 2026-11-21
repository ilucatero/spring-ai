package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ilucatero.springai.chat_cs_adv.app.model.WorkflowStatus;
import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.repository.CancellationWorkflowRepository;
import com.ilucatero.springai.chat_cs_adv.app.repository.entity.CancellationWorkflowEntity;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

@ExtendWith(MockitoExtension.class)
public class CancellationWorkflowRecoveryServiceTest {

    @Mock
    private OrderStatusTool orderStatusTool;
    @Mock
    private CancellationWorkflowRepository workflowRepository;
    @Mock
    private CancellationWorkflowStateService stateService;

    private CancellationWorkflowRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new CancellationWorkflowRecoveryService(workflowRepository, stateService, orderStatusTool);
    }

    @Test
    void shouldCompleteWorkflowWhenOrderWasAlreadyCancelled() {
        // Given
        var workflow = new CancellationWorkflowEntity(
                "workflow-1",
                "session-1",
                "confirmation-1",
                "123",
                Order.Status.PROCESSING,
                WorkflowStatus.CANCELLING);

        when(workflowRepository.findAllByWorkflowStatus(WorkflowStatus.CANCELLING))
                .thenReturn(List.of(workflow));

        when(orderStatusTool.getOrderStatus("123"))
                .thenReturn(Order.Status.CANCELLED);

        // When
        recoveryService.recoverPendingWorkflows();

        // Then
        verify(orderStatusTool).getOrderStatus("123");

        verify(stateService).updateFinalStatus(
                "workflow-1",
                WorkflowStatus.COMPLETED);
    }

    @Test
    void shouldKeepWorkflowInCancellingWhenOrderIsNotCancelled() {
        // Given
        var workflow = new CancellationWorkflowEntity(
                "workflow-1",
                "session-1",
                "confirmation-1",
                "123",
                Order.Status.PROCESSING,
                WorkflowStatus.CANCELLING);

        when(workflowRepository.findAllByWorkflowStatus(WorkflowStatus.CANCELLING))
                .thenReturn(List.of(workflow));

        when(orderStatusTool.getOrderStatus("123"))
                .thenReturn(Order.Status.PROCESSING);

        // When
        recoveryService.recoverPendingWorkflows();

        // Then
        verify(orderStatusTool).getOrderStatus("123");

        verifyNoInteractions(stateService);
    }

    @Test
    void shouldKeepWorkflowInCancellingWhenStatusLookupFails() {
        // Given
        var workflow = new CancellationWorkflowEntity(
                "workflow-1",
                "session-1",
                "confirmation-1",
                "123",
                Order.Status.PROCESSING,
                WorkflowStatus.CANCELLING);

        when(workflowRepository.findAllByWorkflowStatus(WorkflowStatus.CANCELLING))
                .thenReturn(List.of(workflow));

        when(orderStatusTool.getOrderStatus("123"))
                .thenThrow(new RuntimeException("Service unavailable"));

        // When
        recoveryService.recoverPendingWorkflows();

        // Then
        verify(orderStatusTool).getOrderStatus("123");
        verifyNoInteractions(stateService);
    }

}
