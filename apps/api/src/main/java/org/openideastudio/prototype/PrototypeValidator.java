package org.openideastudio.prototype;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Semantic checks beyond field constraints. Errors block saving; warnings are
 * shown but allow a work-in-progress prototype.
 */
public final class PrototypeValidator {

    static final int MAX_DEPTH = 4;
    static final int MAX_COMPONENTS_PER_SCREEN = 80;

    private PrototypeValidator() {
    }

    public record Result(List<String> errors, List<String> warnings) {

        public boolean valid() {
            return errors.isEmpty();
        }
    }

    public static Result validate(PrototypeSpec spec, Set<String> requirementKeys) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Set<String> ids = new HashSet<>();
        for (PrototypeSpec.Screen s : spec.screens()) {
            if (!ids.add(s.id())) {
                errors.add("Duplicate screen id '" + s.id() + "'.");
            }
        }
        if (!ids.contains(spec.startScreen())) {
            errors.add("Start screen '" + spec.startScreen() + "' does not exist.");
        }

        Set<String> reachable = new HashSet<>(Set.of(spec.startScreen()));
        for (PrototypeSpec.Screen s : spec.screens()) {
            for (String key : s.requirementKeysOrEmpty()) {
                if (!requirementKeys.contains(key)) {
                    errors.add("Screen '" + s.id() + "' links to unknown requirement '" + key + "'.");
                }
            }
            AtomicInteger count = new AtomicInteger();
            PrototypeTree.walk(s.components(), 1, (c, depth) -> {
                count.incrementAndGet();
                if (depth > MAX_DEPTH) {
                    errors.add("Screen '" + s.id() + "' nests components deeper than " + MAX_DEPTH + " levels.");
                }
            });
            if (count.get() > MAX_COMPONENTS_PER_SCREEN) {
                errors.add("Screen '" + s.id() + "' has more than " + MAX_COMPONENTS_PER_SCREEN + " components.");
            }
            PrototypeTree.actions(s.components(), a -> {
                switch (a.type()) {
                    case "navigate" -> {
                        if (a.target() == null || !ids.contains(a.target())) {
                            errors.add("Screen '" + s.id() + "' navigates to unknown screen '" + a.target() + "'.");
                        } else {
                            reachable.add(a.target());
                        }
                    }
                    case "message" -> {
                        if (a.message() == null || a.message().isBlank()) {
                            errors.add("Screen '" + s.id() + "' has a message action without a message.");
                        }
                    }
                    case "setState" -> {
                        if (a.key() == null || a.value() == null) {
                            errors.add("Screen '" + s.id() + "' has a setState action without key and value.");
                        }
                        if (a.target() != null) {
                            if (!ids.contains(a.target())) {
                                errors.add("Screen '" + s.id() + "' navigates to unknown screen '" + a.target() + "'.");
                            } else {
                                reachable.add(a.target());
                            }
                        }
                    }
                    default -> errors.add("Unknown action type '" + a.type() + "'.");
                }
            });
        }
        for (PrototypeSpec.Screen s : spec.screens()) {
            if (!reachable.contains(s.id())) {
                warnings.add("Screen '" + s.title() + "' cannot be reached from any other screen.");
            }
        }
        return new Result(errors, warnings);
    }
}
