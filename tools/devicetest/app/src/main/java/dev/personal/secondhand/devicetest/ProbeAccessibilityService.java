package dev.personal.secondhand.devicetest;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * The eyes of the device tests, in the test app only. While an accessibility service is enabled the
 * keyboard exposes its keys as accessibility nodes (MainKeyboardView.setKeyboard), and this service
 * lists the nodes of all windows, the keyboard window included, for the host script ({@link DumpProvider}).
 * It does not request touch exploration, so typing behaves as without it, and it never acts on a node.
 */
public class ProbeAccessibilityService extends AccessibilityService {
    private static final int MAX_NODES = 4000;

    static volatile ProbeAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    /** All nodes of all windows as JSON: {"nodes":[{"w":window type,"p":package,"c":class,"id":..,"d":..,"t":..,"b":[l,t,r,b]}]}. */
    String dump() throws JSONException {
        final JSONArray nodes = new JSONArray();
        final List<AccessibilityWindowInfo> windows = getWindows();
        for (AccessibilityWindowInfo window : windows) {
            final AccessibilityNodeInfo root = window.getRoot();
            if (root != null) add(nodes, root, window.getType(), 0);
        }
        return new JSONObject().put("windows", windows.size()).put("nodes", nodes).toString();
    }

    private void add(JSONArray nodes, AccessibilityNodeInfo node, int windowType, int depth) throws JSONException {
        if (nodes.length() >= MAX_NODES || depth > 60) return;
        final Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        nodes.put(new JSONObject()
                .put("w", windowType)
                .put("p", string(node.getPackageName()))
                .put("c", string(node.getClassName()))
                .put("id", string(node.getViewIdResourceName()))
                .put("d", string(node.getContentDescription()))
                .put("t", string(node.getText()))
                .put("b", new JSONArray().put(bounds.left).put(bounds.top).put(bounds.right).put(bounds.bottom)));
        final int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            final AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) add(nodes, child, windowType, depth + 1);
        }
    }

    private static String string(CharSequence text) {
        return text == null ? "" : text.toString();
    }
}
