package org.openideastudio.prototype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What a control does when used. Every action is simulated: there are no
 * network calls, URLs or scripts.
 *
 * <ul>
 *   <li>navigate: go to {@code target} screen</li>
 *   <li>message: show {@code message} as a "simulated" notice</li>
 *   <li>setState: set prototype state {@code key} to {@code value} (then optionally navigate to {@code target})</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PrototypeAction(
        @NotBlank @Pattern(regexp = "navigate|message|setState") String type,
        @Size(max = 80) String target,
        @Size(max = 300) String message,
        @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_]{0,39}") String key,
        @Size(max = 100) String value) {

    PrototypeAction withTarget(String newTarget) {
        return new PrototypeAction(type, newTarget, message, key, value);
    }
}
