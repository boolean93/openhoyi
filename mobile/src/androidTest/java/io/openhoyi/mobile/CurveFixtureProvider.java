package io.openhoyi.mobile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;

/** Test APK only. Runs outside instrumentation, so must not depend on target Kotlin classes. */
public final class CurveFixtureProvider extends ContentProvider {
    public static final String AUTHORITY = "io.openhoyi.mobile.mock.test.curve-fixtures";
    public static final String PERMISSION = "io.openhoyi.mobile.mock.test.permission.CURVE_FIXTURE";
    public static final int MAX_FIXTURE_BYTES = 64 * 1024;
    private static final List<String> NAMES = Arrays.asList("output.json", "valid.json", "malformed.json", "oversize.json");
    private final Map<String, Integer> reads = new HashMap<>(), writes = new HashMap<>();
    public static Uri uri(String session, String name) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("fixtures").appendPath(session).appendPath(name).build();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    @Override public boolean onCreate() { return "io.openhoyi.mobile.mock.test".equals(getContext().getPackageName()); }
    private void requireCaller() {
        PackageManager manager = getContext().getPackageManager();
        require(manager.checkSignatures(Binder.getCallingUid(), getContext().getApplicationInfo().uid) == PackageManager.SIGNATURE_MATCH, "Fixture caller signature mismatch");
        String[] packages = manager.getPackagesForUid(Binder.getCallingUid());
        boolean allowed = false;
        if (packages != null) for (String name : packages) {
            if ("io.openhoyi.mobile.mock".equals(name) || "io.openhoyi.mobile.mock.test".equals(name)) allowed = true;
        }
        require(allowed, "Only the isolated Mock or test package may use fixtures");
    }
    private String session(String value) {
        require(value != null && UUID.fromString(value).toString().equals(value), "Invalid fixture UUID");
        return value;
    }
    private File directory(String id, boolean registered) {
        File folder = new File(new File(getContext().getFilesDir(), "curve-contract-fixtures"), session(id));
        if (registered) require(new File(folder, ".registered").isFile(), "Unknown fixture session");
        return folder;
    }
    private File file(Uri uri) {
        require("content".equals(uri.getScheme()) && AUTHORITY.equals(uri.getAuthority()) && uri.getQuery() == null && uri.getFragment() == null, "Invalid fixture URI");
        List<String> parts = uri.getPathSegments();
        require(parts.size() == 3 && "fixtures".equals(parts.get(0)) && NAMES.contains(parts.get(2)), "Unknown fixture path");
        return new File(directory(parts.get(1), true), parts.get(2));
    }
    private static void removeOwned(File file) throws IOException {
        // Do not follow links or traverse anything outside this registered fixture folder.
        if (Files.isSymbolicLink(file.toPath())) { Files.delete(file.toPath()); return; }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot enumerate owned fixture session");
            for (File child : children) removeOwned(child);
        }
        Files.delete(file.toPath());
    }
    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        requireCaller();
        String id = session(arg);
        Bundle result = new Bundle();
        try {
            switch (method) {
                case "create": {
                    String json = extras == null ? null : extras.getString("document");
                    require(json != null, "Missing fixture document");
                    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                    require(bytes.length <= MAX_FIXTURE_BYTES, "Fixture document too large");
                    require("openhoyi.local-curve".equals(new JSONObject(json).getString("kind")), "Invalid fixture document kind");
                    File folder = directory(id, false);
                    require(!folder.exists(), "Fixture session already exists");
                    require(folder.mkdirs(), "Cannot create fixture session");
                    Files.write(new File(folder, "valid.json").toPath(), bytes);
                    Files.write(new File(folder, "malformed.json").toPath(), new byte[]{(byte) 0xC3, 0x28});
                    byte[] oversize = new byte[MAX_FIXTURE_BYTES + 1];
                    Arrays.fill(oversize, (byte) 'x');
                    Files.write(new File(folder, "oversize.json").toPath(), oversize);
                    Files.write(new File(folder, ".registered").toPath(), "curve-contract-v1".getBytes(StandardCharsets.UTF_8));
                    result.putString("session", id);
                    return result;
                }
                case "stats": {
                    directory(id, true);
                    for (String name : NAMES) {
                        result.putInt("read:" + name, reads.getOrDefault(id + "/" + name, 0));
                        result.putInt("write:" + name, writes.getOrDefault(id + "/" + name, 0));
                    }
                    return result;
                }
                case "cleanup": {
                    removeOwned(directory(id, true));
                    for (String name : NAMES) { reads.remove(id + "/" + name); writes.remove(id + "/" + name); }
                    return result;
                }
                default: throw new IllegalArgumentException("Unknown fixture operation");
            }
        } catch (IOException | org.json.JSONException error) { throw new IllegalStateException("Fixture operation failed", error); }
    }
    @Override public synchronized ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        requireCaller();
        File target = file(uri);
        String key = uri.getPathSegments().get(1) + "/" + uri.getLastPathSegment();
        int flags;
        if ("r".equals(mode)) {
            if (!target.isFile()) throw new FileNotFoundException("Fixture not written");
            reads.put(key, reads.getOrDefault(key, 0) + 1);
            flags = ParcelFileDescriptor.MODE_READ_ONLY;
        } else if ("w".equals(mode) || "wt".equals(mode)) {
            require("output.json".equals(uri.getLastPathSegment()), "Input fixtures are read-only");
            writes.put(key, writes.getOrDefault(key, 0) + 1);
            flags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
        } else throw new FileNotFoundException("Unsupported fixture mode");
        return ParcelFileDescriptor.open(target, flags);
    }
    @Override public String getType(Uri uri) { requireCaller(); file(uri); return "application/json"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        requireCaller();
        require(selection == null && selectionArgs == null && sortOrder == null, "Unsupported fixture query");
        File target = file(uri);
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            require(OpenableColumns.DISPLAY_NAME.equals(columns[i]) || OpenableColumns.SIZE.equals(columns[i]), "Unsupported fixture column");
            values[i] = OpenableColumns.DISPLAY_NAME.equals(columns[i]) ? target.getName() : target.length();
        }
        MatrixCursor cursor = new MatrixCursor(columns); cursor.addRow(values); return cursor;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Fixture insertion is unavailable"); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException("Fixture deletion is unavailable"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException("Fixture update is unavailable"); }
}
