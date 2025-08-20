// Minimal JSON parser for extracting deviceId, coordinates, and rssi from the publisher's message
// Only for use in this project, not a general-purpose JSON parser

public class SimpleJsonParser {
    public static String getString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    public static double getDouble(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return Double.NaN;
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
        return Double.parseDouble(json.substring(start, end));
    }

    public static double getCoord(String json, String axis) {
        String coordsPattern = "\"coordinates\":{";
        int coordsStart = json.indexOf(coordsPattern);
        if (coordsStart == -1) return Double.NaN;
        coordsStart += coordsPattern.length();
        String axisPattern = "\"" + axis + "\":";
        int axisStart = json.indexOf(axisPattern, coordsStart);
        if (axisStart == -1) return Double.NaN;
        axisStart += axisPattern.length();
        int end = axisStart;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
        return Double.parseDouble(json.substring(axisStart, end));
    }
}
