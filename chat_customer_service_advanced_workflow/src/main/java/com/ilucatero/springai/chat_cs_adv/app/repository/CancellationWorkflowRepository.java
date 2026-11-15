package com.ilucatero.springai.chat_cs_adv.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ilucatero.springai.chat_cs_adv.app.model.WorkflowStatus;
import com.ilucatero.springai.chat_cs_adv.app.repository.entity.CancellationWorkflowEntity;

public interface CancellationWorkflowRepository extends JpaRepository<CancellationWorkflowEntity, String> {

    Optional<CancellationWorkflowEntity> findByConfirmationId(String confirmationId);

    Optional<CancellationWorkflowEntity> findBySessionId(String sessionId);

    Optional<CancellationWorkflowEntity> findBySessionIdAndConfirmationId(String sessionId, String confirmationId);

    List<CancellationWorkflowEntity> findAllBySessionIdAndWorkflowStatus(String sessionId,
            WorkflowStatus workflowStatus);
}
