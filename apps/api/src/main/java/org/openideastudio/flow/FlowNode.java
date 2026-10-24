package org.openideastudio.flow;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param id              stable ID, unique within the flow; never regenerated
 * @param type            start | end | action | decision | subprocess
 * @param requirementKeys keys of requirement artifacts this step implements
 */
public record FlowNode(
        @NotBlank @Pattern(regexp = FlowNode.ID_PATTERN) String id,
        @NotBlank @Pattern(regexp = "start|end|action|decision|subprocess") String type,
        @NotBlank @Size(max = 120) String label,
        @Size(max = 1000) String description,
        @NotNull @Valid Position position,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> requirementKeys) {

    public static final String ID_PATTERN = "[a-z0-9][a-z0-9-]{0,79}";

    public record Position(double x, double y) {
    }

    public List<String> requirementKeysOrEmpty() {
        return requirementKeys == null ? List.of() : requirementKeys;
    }

    public FlowNode withPosition(Position p) {
        return new FlowNode(id, type, label, description, p, requirementKeys);
    }
}
