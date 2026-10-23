package io.openhoyi.mobile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

/** Android 34 JSONObject.toString(2) bytes, without a whole-tree StringBuilder. */
final class WindowProofStreamingWriter {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int BUFFER_CHARS = 8192;

    interface OutputFactory { OutputStream open(File temporary) throws IOException; }
    interface Publisher { void publish(File temporary, File canonical) throws IOException; }

    private WindowProofStreamingWriter() {}

    static void write(JSONObject proof, File canonical) throws IOException, JSONException {
        write(proof, canonical, file -> Files.newOutputStream(file.toPath()),
            (temporary, destination) -> Files.move(temporary.toPath(), destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    static void writeStrict(JSONObject proof, File canonical) throws IOException, JSONException {
        writeStrict(proof, canonical, file -> Files.newOutputStream(file.toPath()),
            (temporary, destination) -> Files.move(temporary.toPath(), destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    // Fault seams are used only by the offline oracle. The production call uses the overload above.
    static void write(JSONObject proof, File canonical, OutputFactory output, Publisher publish)
        throws IOException, JSONException {
        writeInternal(proof, canonical, output, publish, false);
    }

    static void writeStrict(JSONObject proof, File canonical, OutputFactory output, Publisher publish)
        throws IOException, JSONException {
        writeInternal(proof, canonical, output, publish, true);
    }

    private static void writeInternal(JSONObject proof, File canonical, OutputFactory output,
                                      Publisher publish, boolean strict) throws IOException, JSONException {
        boolean streamable = streamable(proof);
        if (strict && !streamable) throw new JSONException("Unsupported strict JSON value tree");
        File destination = canonical.getAbsoluteFile();
        File parent = destination.getParentFile();
        File temporary = File.createTempFile(destination.getName() + ".owned-", ".tmp", parent);
        Throwable first = null;
        try {
            try (Writer writer = new BufferedWriter(
                    new OutputStreamWriter(output.open(temporary), StandardCharsets.UTF_8), BUFFER_CHARS)) {
                if (streamable) writeValue(proof, writer, 0);
                else writer.write(proof.toString(2)); // Whole-tree compatibility fallback, before publishing.
            }
            publish.publish(temporary, destination);
        } catch (IOException | JSONException | RuntimeException | Error failure) {
            first = failure;
            throw failure;
        } finally {
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException cleanupFailure) {
                if (first != null) first.addSuppressed(cleanupFailure);
                else throw cleanupFailure;
            }
        }
    }

    private static boolean streamable(Object value) {
        if (value == null || value == JSONObject.NULL || value instanceof String || value instanceof Boolean) return true;
        if (value.getClass() == JSONObject.class) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key == null || !streamable(object.opt(key))) return false;
            }
            return true;
        }
        if (value.getClass() == JSONArray.class) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) if (!streamable(array.opt(i))) return false;
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

    private static void writeValue(Object value, Writer writer, int depth) throws IOException, JSONException {
        if (value == null || value == JSONObject.NULL) writer.write("null");
        else if (value.getClass() == JSONObject.class) writeObject((JSONObject) value, writer, depth);
        else if (value.getClass() == JSONArray.class) writeArray((JSONArray) value, writer, depth);
        else if (value instanceof String) writeString((String) value, writer);
        else if (value instanceof Boolean) writer.write(value.toString());
        else writer.write(JSONObject.numberToString((Number) value));
    }

    private static void writeObject(JSONObject object, Writer writer, int depth) throws IOException, JSONException {
        writer.write('{');
        Iterator<String> keys = object.keys();
        boolean hadValue = false;
        while (keys.hasNext()) {
            String key = keys.next();
            if (hadValue) writer.write(',');
            indent(writer, depth + 1);
            writeString(key, writer);
            writer.write(": ");
            writeValue(object.opt(key), writer, depth + 1);
            hadValue = true;
        }
        if (hadValue) indent(writer, depth);
        writer.write('}');
    }

    private static void writeArray(JSONArray array, Writer writer, int depth) throws IOException, JSONException {
        writer.write('[');
        for (int i = 0; i < array.length(); i++) {
            if (i > 0) writer.write(',');
            indent(writer, depth + 1);
            writeValue(array.opt(i), writer, depth + 1);
        }
        if (array.length() > 0) indent(writer, depth);
        writer.write(']');
    }

    private static void indent(Writer writer, int depth) throws IOException {
        writer.write('\n');
        for (int i = 0; i < depth; i++) writer.write("  ");
    }

    private static void writeString(String text, Writer writer) throws IOException {
        writer.write('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': case '\\': case '/': writer.write('\\'); writer.write(c); break;
                case '\t': writer.write("\\t"); break;
                case '\b': writer.write("\\b"); break;
                case '\n': writer.write("\\n"); break;
                case '\r': writer.write("\\r"); break;
                case '\f': writer.write("\\f"); break;
                default:
                    if (c <= 0x1f) {
                        writer.write("\\u00");
                        writer.write(HEX[(c >> 4) & 15]);
                        writer.write(HEX[c & 15]);
                    } else writer.write(c);
            }
        }
        writer.write('"');
    }
}
