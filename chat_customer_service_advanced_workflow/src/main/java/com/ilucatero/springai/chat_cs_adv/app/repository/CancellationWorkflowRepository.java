package com.ilucatero.springai.chat_cs_adv.app.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ilucatero.springai.chat_cs_adv.app.model.WorkflowStatus;
import com.ilucatero.springai.chat_cs_adv.app.repository.entity.CancellationWorkflowEntity;

public interface CancellationWorkflowRepository extends JpaRepository<CancellationWorkflowEntity, String> {

    Optional<CancellationWorkflowEntity> findByConfirmationId(String confirmationId);

    Optional<CancellationWorkflowEntity> findBySessionId(String sessionId);

    Optional<CancellationWorkflowEntity> findBySessionIdAndConfirmationId(String sessionId, String confirmationId);

    List<CancellationWorkflowEntity> findAllBySessionIdAndWorkflowStatus(String sessionId,
            WorkflowStatus workflowStatus);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
                UPDATE CancellationWorkflowEntity w
                SET w.workflowStatus = :newStatus,
                    w.updatedAt = :updatedAt,
                    w.version = w.version + 1
                WHERE w.workflowId = :workflowId
                  AND w.workflowStatus = :expectedStatus
            """)
    int transitionStatus(
            @Param("workflowId") String workflowId,
            @Param("expectedStatus") WorkflowStatus expectedStatus,
            @Param("newStatus") WorkflowStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt);
}
