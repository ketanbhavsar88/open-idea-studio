package org.openideastudio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class ModelGatewayTest {

    record Answer(@NotBlank String text) {
    }

    FakeProvider fake;
    ModelGateway gateway;

    @BeforeEach
    void setUp() {
        fake = new FakeProvider();
        AiProperties properties = new AiProperties("fake", 5, 2, 0, null, null, Map.of());
        gateway = new ModelGateway(fake, properties,
                Validation.buildDefaultValidatorFactory().getValidator(), JsonMapper.builder().build());
    }

    @Test
    void stripsCodeFencesAndProse() {
        fake.enqueue("Sure! ```json\n{\"text\": \"hello\"}\n```");
        assertThat(gateway.generate("op", "task", List.of(ModelMessage.user("hi")), Answer.class).text())
                .isEqualTo("hello");
    }

    @Test
    void retriesWithErrorsUntilOutputIsValid() {
        fake.enqueue("not json at all");
        fake.enqueue("{\"text\": \"\"}");
        fake.enqueue("{\"text\": \"fixed\"}");
        assertThat(gateway.generate("op", "task", List.of(ModelMessage.user("hi")), Answer.class).text())
                .isEqualTo("fixed");
    }

    @Test
    void rejectsUnknownFields() {
        fake.enqueue("{\"text\": \"a\", \"status\": \"confirmed\"}");
        fake.enqueue("{\"text\": \"a\", \"status\": \"confirmed\"}");
        fake.enqueue("{\"text\": \"a\", \"status\": \"confirmed\"}");
        assertThatThrownBy(() -> gateway.generate("op", "task", List.of(ModelMessage.user("hi")), Answer.class))
                .isInstanceOf(InvalidModelOutputException.class);
    }

    @Test
    void propagatesProviderFailuresWithoutRetrying() {
        fake.enqueueFailure(new ModelUnavailableException(ModelUnavailableException.Reason.RATE_LIMITED, "quota"));
        fake.enqueue("{\"text\": \"never used\"}");
        assertThatThrownBy(() -> gateway.generate("op", "task", List.of(ModelMessage.user("hi")), Answer.class))
                .isInstanceOfSatisfying(ModelUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(ModelUnavailableException.Reason.RATE_LIMITED));
    }
}
