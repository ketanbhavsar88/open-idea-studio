package org.openideastudio.artifact;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Generates stable artifact keys on the backend. The model never invents keys,
 * so AI output cannot collide with or overwrite existing artifacts.
 */
public final class ArtifactKeys {

    private static final int MAX_SLUG = 40;

    private ArtifactKeys() {
    }

    public static String generate(ArtifactType type, String title, Set<String> taken) {
        if (type == ArtifactType.SUMMARY) {
            return type.prefix();
        }
        String slug = slug(title);
        String base = slug.isEmpty() ? type.prefix() + "-ITEM" : type.prefix() + "-" + slug;
        String key = base;
        for (int n = 2; taken.contains(key); n++) {
            key = base + "-" + n;
        }
        return key;
    }

    static String slug(String title) {
        String ascii = Normalizer.normalize(title, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String slug = ascii.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.length() > MAX_SLUG) {
            slug = slug.substring(0, MAX_SLUG);
            int cut = slug.lastIndexOf('-');
            slug = cut > 10 ? slug.substring(0, cut) : slug;
        }
        return slug;
    }
}
