package io.github.kmadsdev.cardengine.testsupport;

import io.github.kmadsdev.cardengine.application.port.CreditBureau;

import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test double for the bureau port. Tests pick the score per CPF. An optional barrier
 * makes concurrent callers wait for each other at this point of the flow, which the
 * upgrade race test uses to force both requests to read the account first.
 */
public class TestCreditBureau implements CreditBureau {

    public static final int DEFAULT_SCORE = 700;

    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile CyclicBarrier barrier;

    public void setScore(String cpf, int score) {
        scores.put(cpf, score);
    }

    public void holdCallersAt(CyclicBarrier barrier) {
        this.barrier = barrier;
    }

    public int calls() {
        return calls.get();
    }

    @Override
    public int scoreFor(String cpf) {
        calls.incrementAndGet();
        CyclicBarrier b = barrier;
        if (b != null) {
            try {
                b.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException("barrier", e);
            }
        }
        return scores.getOrDefault(cpf, DEFAULT_SCORE);
    }
}
