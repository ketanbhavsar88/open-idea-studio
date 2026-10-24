package org.openideastudio.prototype;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * The closed set of prototype components (design doc §12). Each is plain data
 * rendered by trusted frontend components; text is always rendered as text.
 * Text fields may contain {@code {state.key}} placeholders.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(Component.Header.class),
        @JsonSubTypes.Type(Component.Text.class),
        @JsonSubTypes.Type(Component.Alert.class),
        @JsonSubTypes.Type(Component.Badge.class),
        @JsonSubTypes.Type(Component.Button.class),
        @JsonSubTypes.Type(Component.Card.class),
        @JsonSubTypes.Type(Component.ListView.class),
        @JsonSubTypes.Type(Component.Table.class),
        @JsonSubTypes.Type(Component.Form.class),
        @JsonSubTypes.Type(Component.Tabs.class),
        @JsonSubTypes.Type(Component.Dialog.class),
        @JsonSubTypes.Type(Component.Image.class),
        @JsonSubTypes.Type(Component.Conditional.class)
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface Component {

    String TONES = "neutral|info|success|warning|danger";

    /** Child components, for traversal; empty for leaves. */
    default List<Component> children() {
        return List.of();
    }

    @JsonTypeName("header")
    record Header(@NotBlank @Size(max = 120) String text, @Size(max = 300) String subtitle) implements Component {
    }

    @JsonTypeName("text")
    record Text(@NotBlank @Size(max = 1000) String text, @Pattern(regexp = "body|caption|heading") String variant)
            implements Component {
    }

    @JsonTypeName("alert")
    record Alert(@NotBlank @Size(max = 500) String text, @Pattern(regexp = TONES) String tone) implements Component {
    }

    @JsonTypeName("badge")
    record Badge(@NotBlank @Size(max = 60) String text, @Pattern(regexp = TONES) String tone) implements Component {
    }

    @JsonTypeName("button")
    record Button(@NotBlank @Size(max = 60) String label,
                  @Pattern(regexp = "primary|secondary|danger") String variant,
                  @NotNull @Valid PrototypeAction action) implements Component {
    }

    @JsonTypeName("card")
    record Card(@Size(max = 120) String title, @Size(max = 1000) String body,
                @Size(max = 20) List<@Valid @NotNull Component> children) implements Component {
        @Override
        public List<Component> children() {
            return children == null ? List.of() : children;
        }
    }

    @JsonTypeName("list")
    record ListView(@NotNull @Size(min = 1, max = 30) List<@Valid @NotNull Item> items) implements Component {

        public record Item(@NotBlank @Size(max = 120) String title, @Size(max = 300) String subtitle,
                           @Size(max = 60) String meta, @Size(max = 40) String badge,
                           @Valid PrototypeAction action) {
        }
    }

    @JsonTypeName("table")
    record Table(@NotNull @Size(min = 1, max = 8) List<@NotBlank @Size(max = 60) String> columns,
                 @NotNull @Size(max = 30) List<@NotNull @Size(max = 8) List<@NotNull @Size(max = 200) String>> rows)
            implements Component {
    }

    @JsonTypeName("form")
    record Form(@NotNull @Size(min = 1, max = 15) List<@Valid @NotNull Field> fields,
                @NotBlank @Size(max = 60) String submitLabel,
                @NotNull @Valid PrototypeAction action) implements Component {

        public record Field(
                @NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_]{0,39}") String name,
                @NotBlank @Size(max = 80) String label,
                @NotBlank @Pattern(regexp = "text|number|date|email|select|textarea|checkbox") String inputType,
                @Size(max = 20) List<@NotBlank @Size(max = 80) String> options,
                Boolean required,
                @Size(max = 120) String placeholder) {
        }
    }

    @JsonTypeName("tabs")
    record Tabs(@NotNull @Size(min = 1, max = 6) List<@Valid @NotNull Tab> tabs) implements Component {

        public record Tab(@NotBlank @Size(max = 40) String label,
                          @NotNull @Size(max = 20) List<@Valid @NotNull Component> children) {
        }

        @Override
        public List<Component> children() {
            return tabs.stream().flatMap(t -> t.children().stream()).toList();
        }
    }

    @JsonTypeName("dialog")
    record Dialog(@NotBlank @Size(max = 60) String triggerLabel, @NotBlank @Size(max = 120) String title,
                  @Size(max = 1000) String body, @NotBlank @Size(max = 60) String confirmLabel,
                  @NotNull @Valid PrototypeAction action) implements Component {
    }

    /** A grey placeholder; there are never real image URLs. */
    @JsonTypeName("image")
    record Image(@NotBlank @Size(max = 120) String alt) implements Component {
    }

    /** Shows its children only while state {@code key} equals {@code equals}. */
    @JsonTypeName("conditional")
    record Conditional(@NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_]{0,39}") String key,
                       @NotNull @Size(max = 100) String equals,
                       @NotNull @Size(max = 20) List<@Valid @NotNull Component> children) implements Component {
    }
}
