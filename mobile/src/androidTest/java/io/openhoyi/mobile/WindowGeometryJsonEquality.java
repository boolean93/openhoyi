package io.openhoyi.mobile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/** Exact fast path for Android's ordered JSONObject/JSONArray serialization equality. */
final class WindowGeometryJsonEquality {
    private WindowGeometryJsonEquality() {}

    static boolean same(JSONObject before, JSONObject after) {
        // Serialization can fail anywhere in either tree and return null for the entire object.
        // Prove both complete trees safe before comparing even their first fields.
        boolean beforeSafe = serializable(before);
        boolean afterSafe = serializable(after);
        if (!beforeSafe || !afterSafe) {
            return serializedEqual(before, after);
        }
        try {
            return sameValue(before, after);
        } catch (JSONException unexpected) {
            return serializedEqual(before, after);
        }
    }

    private static boolean serializedEqual(JSONObject before, JSONObject after) {
        String first = before.toString();
        String second = after.toString();
        return first == null ? second == null : first.equals(second);
    }

    private static boolean serializable(Object value) {
        if (value == null || value == JSONObject.NULL || value instanceof String || value instanceof Boolean) {
            return true;
        }
        if (value.getClass() == JSONObject.class) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key == null || !serializable(object.opt(key))) return false;
            }
            return true;
        }
        if (value.getClass() == JSONArray.class) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                if (!serializable(array.opt(i))) return false;
            }
            return true;
        }
        if (value.getClass() == Byte.class || value.getClass() == Short.class ||
            value.getClass() == Integer.class || value.getClass() == Long.class ||
            value.getClass() == Float.class || value.getClass() == Double.class) {
            try {
                JSONObject.numberToString((Number) value);
                return true;
            } catch (JSONException invalidNumber) {
                return false;
            }
        }
        return false;
    }

    private static boolean sameValue(Object left, Object right) throws JSONException {
        if (left == null || left == JSONObject.NULL) return right == null || right == JSONObject.NULL;
        if (right == null || right == JSONObject.NULL || left.getClass() != right.getClass() &&
            !(left instanceof Number && right instanceof Number)) return false;
        if (left.getClass() == JSONObject.class) {
            JSONObject a = (JSONObject) left;
            JSONObject b = (JSONObject) right;
            if (a.length() != b.length()) return false;
            Iterator<String> aKeys = a.keys(), bKeys = b.keys();
            while (aKeys.hasNext() && bKeys.hasNext()) {
                String aKey = aKeys.next(), bKey = bKeys.next();
                if (!aKey.equals(bKey) || !sameValue(a.opt(aKey), b.opt(bKey))) return false;
            }
            return !aKeys.hasNext() && !bKeys.hasNext();
        }
        if (left.getClass() == JSONArray.class) {
            JSONArray a = (JSONArray) left;
            JSONArray b = (JSONArray) right;
            if (a.length() != b.length()) return false;
            for (int i = 0; i < a.length(); i++) {
                if (!sameValue(a.opt(i), b.opt(i))) return false;
            }
            return true;
        }
        if (left instanceof Number) {
            // Android's numberToString, not numeric or Double equality, defines the old text.
            return JSONObject.numberToString((Number) left).equals(JSONObject.numberToString((Number) right));
        }
        return left.equals(right);
    }
}
