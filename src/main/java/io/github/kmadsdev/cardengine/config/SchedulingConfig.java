package io.github.kmadsdev.cardengine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The outbox publisher runs on a schedule unless {@code app.outbox.scheduling-enabled=false} (tests). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.outbox.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
