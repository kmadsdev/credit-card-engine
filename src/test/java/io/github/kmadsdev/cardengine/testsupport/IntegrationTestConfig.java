package io.github.kmadsdev.cardengine.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

/** Real PostgreSQL plus test doubles for the three ports and the clock. */
@TestConfiguration(proxyBeanMethods = false)
@Import({PostgresTestcontainer.class, Fixtures.class})
public class IntegrationTestConfig {

    public static final Instant START = Instant.parse("2026-06-01T12:00:00Z");

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(START);
    }

    @Bean
    @Primary
    TestCreditBureau testCreditBureau() {
        return new TestCreditBureau();
    }

    @Bean
    @Primary
    RecordingEventPublisher recordingEventPublisher() {
        return new RecordingEventPublisher();
    }
}
