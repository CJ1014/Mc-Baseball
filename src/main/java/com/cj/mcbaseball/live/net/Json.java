package com.cj.mcbaseball.live.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import javax.annotation.Nullable;

/**
 * Null-safe, exception-free accessors for provider JSON. Feeds routinely omit fields, send null,
 * or change a number to a string; every accessor here returns a fallback instead of throwing.
 */
public final class Json {

    private Json() {
    }

    /** Parses a document whose root must be an object. */
    public static JsonObject parseObject(String body) throws LiveDataException {
        if (body == null || body.isBlank()) {
            throw new LiveDataException(LiveDataException.Kind.INVALID_RESPONSE, "empty response body");
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (RuntimeException e) {
            throw new LiveDataException(LiveDataException.Kind.INVALID_RESPONSE, "invalid JSON: " + e.getClass().getSimpleName(), e);
        }
        if (root == null || !root.isJsonObject()) {
            throw new LiveDataException(LiveDataException.Kind.INVALID_RESPONSE, "JSON root is not an object");
        }
        return root.getAsJsonObject();
    }

    /** Walks nested objects: obj(root, "a", "b") == root.a.b, or null if any step is missing / not an object. */
    @Nullable
    public static JsonObject obj(@Nullable JsonObject o, String... path) {
        JsonObject cur = o;
        for (String key : path) {
            if (cur == null) {
                return null;
            }
            JsonElement e = cur.get(key);
            cur = e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
        }
        return cur;
    }

    @Nullable
    public static JsonArray arr(@Nullable JsonObject o, String key) {
        if (o == null) {
            return null;
        }
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    @Nullable
    public static JsonObject objAt(@Nullable JsonArray a, int i) {
        if (a == null || i < 0 || i >= a.size()) {
            return null;
        }
        JsonElement e = a.get(i);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    @Nullable
    private static JsonPrimitive prim(@Nullable JsonObject o, String key) {
        if (o == null) {
            return null;
        }
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsJsonPrimitive() : null;
    }

    public static String str(@Nullable JsonObject o, String key) {
        return str(o, key, "");
    }

    public static String str(@Nullable JsonObject o, String key, String fallback) {
        JsonPrimitive p = prim(o, key);
        return p == null ? fallback : p.getAsString();
    }

    public static int integer(@Nullable JsonObject o, String key, int fallback) {
        JsonPrimitive p = prim(o, key);
        if (p == null) {
            return fallback;
        }
        try {
            if (p.isNumber()) {
                double d = p.getAsDouble();
                return Double.isFinite(d) ? (int) d : fallback;
            }
            if (p.isString()) {
                String s = p.getAsString().trim();
                return s.isEmpty() ? fallback : (int) Double.parseDouble(s);
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    public static long lng(@Nullable JsonObject o, String key, long fallback) {
        JsonPrimitive p = prim(o, key);
        if (p == null) {
            return fallback;
        }
        try {
            if (p.isNumber()) {
                return p.getAsLong();
            }
            if (p.isString()) {
                String s = p.getAsString().trim();
                return s.isEmpty() ? fallback : Long.parseLong(s);
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    public static double dbl(@Nullable JsonObject o, String key, double fallback) {
        JsonPrimitive p = prim(o, key);
        if (p == null) {
            return fallback;
        }
        try {
            double d = p.isNumber() ? p.getAsDouble() : Double.parseDouble(p.getAsString().trim());
            return Double.isFinite(d) ? d : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static boolean bool(@Nullable JsonObject o, String key, boolean fallback) {
        JsonPrimitive p = prim(o, key);
        if (p == null) {
            return fallback;
        }
        if (p.isBoolean()) {
            return p.getAsBoolean();
        }
        if (p.isString()) {
            String s = p.getAsString().trim();
            if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("Y")) {
                return true;
            }
            if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("N")) {
                return false;
            }
        }
        return fallback;
    }
}
