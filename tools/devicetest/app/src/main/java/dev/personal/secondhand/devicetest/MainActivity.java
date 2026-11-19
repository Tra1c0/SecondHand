package dev.personal.secondhand.devicetest;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Four input fields (text, password, e-mail, number). Under them one mirror line per field shows the
 * field's real content, which is what the host script (devicetest.py) compares. The extra "scenario"
 * of the start intent is shown as a headline so the recording says which scenario is running.
 */
public class MainActivity extends Activity {
    /** What the three copy buttons put on the clipboard; devicetest.py knows the same texts. */
    static final String[] CLIP_TEXTS = {"Aus der Zwischenablage", "Noch ein kopierter Text", "Der dritte kopierte Text"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        showScenario(getIntent());
        final int[] buttons = {R.id.copy_1, R.id.copy_2, R.id.copy_3};
        for (int i = 0; i < buttons.length; i++) {
            final String text = CLIP_TEXTS[i];
            findViewById(buttons[i]).setOnClickListener(view ->
                    ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                            .setPrimaryClip(ClipData.newPlainText("device test", text)));
        }
        mirror(R.id.field_text, R.id.mirror_text);
        mirror(R.id.field_password, R.id.mirror_password);
        mirror(R.id.field_email, R.id.mirror_email);
        mirror(R.id.field_number, R.id.mirror_number);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        showScenario(intent);
    }

    private void showScenario(Intent intent) {
        final String name = intent == null ? null : intent.getStringExtra("scenario");
        ((TextView) findViewById(R.id.scenario)).setText(name == null ? "" : name);
    }

    private void mirror(int fieldId, int mirrorId) {
        final EditText field = findViewById(fieldId);
        final TextView mirror = findViewById(mirrorId);
        final String name = getResources().getResourceEntryName(fieldId).substring("field_".length());
        final TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // brackets make leading and trailing spaces visible
                mirror.setText(name + "=[" + s + "]");
            }
        };
        field.addTextChangedListener(watcher);
        watcher.afterTextChanged(field.getText());
    }
}
