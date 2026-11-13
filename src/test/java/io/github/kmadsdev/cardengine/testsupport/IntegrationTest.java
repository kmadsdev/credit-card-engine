package io.github.kmadsdev.cardengine.testsupport;

import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Full application context (with the Prometheus registry on) against PostgreSQL in a container. One shared context for all ITs. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
public @interface IntegrationTest {
}
