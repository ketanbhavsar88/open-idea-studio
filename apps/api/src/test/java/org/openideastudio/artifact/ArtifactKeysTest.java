package org.openideastudio.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ArtifactKeysTest {

    @Test
    void buildsReadableKeysFromTitles() {
        assertThat(ArtifactKeys.generate(ArtifactType.REQUIREMENT, "Owners approve each booking", Set.of()))
                .isEqualTo("REQ-OWNERS-APPROVE-EACH-BOOKING");
        assertThat(ArtifactKeys.generate(ArtifactType.QUESTION, "Who can list a vehicle?", Set.of()))
                .isEqualTo("Q-WHO-CAN-LIST-A-VEHICLE");
    }

    @Test
    void avoidsCollisions() {
        assertThat(ArtifactKeys.generate(ArtifactType.RISK, "Damage", Set.of("RISK-DAMAGE", "RISK-DAMAGE-2")))
                .isEqualTo("RISK-DAMAGE-3");
    }

    @Test
    void truncatesLongTitlesAtWordBoundaries() {
        String key = ArtifactKeys.generate(ArtifactType.DECISION,
                "Renters must verify their driving licence before their first booking request", Set.of());
        assertThat(key).startsWith("DEC-RENTERS-MUST-VERIFY").hasSizeLessThanOrEqualTo(44).doesNotEndWith("-");
    }

    @Test
    void handlesTitlesWithoutLetters() {
        assertThat(ArtifactKeys.generate(ArtifactType.ASSUMPTION, "???", Set.of())).isEqualTo("ASM-ITEM");
    }
}
