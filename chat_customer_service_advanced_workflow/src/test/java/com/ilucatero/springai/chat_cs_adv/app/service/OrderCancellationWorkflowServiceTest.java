package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    private OrderCancellationWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new OrderCancellationWorkflowService(orderStatusTool, orderCancelationTool,
                workflowRepository);
    }

    @Test
    void shouldCompleteWorkflowWhenProcessingOrderCancellationIsConfirmed() {
        // Given
        var context = newContext(ORDER_ID, Order.Status.PROCESSING, WorkflowStatus.WAITING_FOR_CONFIRMATION);
        when(workflowRepository.findById(context.workflowId()))
                .thenReturn(Optional.of(new CancellationWorkflowEntity(
                        context.workflowId(),
                        SESSION_ID,
                        context.confirmationId(),
                        context.orderId(),
                        context.orderStatus(),
                        context.workflowStatus())));
        when(orderStatusTool.getOrderStatus(context.orderId())).thenReturn(context.orderStatus());

        // When
        var result = workflowService.confirmCancellation(context.workflowId(), true);

        // Then
        assertEquals(WorkflowStatus.COMPLETED, result.workflowStatus());
        verify(workflowRepository).findById(context.workflowId());
        verify(orderStatusTool).getOrderStatus(context.orderId());
        verify(orderCancelationTool).cancelOrder(context.orderId());
        verify(workflowRepository).save(any(CancellationWorkflowEntity.class));
        verifyNoMoreInteractions(workflowRepository, orderStatusTool, orderCancelationTool);
    }

    @Test
    void shouldCancelWorkflowWhenCancellationIsRejected() {
        // Given
        var context = newContext(ORDER_ID, Order.Status.PROCESSING, WorkflowStatus.WAITING_FOR_CONFIRMATION);
        when(workflowRepository.findById(context.workflowId()))
                .thenReturn(Optional.of(new CancellationWorkflowEntity(
                        context.workflowId(),
                        SESSION_ID,
                        context.confirmationId(),
                        context.orderId(),
                        context.orderStatus(),
                        context.workflowStatus())));

        // When
        var result = workflowService.confirmCancellation(context.workflowId(), false);
        // Then
        assertEquals(WorkflowStatus.CANCELLED, result.workflowStatus());
        verify(workflowRepository).save(any(CancellationWorkflowEntity.class));
        verifyNoMoreInteractions(workflowRepository);
        verifyNoInteractions(orderStatusTool, orderCancelationTool);
    }

    @Test
    void shouldRejectStartingWorkflowWhenOrderIsNotCancellable() {
        when(orderStatusTool.getOrderStatus(ORDER_ID)).thenReturn(Order.Status.DELIVERED);

        assertThrows(IllegalStateException.class, () -> workflowService.startCancellation(ORDER_ID, SESSION_ID));

        verify(orderStatusTool).getOrderStatus(ORDER_ID);
        verifyNoInteractions(orderCancelationTool);
    }

    @Test
    void shouldFailWorkflowWhenOrderBecomesNotCancellableBeforeConfirmation() {
        // Given
        var context = newContext(ORDER_ID, Order.Status.DELIVERED, WorkflowStatus.WAITING_FOR_CONFIRMATION);
        when(workflowRepository.findById(context.workflowId()))
                .thenReturn(Optional.of(new CancellationWorkflowEntity(
                        context.workflowId(),
                        SESSION_ID,
                        context.confirmationId(),
                        context.orderId(),
                        context.orderStatus(),
                        context.workflowStatus())));
        when(orderStatusTool.getOrderStatus(context.orderId())).thenReturn(context.orderStatus());

        // When
        assertThrows(
                IllegalStateException.class,
                () -> workflowService.confirmCancellation(context.workflowId(), true));

        // Then
        verify(workflowRepository).findById(context.workflowId());
        verify(orderStatusTool, times(1)).getOrderStatus(context.orderId());
        verify(workflowRepository).save(any(CancellationWorkflowEntity.class));
        verifyNoMoreInteractions(workflowRepository, orderStatusTool);
        verifyNoInteractions(orderCancelationTool);
    }

    @Test
    void shouldThrowWhenWorkflowDoesNotExist() {
        assertThrows(
                IllegalArgumentException.class,
                () -> workflowService.confirmCancellation("missing-workflow", true));

        verifyNoInteractions(orderStatusTool, orderCancelationTool);
    }

    @Test
    void shouldThrowWhenWorkflowWasAlreadyCompleted() {
        // Given
        var context = newContext(ORDER_ID, Order.Status.PROCESSING, WorkflowStatus.COMPLETED);
        when(workflowRepository.findById(context.workflowId()))
                .thenReturn(Optional.of(new CancellationWorkflowEntity(
                        context.workflowId(),
                        SESSION_ID,
                        context.confirmationId(),
                        context.orderId(),
                        context.orderStatus(),
                        context.workflowStatus())));

        // When
        assertThrows(
                IllegalStateException.class,
                () -> workflowService.confirmCancellation(context.workflowId(), true));

        // Then
        verify(workflowRepository).findById(context.workflowId());
        verifyNoMoreInteractions(workflowRepository);
        verifyNoInteractions(orderStatusTool, orderCancelationTool);
    }

    @Test
    void shouldFailWorkflowWhenOrderCancellationThrows() {
        // Given
        var context = newContext(ORDER_ID, Order.Status.PROCESSING, WorkflowStatus.WAITING_FOR_CONFIRMATION);
        when(workflowRepository.findById(context.workflowId()))
                .thenReturn(Optional.of(new CancellationWorkflowEntity(
                        context.workflowId(),
                        SESSION_ID,
                        context.confirmationId(),
                        context.orderId(),
                        context.orderStatus(),
                        context.workflowStatus())));
        when(orderStatusTool.getOrderStatus(context.orderId())).thenReturn(Order.Status.PROCESSING);
        when(orderCancelationTool.cancelOrder(context.orderId()))
                .thenThrow(new RuntimeException("Cancellation failed"));

        // When
        assertThrows(
                IllegalStateException.class,
                () -> workflowService.confirmCancellation(context.workflowId(), true));

        // Then
        verify(workflowRepository).findById(context.workflowId());
        verify(orderStatusTool).getOrderStatus(context.orderId());
        verify(orderCancelationTool).cancelOrder(context.orderId());
        verify(workflowRepository).save(any(CancellationWorkflowEntity.class));
        verifyNoMoreInteractions(workflowRepository, orderStatusTool, orderCancelationTool);
    }

    private CancellationWorkflowContext newContext(String orderId, Order.Status orderStatus,
            WorkflowStatus workflowStatus) {
        return new CancellationWorkflowContext(
                "workflow-1",
                orderId,
                orderStatus,
                workflowStatus,
                "confirmation-1");
    }
}
