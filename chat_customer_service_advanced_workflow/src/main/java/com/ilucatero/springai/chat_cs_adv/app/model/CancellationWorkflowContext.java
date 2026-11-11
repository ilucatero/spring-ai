package com.ilucatero.springai.chat_cs_adv.app.model;

public class CancellationWorkflowContext {

    private final String workflowId;
    private final String orderId;
    private final Order.Status orderStatus;
    private WorkflowStatus workflowStatus;

    public CancellationWorkflowContext(
            String workflowId,
            String orderId,
            Order.Status orderStatus,
            WorkflowStatus workflowStatus) {

        this.workflowId = workflowId;
        this.orderId = orderId;
        this.orderStatus = orderStatus;
        this.workflowStatus = workflowStatus;
    }

    public String workflowId() {
        return workflowId;
    }

    public String orderId() {
        return orderId;
    }

    public Order.Status orderStatus() {
        return orderStatus;
    }

    public WorkflowStatus workflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(WorkflowStatus workflowStatus) {
        this.workflowStatus = workflowStatus;
    }
}
