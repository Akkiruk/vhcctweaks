package com.vhcctweaks.detail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts one piece's Gear Score into its item details, from what Vault Hunters' scorer explained.
 * Kept free of the game so it can be tested on its own.
 *
 * The piece gets gearScoreStatus always, and gearScore, gearScoreExact and gearScoreLines when it counts.
 * With lines on, each modifier under implicits, prefixes and suffixes gets scoreRatio (1 is the best natural roll
 * at the reference level), scorePoints (its share of gearScore before the cap) and, for numeric lines with a known
 * range, naturalBest and naturalWorst.
 */
final class GearScoreShape {

    static final String COUNTED = "COUNTED";

    /** One modifier line. affix is IMPLICIT, PREFIX or SUFFIX; best and worst are null when there is no range. */
    record Line(String affix, double ratio, Double best, Double worst) {}

    /**
     * @param status        Vault Hunters' SlotStatus name: COUNTED, INERT, NO_TIER_CONFIG, ...
     * @param expectedLines the line count of a best natural piece of this type
     * @param perfect       the score of a perfect natural piece (gear_power.json perfectSlotScore)
     */
    record Piece(String status, double score, int expectedLines, double perfect, List<Line> lines) {}

    private static final Map<String, String> AFFIX_KEYS = Map.of(
            "IMPLICIT", "implicits", "PREFIX", "prefixes", "SUFFIX", "suffixes");

    private GearScoreShape() {}

    static void put(Map<String, Object> gear, Piece piece, boolean withLines) {
        if (piece == null || piece.status() == null) return;
        gear.put("gearScoreStatus", piece.status());
        if (!COUNTED.equals(piece.status()) || !Double.isFinite(piece.score())) return;

        List<Line> lines = piece.lines() == null ? List.of() : piece.lines();
        gear.put("gearScore", (int) Math.round(piece.score()));
        gear.put("gearScoreExact", piece.score());
        Map<String, Object> counts = new HashMap<>();
        counts.put("expected", piece.expectedLines());
        counts.put("actual", lines.size());
        gear.put("gearScoreLines", counts);

        if (!withLines) return;
        int divisor = Math.max(piece.expectedLines(), lines.size());
        Map<String, Integer> seen = new HashMap<>();
        for (Line line : lines) {
            String key = AFFIX_KEYS.get(line.affix());
            if (key == null) continue;
            int index = seen.merge(key, 1, Integer::sum) - 1;
            if (!(gear.get(key) instanceof List<?> modifiers) || index >= modifiers.size()) continue;
            if (!(modifiers.get(index) instanceof Map<?, ?> raw)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> modifier = (Map<String, Object>) raw;
            modifier.put("scoreRatio", line.ratio());
            if (divisor > 0) modifier.put("scorePoints", piece.perfect() * line.ratio() / divisor);
            if (line.best() != null && line.worst() != null) {
                modifier.put("naturalBest", line.best());
                modifier.put("naturalWorst", line.worst());
            }
        }
    }
}
