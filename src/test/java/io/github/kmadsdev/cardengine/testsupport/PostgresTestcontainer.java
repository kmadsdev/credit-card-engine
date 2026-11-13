package io.github.kmadsdev.cardengine.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real PostgreSQL for integration tests. {@link ServiceConnection} points the
 * datasource at it. The container is a bean, so it lives as long as the cached
 * Spring context and every IT class shares it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    public static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(IMAGE);
    }
}
