package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.WorkflowStatus;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderCancelationTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

@ExtendWith(MockitoExtension.class)
class OrderCancellationWorkflowServiceTest {

    private static final String ORDER_ID = "123";

    @Mock
    private OrderStatusTool orderStatusTool;
    @Mock
    private OrderCancelationTool orderCancelationTool;

    private OrderCancellationWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new OrderCancellationWorkflowService(orderStatusTool, orderCancelationTool);
    }

    @Test
    void shouldCompleteWorkflowWhenProcessingOrderCancellationIsConfirmed() {
        when(orderStatusTool.getOrderStatus(ORDER_ID)).thenReturn(Order.Status.PROCESSING);

        var context = workflowService.startCancellation(ORDER_ID);
        var result = workflowService.confirmCancellation(context.workflowId(), true);

        assertEquals(WorkflowStatus.COMPLETED, result.workflowStatus());
        verify(orderStatusTool, times(2)).getOrderStatus(ORDER_ID);
        verify(orderCancelationTool).cancelOrder(ORDER_ID);
    }

    @Test
    void shouldCancelWorkflowWhenCancellationIsRejected() {
        when(orderStatusTool.getOrderStatus(ORDER_ID)).thenReturn(Order.Status.PROCESSING);

        var context = workflowService.startCancellation(ORDER_ID);
        var result = workflowService.confirmCancellation(context.workflowId(), false);

        assertEquals(WorkflowStatus.CANCELLED, result.workflowStatus());
        verify(orderStatusTool).getOrderStatus(ORDER_ID);
        verifyNoInteractions(orderCancelationTool);
    }

    @Test
    void shouldRejectStartingWorkflowWhenOrderIsNotCancellable() {
        when(orderStatusTool.getOrderStatus(ORDER_ID)).thenReturn(Order.Status.DELIVERED);

        assertThrows(IllegalStateException.class, () -> workflowService.startCancellation(ORDER_ID));

        verify(orderStatusTool).getOrderStatus(ORDER_ID);
        verifyNoInteractions(orderCancelationTool);
    }

    @Test
    void shouldFailWorkflowWhenOrderBecomesNotCancellableBeforeConfirmation() {
        when(orderStatusTool.getOrderStatus(ORDER_ID))
                .thenReturn(Order.Status.PROCESSING, Order.Status.DELIVERED);

        var context = workflowService.startCancellation(ORDER_ID);

        assertThrows(
                IllegalStateException.class,
                () -> workflowService.confirmCancellation(context.workflowId(), true));

        assertEquals(WorkflowStatus.FAILED, context.workflowStatus());
        verify(orderStatusTool, times(2)).getOrderStatus(ORDER_ID);
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
        when(orderStatusTool.getOrderStatus(ORDER_ID)).thenReturn(Order.Status.PROCESSING);

        var context = workflowService.startCancellation(ORDER_ID);
        workflowService.confirmCancellation(context.workflowId(), true);

        assertThrows(
                IllegalStateException.class,
                () -> workflowService.confirmCancellation(context.workflowId(), true));

        assertEquals(WorkflowStatus.COMPLETED, context.workflowStatus());
        verify(orderStatusTool, times(2)).getOrderStatus(ORDER_ID);
        verify(orderCancelationTool).cancelOrder(ORDER_ID);
    }

    @Test
    void shouldFailWorkflowWhenOrderCancellationThrows() {
        when(orderStatusTool.getOrderStatus(ORDER_ID)).thenReturn(Order.Status.PROCESSING);
        when(orderCancelationTool.cancelOrder(ORDER_ID)).thenThrow(new RuntimeException("Cancellation failed"));

        var context = workflowService.startCancellation(ORDER_ID);

        assertThrows(
                IllegalStateException.class,
                () -> workflowService.confirmCancellation(context.workflowId(), true));

        assertEquals(WorkflowStatus.FAILED, context.workflowStatus());
        verify(orderStatusTool, times(2)).getOrderStatus(ORDER_ID);
        verify(orderCancelationTool).cancelOrder(ORDER_ID);
    }
}
