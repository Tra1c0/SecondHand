package dev.personal.secondhand.devicetest;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * How the host script reads the screen: "adb shell content read --uri content://dev.personal.secondhand.devicetest.dump/now"
 * returns the nodes of all windows as JSON, taken at that moment by {@link ProbeAccessibilityService}.
 * Exported on purpose (the adb shell is another user); the app exists only in the test emulator.
 */
public class DumpProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String json;
        try {
            final ProbeAccessibilityService service = ProbeAccessibilityService.instance;
            json = service == null ? "{\"error\":\"the accessibility probe is not connected\"}" : service.dump();
        } catch (Exception e) {
            json = "{\"error\":\"" + e.getClass().getSimpleName() + "\"}";
        }
        final File file = new File(getContext().getCacheDir(), "dump.json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new FileNotFoundException("dump not written");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "application/json";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
