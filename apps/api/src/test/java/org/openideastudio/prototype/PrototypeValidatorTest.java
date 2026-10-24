package org.openideastudio.prototype;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PrototypeValidatorTest {

    static PrototypeAction go(String target) {
        return new PrototypeAction("navigate", target, null, null, null);
    }

    static PrototypeSpec.Screen screen(String id, String title, Component... components) {
        return new PrototypeSpec.Screen(id, title, null, List.of(), List.of(components));
    }

    static PrototypeSpec spec(PrototypeSpec.Screen... screens) {
        return new PrototypeSpec("Demo", screens[0].id(), Map.of(), List.of(screens), 1);
    }

    @Test
    void acceptsAConnectedPrototype() {
        PrototypeSpec ok = spec(
                screen("home", "Home", new Component.Button("Open", "primary", go("detail"))),
                screen("detail", "Detail", new Component.Card("Card", null, List.of(
                        new Component.Button("Back", "secondary", go("home"))))));
        PrototypeValidator.Result result = PrototypeValidator.validate(ok, Set.of());
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void rejectsBrokenNavigationAndUnknownRequirements() {
        PrototypeSpec bad = new PrototypeSpec("Demo", "missing", Map.of(), List.of(
                new PrototypeSpec.Screen("home", "Home", null, List.of("REQ-NOPE"), List.of(
                        new Component.Tabs(List.of(new Component.Tabs.Tab("A", List.of(
                                new Component.Button("Go", null, go("nowhere"))))))))), 1);
        assertThat(PrototypeValidator.validate(bad, Set.of("REQ-OK")).errors()).containsExactlyInAnyOrder(
                "Start screen 'missing' does not exist.",
                "Screen 'home' links to unknown requirement 'REQ-NOPE'.",
                "Screen 'home' navigates to unknown screen 'nowhere'.");
    }

    @Test
    void warnsAboutUnreachableScreensAndLimitsNesting() {
        Component deep = new Component.Text("deep", null);
        for (int i = 0; i < 4; i++) {
            deep = new Component.Card(null, null, List.of(deep));
        }
        PrototypeSpec spec = spec(screen("home", "Home", deep), screen("orphan", "Orphan", new Component.Text("x", null)));
        PrototypeValidator.Result result = PrototypeValidator.validate(spec, Set.of());
        assertThat(result.errors()).anyMatch(e -> e.contains("deeper than"));
        assertThat(result.warnings()).containsExactly("Screen 'Orphan' cannot be reached from any other screen.");
    }

    @Test
    void stabilizeKeepsExistingIdsByTitleAndRewritesNavigation() {
        PrototypeSpec existing = spec(screen("listing-detail", "Vehicle details", new Component.Text("x", null)));
        PrototypeSpec raw = spec(
                screen("s1", "Home", new Component.ListView(List.of(
                        new Component.ListView.Item("Car", null, null, null, go("s2"))))),
                screen("s2", "Vehicle Details", new Component.Button("Back", null, go("s1"))));

        PrototypeSpec stable = PrototypeService.stabilizeIds(raw, existing);

        assertThat(stable.screens()).extracting(PrototypeSpec.Screen::id).containsExactly("s1", "listing-detail");
        Component.ListView list = (Component.ListView) stable.screens().getFirst().components().getFirst();
        assertThat(list.items().getFirst().action().target()).isEqualTo("listing-detail");
        assertThat(PrototypeValidator.validate(stable, Set.of()).valid()).isTrue();
    }
}
