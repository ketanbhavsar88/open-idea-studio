package org.openideastudio.flow;

import java.util.Locale;
import java.util.Set;

/** Generates readable, stable node and edge IDs on the backend; the model never chooses them. */
public final class FlowIds {

    private FlowIds() {
    }

    public static String node(String label, Set<String> taken) {
        String slug = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-[^-]*$", "");
        }
        return unique(slug.isEmpty() ? "node" : "node-" + slug, taken);
    }

    public static String edge(String source, String target, Set<String> taken) {
        return unique(("e-" + source + "-" + target).replace("node-", ""), taken);
    }

    private static String unique(String base, Set<String> taken) {
        base = base.length() > 70 ? base.substring(0, 70) : base;
        String id = base;
        for (int n = 2; taken.contains(id); n++) {
            id = base + "-" + n;
        }
        return id;
    }
}
