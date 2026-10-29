package com.virditech.ac7000.evaluation;

import org.json.JSONException;
import org.json.JSONObject;

/** Operator-supplied conditions shared by every trial in one recording session. */
public final class EvaluationConditions {
    public final int groundTruthIndex;
    public final String subjectId;
    public final String attackMediumId;
    public final int sector;
    public final double distanceCm;
    public final String lighting;

    public EvaluationConditions(int groundTruthIndex, String subjectId, String attackMediumId,
                                int sector, double distanceCm, String lighting) {
        if (groundTruthIndex < 0 || groundTruthIndex >= 12) {
            throw new IllegalArgumentException("Select a ground-truth class");
        }
        subjectId = text(subjectId, "Subject ID", true);
        attackMediumId = text(attackMediumId, "Attack medium ID",
                groundTruthIndex != 0 && groundTruthIndex != 10 && groundTruthIndex != 11);
        lighting = text(lighting, "Lighting", true);
        if (sector < 1 || sector > 9) throw new IllegalArgumentException("Sector must be 1 to 9");
        if (!Double.isFinite(distanceCm) || distanceCm <= 0) {
            throw new IllegalArgumentException("Distance must be a positive number in cm");
        }
        this.groundTruthIndex = groundTruthIndex;
        this.subjectId = subjectId;
        this.attackMediumId = attackMediumId;
        this.sector = sector;
        this.distanceCm = distanceCm;
        this.lighting = lighting;
    }

    private static String text(String value, String name, boolean required) {
        String text = value == null ? "" : value.trim();
        if ((required && text.isEmpty()) || text.length() > 80
                || text.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must be 1 to 80 characters without control characters");
        }
        return text;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("ground_truth_index", groundTruthIndex)
                .put("subject_id", subjectId).put("attack_medium_id",
                        attackMediumId.isEmpty() ? JSONObject.NULL : attackMediumId)
                .put("sector", sector).put("distance_cm", distanceCm).put("lighting", lighting);
    }
}
