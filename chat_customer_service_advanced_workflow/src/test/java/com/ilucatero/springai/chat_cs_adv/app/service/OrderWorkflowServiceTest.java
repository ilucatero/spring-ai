package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderDeliveranceTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

@ExtendWith(MockitoExtension.class)
class OrderWorkflowServiceTest {

    @Mock
    private OrderStatusTool orderStatusTool;

    @Mock
    private OrderDeliveranceTool orderDeliveranceTool;

    @InjectMocks
    private OrderWorkflowService orderWorkflowService;

    @ParameterizedTest(name = "{0}: delivery call expected = {1}")
    @MethodSource("orderStatusesAndDeliveryCalls")
    void shouldCallToolsAccordingToOrderStatus(Order.Status status, boolean shouldCallDelivery) {
        // Given
        var orderId = "123";

        when(orderStatusTool.getOrderStatus(orderId))
                .thenReturn(status);
        if (shouldCallDelivery) {
            when(orderDeliveranceTool.getDeliveryDetails(orderId))
                    .thenReturn("Delivery expected on 15/06/2024");
        }

        // When
        var result = orderWorkflowService.execute(orderId);

        // Then
        assertEquals(orderId, result.orderId());
        assertEquals(status, result.orderStatus());
        if (shouldCallDelivery) {
            assertEquals("Delivery expected on 15/06/2024", result.deliveryDetails());
            var inOrder = inOrder(orderStatusTool, orderDeliveranceTool);
            inOrder.verify(orderStatusTool).getOrderStatus(orderId);
            inOrder.verify(orderDeliveranceTool).getDeliveryDetails(orderId);
        } else {
            assertNull(result.deliveryDetails());
            verify(orderStatusTool).getOrderStatus(orderId);
            verifyNoInteractions(orderDeliveranceTool);
        }

        verifyNoMoreInteractions(orderStatusTool, orderDeliveranceTool);
    }

    static Stream<Arguments> orderStatusesAndDeliveryCalls() {
        return Stream.of(
                Arguments.of(Order.Status.SHIPPED, true),
                Arguments.of(Order.Status.PROCESSING, false),
                Arguments.of(Order.Status.READY_TO_SHIP, false),
                Arguments.of(Order.Status.DELIVERING, false),
                Arguments.of(Order.Status.DELIVERED, false),
                Arguments.of(Order.Status.CANCELLED, false),
                Arguments.of(Order.Status.ORDER_NOT_FOUND, false));
    }

    @Test
    void shouldStopWorkflowWhenOrderStatusFails() {
        // Given
        var orderId = "123";

        when(orderStatusTool.getOrderStatus(orderId))
                .thenThrow(new RuntimeException("Order service unavailable"));

        // When / Then
        assertThrows(
                RuntimeException.class,
                () -> orderWorkflowService.execute(orderId));

        // Le deuxième outil ne doit pas être appelé
        verify(orderStatusTool).getOrderStatus(orderId);
        verifyNoInteractions(orderDeliveranceTool);
    }
}
