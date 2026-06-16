package topList;

import java.util.List;

final class LegacyColor {

    private LegacyColor() {
    }

    static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '&' && index + 1 < text.length() && isColorCode(text.charAt(index + 1))) {
                result.append('\u00A7');
                result.append(text.charAt(++index));
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    static List<String> colorize(List<String> lines) {
        return lines.stream().map(LegacyColor::colorize).toList();
    }

    private static boolean isColorCode(char value) {
        char lower = Character.toLowerCase(value);
        return (lower >= '0' && lower <= '9')
                || (lower >= 'a' && lower <= 'f')
                || lower == 'k'
                || lower == 'l'
                || lower == 'm'
                || lower == 'n'
                || lower == 'o'
                || lower == 'r';
    }
}
