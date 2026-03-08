package mini_git.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight JSON parsing utilities.
 *
 * <p>Provides minimal indexOf-based extraction of values from raw JSON strings.
 * No external JSON library is required.
 */
public class Json {

    /**
     * Extracts the first string value associated with {@code key} from a raw JSON string.
     *
     * <p>Performs a simple indexOf-based search; works for flat JSON objects
     * where the value is a quoted string.
     *
     * @param json the raw JSON string
     * @param key  the JSON key to look up
     * @return the value associated with the key
     * @throws RuntimeException if the key is not found
     */
    public static String getString(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            throw new RuntimeException("Key \"" + key + "\" not found in JSON response");
        }
        int colonIndex = json.indexOf(":", keyIndex + searchKey.length());
        int start = json.indexOf("\"", colonIndex + 1) + 1;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    /**
     * Extracts a string value from a nested JSON object.
     *
     * <p>First locates {@code outerKey}, then searches for {@code innerKey}
     * within the remaining JSON text starting from that position.
     *
     * @param json     the raw JSON string
     * @param outerKey the key of the enclosing object
     * @param innerKey the key inside the nested object
     * @return the nested value
     * @throws RuntimeException if either key is not found
     */
    public static String getNestedString(String json, String outerKey, String innerKey) {
        String searchKey = "\"" + outerKey + "\"";
        int outerIndex = json.indexOf(searchKey);
        if (outerIndex < 0) {
            throw new RuntimeException("Key \"" + outerKey + "\" not found in JSON response");
        }
        String remaining = json.substring(outerIndex);
        return getString(remaining, innerKey);
    }

    /**
     * Escapes special characters in a string so it can be safely embedded
     * inside a JSON quoted value.
     *
     * @param value the raw string
     * @return the escaped string
     */
    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /**
     * Extracts all objects from a JSON array field, returning the raw JSON of each element.
     *
     * <p>For example, given {@code {"tree":[{...},{...}]}},
     * {@code getObjectArray(json, "tree")} returns a list of {@code "{...}"} strings.
     *
     * @param json the raw JSON string
     * @param key  the key whose value is a JSON array of objects
     * @return a list of raw JSON strings, one per array element
     */
    public static List<String> getObjectArray(String json, String key) {
        List<String> items = new ArrayList<>();
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) return items;
        int bracketStart = json.indexOf("[", keyIndex);
        if (bracketStart < 0) return items;

        int depth = 0;
        int itemStart = -1;
        for (int i = bracketStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 1) itemStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 1 && itemStart >= 0) {
                    items.add(json.substring(itemStart, i + 1));
                    itemStart = -1;
                }
            } else if (c == '[') {
                if (depth == 0) depth = 1;
                else depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) break;
            }
        }
        return items;
    }
}
