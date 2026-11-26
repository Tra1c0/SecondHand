package dev.personal.secondhand.devicetest;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Stage 100: real touches for the drawn-symbol scenarios. The host script asks the test app (DumpProvider.call,
 * "adb shell content call … --method ink --arg <strokes>") to draw strokes on the screen through the accessibility
 * service's gesture API (dispatchGesture: the system injects real MotionEvents into the window under them, here the
 * keyboard; several strokes with real gaps, a tap as a stroke without movement). "input motionevent" starts a process per
 * event and is too slow for a stroke. The test app notes when the gesture was dispatched and finished and every change of
 * its fields with SystemClock.uptimeMillis (one clock for all processes), so the script can measure from the end of the
 * last stroke to the text in the field and see an early or a second change. Test app only; it holds fixed test symbols.
 *
 * Strokes: "start@duration@x1,y1,x2,y2,...;start@duration@..." (milliseconds from the start of the gesture, screen pixels).
 */
final class InkProbe {
    private static final Object LOCK = new Object();
    private static long dispatched = -1;
    private static long plannedEnd = -1;
    private static long completed = -1;
    private static boolean cancelled;
    private static String error = "";
    private static final ArrayList<Object[]> changes = new ArrayList<>();

    private InkProbe() {}

    /** A field of the test app changed (MainActivity); kept from the last dispatch on. */
    static void onFieldChanged(String field, CharSequence text) {
        synchronized (LOCK) {
            if (dispatched < 0 || changes.size() > 200) return;
            changes.add(new Object[]{SystemClock.uptimeMillis(), field, text.toString()});
        }
    }

    /** Parses [spec] and dispatches it; returns an error text or null. */
    static String draw(String spec) {
        final ProbeAccessibilityService service = ProbeAccessibilityService.instance;
        if (service == null) return "the accessibility probe is not connected";
        final GestureDescription.Builder builder = new GestureDescription.Builder();
        long end = 0;
        try {
            for (String stroke : spec.split(";")) {
                final String[] parts = stroke.split("@");
                final long start = Long.parseLong(parts[0]);
                final long duration = Math.max(1, Long.parseLong(parts[1]));
                final String[] xy = parts[2].split(",");
                final Path path = new Path();
                path.moveTo(Float.parseFloat(xy[0]), Float.parseFloat(xy[1]));
                for (int i = 2; i + 1 < xy.length; i += 2) path.lineTo(Float.parseFloat(xy[i]), Float.parseFloat(xy[i + 1]));
                builder.addStroke(new GestureDescription.StrokeDescription(path, start, duration));
                end = Math.max(end, start + duration);
            }
        } catch (RuntimeException e) {
            return "bad strokes: " + e.getClass().getSimpleName() + " " + e.getMessage();
        }
        final GestureDescription gesture = builder.build();
        final long total = end;
        final String[] result = {null};
        final Object done = new Object();
        // dispatch on the main thread of the service; the provider call comes on a binder thread
        new Handler(Looper.getMainLooper()).post(() -> {
            synchronized (LOCK) {
                changes.clear();
                dispatched = SystemClock.uptimeMillis();
                plannedEnd = dispatched + total;
                completed = -1;
                cancelled = false;
                error = "";
            }
            final boolean accepted = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription description) {
                    synchronized (LOCK) { completed = SystemClock.uptimeMillis(); }
                }

                @Override
                public void onCancelled(GestureDescription description) {
                    synchronized (LOCK) { completed = SystemClock.uptimeMillis(); cancelled = true; }
                }
            }, null);
            synchronized (done) {
                result[0] = accepted ? "" : "dispatchGesture refused";
                done.notifyAll();
            }
        });
        synchronized (done) {
            final long until = SystemClock.uptimeMillis() + 5000;
            while (result[0] == null && SystemClock.uptimeMillis() < until) {
                try { done.wait(200); } catch (InterruptedException e) { return "interrupted"; }
            }
        }
        if (result[0] == null) return "no answer from the main thread";
        return result[0].isEmpty() ? null : result[0];
    }

    /** What happened since the last dispatch, as JSON. */
    static String report() throws JSONException {
        synchronized (LOCK) {
            final JSONArray list = new JSONArray();
            for (Object[] change : changes) {
                list.put(new JSONObject().put("t", change[0]).put("f", change[1]).put("text", change[2]));
            }
            return new JSONObject().put("now", SystemClock.uptimeMillis()).put("dispatched", dispatched).put("plannedEnd", plannedEnd)
                    .put("completed", completed).put("cancelled", cancelled).put("error", error).put("changes", list).toString();
        }
    }
}
