package de.pinkhorizon.generators.data;

/**
 * Repräsentiert einen gespeicherten (noch nicht aktivierten) Booster.
 * Wird serialisiert als "multiplier:durationMinutes", z.B. "2.0:60".
 */
public record StoredBooster(double multiplier, int durationMinutes) {

    /** Serialisiert diesen Booster als "2.0:60" */
    public String serialize() {
        return multiplier + ":" + durationMinutes;
    }

    /** Deserialisiert aus "2.0:60" */
    public static StoredBooster deserialize(String s) {
        String[] parts = s.split(":");
        return new StoredBooster(Double.parseDouble(parts[0]), Integer.parseInt(parts[1]));
    }

    /** Anzeigename im GUI */
    public String displayName() {
        return "x" + multiplier + " für " + durationMinutes + " Min.";
    }
}
