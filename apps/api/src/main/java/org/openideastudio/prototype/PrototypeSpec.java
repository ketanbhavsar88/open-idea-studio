package org.openideastudio.prototype;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A complete, validated prototype: screens built from {@link Component}s plus simulated state. */
public record PrototypeSpec(
        @NotBlank @Size(max = 120) String title,
        @NotBlank String startScreen,
        @Size(max = 20) Map<@Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_]{0,39}") String, @Size(max = 100) String> initialState,
        @NotNull @Size(min = 1, max = PrototypeSpec.MAX_SCREENS) List<@Valid @NotNull Screen> screens,
        Integer schemaVersion) {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_SCREENS = 12;

    /**
     * @param id              stable screen ID, kept across regenerations
     * @param requirementKeys requirements this screen implements
     */
    public record Screen(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,79}") String id,
            @NotBlank @Size(max = 80) String title,
            @Size(max = 500) String description,
            @Size(max = 20) List<@NotBlank @Size(max = 100) String> requirementKeys,
            @NotNull @Size(min = 1, max = 30) List<@Valid @NotNull Component> components) {

        public List<String> requirementKeysOrEmpty() {
            return requirementKeys == null ? List.of() : requirementKeys;
        }
    }
}
