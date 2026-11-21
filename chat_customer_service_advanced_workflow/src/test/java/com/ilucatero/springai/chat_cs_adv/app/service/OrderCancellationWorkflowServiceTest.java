package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ilucatero.springai.chat_cs_adv.app.model.CancellationWorkflowContext;
import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.WorkflowStatus;
import com.ilucatero.springai.chat_cs_adv.app.repository.CancellationWorkflowRepository;
import com.ilucatero.springai.chat_cs_adv.app.repository.entity.CancellationWorkflowEntity;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderCancelationTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

@ExtendWith(MockitoExtension.class)
class OrderCancellationWorkflowServiceTest {

    private static final String ORDER_ID = "123";
    private static final String SESSION_ID = "session-1";

    @Mock
    private OrderStatusTool orderStatusTool;
    @Mock
    private OrderCancelationTool orderCancelationTool;
    @Mock
    private CancellationWorkflowRepository workflowRepository;
    @Mock
    private CancellationWorkflowStateService stateService;

    private OrderCancellationWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new OrderCancellationWorkflowService(
                orderStatusTool,
                orderCancelationTool,
                stateService,
                workflowRepository);
    }

    @Test
    void shouldCompleteWorkflowWhenProcessingOrderCancellationIsConfirmed() {
        // Given
        var context = newContext(
                ORDER_ID,
                Order.Status.PROCESSING,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);

        var initialEntity = newEntity(context, WorkflowStatus.WAITING_FOR_CONFIRMATION);
        var completedEntity = newEntity(context, WorkflowStatus.COMPLETED);

        when(workflowRepository.findById(context.workflowId()))
                .thenReturn(Optional.of(initialEntity), Optional.of(completedEntity));

        when(stateService.transition(
                context.workflowId(),
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING))
                .thenReturn(true);

        when(orderStatusTool.getOrderStatus(context.orderId()))
                .thenReturn(Order.Status.PROCESSING);

        // When
        var result = workflowService.confirmCancellation(context.workflowId(), true);

        // Then
        assertEquals(WorkflowStatus.COMPLETED, result.workflowStatus());

        verify(stateService).transition(
                context.workflowId(),
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING);

        verify(orderStatusTool).getOrderStatus(context.orderId());
        verify(orderCancelationTool).cancelOrder(context.orderId());

        verify(stateService).updateFinalStatus(
                context.workflowId(),
                WorkflowStatus.COMPLETED);
    }

    @Test
    void shouldCancelWorkflowWhenCancellationIsRejected() {
        // Given
        var context = newContext(
                ORDER_ID,
                Order.Status.PROCESSING,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);

        var initialEntity = newEntity(context, WorkflowStatus.WAITING_FOR_CONFIRMATION);
        var cancelledEntity = newEntity(context, WorkflowStatus.CANCELLED);

        when(workflowRepository.findById(context.workflowId()))
                .thenReturn(Optional.of(initialEntity), Optional.of(cancelledEntity));

        when(stateService.transition(
                context.workflowId(),
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLED))
                .thenReturn(true);

        // When
        var result = workflowService.confirmCancellation(context.workflowId(), false);

        // Then
        assertEquals(WorkflowStatus.CANCELLED, result.workflowStatus());

        verify(stateService).transition(
                context.workflowId(),
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLED);

        verifyNoInteractions(orderStatusTool, orderCancelationTool);
    }

    @Test
    void shouldRejectStartingWorkflowWhenOrderIsNotCancellable() {
        when(orderStatusTool.getOrderStatus(ORDER_ID))
                .thenReturn(Order.Status.DELIVERED);

        assertThrows(
                IllegalStateException.class,
                () -> workflowService.startCancellation(ORDER_ID, SESSION_ID));

        verify(orderStatusTool).getOrderStatus(ORDER_ID);
        verifyNoInteractions(orderCancelationTool);
    }

    @Test
    void shouldFailWorkflowWhenOrderBecomesNotCancellableBeforeConfirmation() {
        // Given
        var context = newContext(
                ORDER_ID,
                Order.Status.DELIVERED,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);

        var initialEntity = newEntity(context, WorkflowStatus.WAITING_FOR_CONFIRMATION);

        when(workflowRepository.findById(context.workflowId()))
                .thenReturn(Optional.of(initialEntity));

        when(stateService.transition(
                context.workflowId(),
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING))
                .thenReturn(true);

        when(orderStatusTool.getOrderStatus(context.orderId()))
                .thenReturn(Order.Status.DELIVERED);

        // When
        assertThrows(
                IllegalStateException.class,
                () -> workflowService.confirmCancellation(context.workflowId(), true));

        // Then
        verify(stateService).transition(
                context.workflowId(),
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING);

        verify(orderStatusTool).getOrderStatus(context.orderId());

        verify(stateService).updateFinalStatus(
                context.workflowId(),
                WorkflowStatus.FAILED);

        verifyNoInteractions(orderCancelationTool);
    }

    @Test
    void shouldThrowWhenWorkflowDoesNotExist() {
        assertThrows(
                IllegalArgumentException.class,
                () -> workflowService.confirmCancellation("missing-workflow", true));

        verifyNoInteractions(
                orderStatusTool,
                orderCancelationTool,
                stateService);
    }

    @Test
    void shouldThrowWhenWorkflowWasAlreadyCompleted() {
        // Given
        var context = newContext(
                ORDER_ID,
                Order.Status.PROCESSING,
                WorkflowStatus.COMPLETED);

        when(workflowRepository.findById(context.workflowId()))
                .thenReturn(Optional.of(newEntity(context, WorkflowStatus.COMPLETED)));

        // When
        assertThrows(
                IllegalStateException.class,
                () -> workflowService.confirmCancellation(context.workflowId(), true));

        // Then
        verify(workflowRepository).findById(context.workflowId());

        verifyNoInteractions(
                orderStatusTool,
                orderCancelationTool,
                stateService);
    }

    @Test
    void shouldFailWorkflowWhenOrderCancellationThrows() {
        // Given
        var context = newContext(
                ORDER_ID,
                Order.Status.PROCESSING,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);

        var initialEntity = newEntity(context, WorkflowStatus.WAITING_FOR_CONFIRMATION);

        when(workflowRepository.findById(context.workflowId()))
                .thenReturn(Optional.of(initialEntity));

        when(stateService.transition(
                context.workflowId(),
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING))
                .thenReturn(true);

        when(orderStatusTool.getOrderStatus(context.orderId()))
                .thenReturn(Order.Status.PROCESSING);

        when(orderCancelationTool.cancelOrder(context.orderId()))
                .thenThrow(new RuntimeException("Cancellation failed"));

        // When
        assertThrows(
                IllegalStateException.class,
                () -> workflowService.confirmCancellation(context.workflowId(), true));

        // Then
        verify(stateService).transition(
                context.workflowId(),
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING);

        verify(orderStatusTool).getOrderStatus(context.orderId());
        verify(orderCancelationTool).cancelOrder(context.orderId());

        verify(stateService).updateFinalStatus(
                context.workflowId(),
                WorkflowStatus.FAILED);
    }

    @Test
    void shouldRejectConcurrentConfirmationWhenWorkflowIsAlreadyClaimed() {
        // Given
        var context = newContext(
                ORDER_ID,
                Order.Status.PROCESSING,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);

        when(workflowRepository.findById(context.workflowId()))
                .thenReturn(Optional.of(newEntity(context, WorkflowStatus.WAITING_FOR_CONFIRMATION)));

        when(stateService.transition(
                context.workflowId(),
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING))
                .thenReturn(false);

        // When
        assertThrows(
                IllegalStateException.class,
                () -> workflowService.confirmCancellation(context.workflowId(), true));

        // Then
        verify(stateService).transition(
                context.workflowId(),
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING);

        verifyNoInteractions(orderStatusTool, orderCancelationTool);
    }

    @Test
    void shouldNotCancelOrderTwiceForSameConfirmation() {
        // Given
        var context = newContext(
                ORDER_ID,
                Order.Status.PROCESSING,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);

        var initialEntity = newEntity(context, WorkflowStatus.WAITING_FOR_CONFIRMATION);
        var completedEntity = newEntity(context, WorkflowStatus.COMPLETED);

        when(workflowRepository.findById(context.workflowId()))
                .thenReturn(
                        Optional.of(initialEntity),
                        Optional.of(completedEntity),
                        Optional.of(completedEntity));

        when(stateService.transition(
                context.workflowId(),
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING))
                .thenReturn(true);

        when(orderStatusTool.getOrderStatus(context.orderId()))
                .thenReturn(Order.Status.PROCESSING);

        // When
        // first confirmation
        workflowService.confirmCancellation(context.workflowId(), true);
        // Second confirmation
        assertThrows(IllegalStateException.class,
                () -> workflowService.confirmCancellation(context.workflowId(), true));

        // Then
        verify(orderCancelationTool).cancelOrder(context.orderId());
        verify(stateService).transition(
                context.workflowId(),
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING);
        verify(orderStatusTool).getOrderStatus(context.orderId());
    }

    private CancellationWorkflowEntity newEntity(
            CancellationWorkflowContext context,
            WorkflowStatus workflowStatus) {

        return new CancellationWorkflowEntity(
                context.workflowId(),
                SESSION_ID,
                context.confirmationId(),
                context.orderId(),
                context.orderStatus(),
                workflowStatus);
    }

    private CancellationWorkflowContext newContext(
            String orderId,
            Order.Status orderStatus,
            WorkflowStatus workflowStatus) {

        return new CancellationWorkflowContext(
                "workflow-1",
                orderId,
                orderStatus,
                workflowStatus,
                "confirmation-1");
    }

}
