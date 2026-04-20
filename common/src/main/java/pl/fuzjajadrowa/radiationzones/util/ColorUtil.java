package pl.fuzjajadrowa.radiationzones.util;

public final class ColorUtil {
    private ColorUtil() {
    }

    public static int parseHexColor(String input, int fallback) {
        if (input == null) {
            return fallback;
        }

        String normalized = input.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }

        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}