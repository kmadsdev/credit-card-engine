package io.github.kmadsdev.cardengine.application;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs an action only when the current transaction commits. Metrics and "decided" log
 * lines use it, so a decision that rolls back (a lost race) is neither counted nor
 * logged as made.
 */
final class AfterCommit {

    private AfterCommit() {
    }

    static void run(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
