package io.github.kmadsdev.cardengine.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import io.github.kmadsdev.cardengine.domain.Luhn;
import io.github.kmadsdev.cardengine.testsupport.TestCpfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.random.RandomGenerator;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the real logback-spring.xml and pushes events through its encoder. Proves
 * that a CPF or a card number that reaches a log call by mistake is masked in the
 * JSON line: in the message, in a structured argument and in a stack trace.
 */
class LogMaskingTest {

    private LoggerContext context;
    private OutputStreamAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void loadProductionConfig() throws Exception {
        context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
        logger = context.getLogger("masking-test");
        appender = (OutputStreamAppender<ILoggingEvent>) context.getLogger(Logger.ROOT_LOGGER_NAME)
                .getAppender("JSON_CONSOLE");
        assertThat(appender).as("appender from logback-spring.xml").isNotNull();
    }

    @AfterEach
    void stop() {
        context.stop();
    }

    private String encode(String message, Object[] args, Throwable error) {
        LoggingEvent event = new LoggingEvent(Logger.class.getName(), logger, Level.INFO, message, error, args);
        return new String(appender.getEncoder().encode(event), StandardCharsets.UTF_8);
    }

    @Test
    void cpfInMessageIsMaskedInBothFormats() {
        String cpf = TestCpfs.random();
        String line = encode("applicant {} and {}", new Object[]{cpf, TestCpfs.formatted(cpf)}, null);

        assertThat(line).doesNotContain(cpf).doesNotContain(TestCpfs.formatted(cpf))
                .contains("***.***.***-**");
        assertThat(line).startsWith("{").contains("\"message\"");
    }

    @Test
    void cardNumberIsMaskedInMessageArgumentsAndStackTrace() {
        String pan = Luhn.generateTestPan(RandomGenerator.getDefault());
        String spaced = pan.replaceAll("(\\d{4})(?=\\d)", "$1 ");
        String cpf = TestCpfs.random();
        String line = encode("card {} {}", new Object[]{pan, kv("detail", Map.of("pan", spaced, "cpf", cpf))},
                new IllegalStateException("failed for " + pan + " / " + cpf));

        assertThat(line).doesNotContain(pan).doesNotContain(spaced).doesNotContain(cpf);
        assertThat(line).contains("****").contains("stack_trace");
    }

    @Test
    void ordinaryNumbersAndMaskedCpfAreLeftAlone() {
        String line = encode("limit {} score {} cpf {} id {}",
                new Object[]{"8000.01", 760, "***.***.***-09", "3f2a9c1e-0b7d-4c55-9a51-2c0d6f7e8a90"}, null);
        assertThat(line).contains("8000.01").contains("760").contains("***.***.***-09")
                .contains("3f2a9c1e-0b7d-4c55-9a51-2c0d6f7e8a90");
    }
}
