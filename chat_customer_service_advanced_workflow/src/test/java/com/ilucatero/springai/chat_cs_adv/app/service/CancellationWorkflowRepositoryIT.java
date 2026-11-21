package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.WorkflowStatus;
import com.ilucatero.springai.chat_cs_adv.app.repository.CancellationWorkflowRepository;
import com.ilucatero.springai.chat_cs_adv.app.repository.entity.CancellationWorkflowEntity;

import jakarta.persistence.EntityManager;

@DataJpaTest
class CancellationWorkflowRepositoryIT {

    @Autowired
    private CancellationWorkflowRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldRejectUpdateWhenVersionIsStale() {

        var entity = new CancellationWorkflowEntity(
                "workflow-1",
                "session123",
                "confirm-1",
                "order-1",
                Order.Status.PROCESSING,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);

        repository.saveAndFlush(entity);

        // First snapshot
        var first = repository.findById("workflow-1").orElseThrow();
        entityManager.detach(first);

        // Second snapshot, independently loaded
        var second = repository.findById("workflow-1").orElseThrow();
        entityManager.detach(second);

        assertNotSame(first, second);
        assertEquals(first.getVersion(), second.getVersion());

        // First update succeeds
        first.setWorkflowStatus(WorkflowStatus.COMPLETED);
        repository.saveAndFlush(first);

        // Second update uses the old version
        second.setWorkflowStatus(WorkflowStatus.CANCELLED);

        assertThrows(
                ObjectOptimisticLockingFailureException.class,
                () -> repository.saveAndFlush(second));
    }

    @Test
    void shouldTransitionWorkflowWhenExpectedStatusMatches() {

        var entity = new CancellationWorkflowEntity(
                "workflow-1",
                "session123",
                "confirm-1",
                "order-1",
                Order.Status.PROCESSING,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);

        repository.saveAndFlush(entity);

        var initialVersion = entity.getVersion();

        // When
        int updated = repository.transitionStatus(
                "workflow-1",
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING,
                LocalDateTime.now());

        // Then
        assertEquals(1, updated);

        entityManager.clear();

        var updatedEntity = repository.findById("workflow-1").orElseThrow();

        assertEquals(WorkflowStatus.CANCELLING, updatedEntity.getWorkflowStatus());
        assertEquals(initialVersion + 1, updatedEntity.getVersion());
    }

    @Test
    void shouldRejectTransitionWhenExpectedStatusDoesNotMatch() {

        var entity = new CancellationWorkflowEntity(
                "workflow-1",
                "session123",
                "confirm-1",
                "order-1",
                Order.Status.PROCESSING,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);

        repository.saveAndFlush(entity);

        var updatedAt = LocalDateTime.now();

        // First transition succeeds
        int firstUpdate = repository.transitionStatus(
                "workflow-1",
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING,
                updatedAt);

        assertEquals(1, firstUpdate);

        // Second transition must fail
        int secondUpdate = repository.transitionStatus(
                "workflow-1",
                WorkflowStatus.WAITING_FOR_CONFIRMATION,
                WorkflowStatus.CANCELLING,
                LocalDateTime.now());

        assertEquals(0, secondUpdate);

        entityManager.clear();

        var updatedEntity = repository.findById("workflow-1").orElseThrow();

        assertEquals(WorkflowStatus.CANCELLING, updatedEntity.getWorkflowStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldAllowOnlyOneConcurrentTransition() throws Exception {
        // Given
        var entity = new CancellationWorkflowEntity(
                "workflow-1",
                "session123",
                "confirm-1",
                "order-1",
                Order.Status.PROCESSING,
                WorkflowStatus.WAITING_FOR_CONFIRMATION);

        repository.saveAndFlush(entity);

        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);

        try {
            // When
            Callable<Integer> transition = () -> {
                start.await();

                return transactionTemplate.execute(status -> repository.transitionStatus(
                        "workflow-1",
                        WorkflowStatus.WAITING_FOR_CONFIRMATION,
                        WorkflowStatus.CANCELLING,
                        LocalDateTime.now()));
            };

            var first = executor.submit(transition);
            var second = executor.submit(transition);

            start.countDown();
            var results = List.of(first.get(), second.get());

            // Then
            assertEquals(1, results.stream().filter(value -> value == 1).count());
            assertEquals(1, results.stream().filter(value -> value == 0).count());

            var finalEntity = repository.findById("workflow-1").orElseThrow();

            assertEquals(WorkflowStatus.CANCELLING, finalEntity.getWorkflowStatus());

        } finally {
            executor.shutdownNow();
        }
    }
}
