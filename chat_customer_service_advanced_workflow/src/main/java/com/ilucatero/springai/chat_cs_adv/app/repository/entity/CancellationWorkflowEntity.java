package com.ilucatero.springai.chat_cs_adv.app.repository.entity;

import java.time.LocalDateTime;

import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.WorkflowStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "cancellation_workflows", indexes = {
        @Index(name = "idx_confirmation_id", columnList = "confirmationId"),
})
public class CancellationWorkflowEntity {

    @Id
    private String workflowId;

    @Version
    private Long version;

    private String sessionId;

    @Column(unique = true)
    private String confirmationId;

    private String orderId;

    @Enumerated(EnumType.STRING)
    private Order.Status orderStatus;

    @Enumerated(EnumType.STRING)
    private WorkflowStatus workflowStatus;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected CancellationWorkflowEntity() {
    }

    // Constructeur, getters et setters
    public CancellationWorkflowEntity(String workflowId, String sessionId, String confirmationId, String orderId,
            Order.Status orderStatus, WorkflowStatus workflowStatus) {
        this.workflowId = workflowId;
        this.sessionId = sessionId;
        this.confirmationId = confirmationId;
        this.orderId = orderId;
        this.orderStatus = orderStatus;
        this.workflowStatus = workflowStatus;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public Long getVersion() {
        return version;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getConfirmationId() {
        return confirmationId;
    }

    public String getOrderId() {
        return orderId;
    }

    public Order.Status getOrderStatus() {
        return orderStatus;
    }

    public CancellationWorkflowEntity setOrderStatus(Order.Status orderStatus) {
        this.orderStatus = orderStatus;
        this.updatedAt = LocalDateTime.now();
        return this;
    }

    public WorkflowStatus getWorkflowStatus() {
        return workflowStatus;
    }

    public CancellationWorkflowEntity setWorkflowStatus(WorkflowStatus workflowStatus) {
        this.workflowStatus = workflowStatus;
        this.updatedAt = LocalDateTime.now();
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
