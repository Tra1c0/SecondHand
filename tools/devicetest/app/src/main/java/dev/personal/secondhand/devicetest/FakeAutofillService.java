package dev.personal.secondhand.devicetest;

import android.app.PendingIntent;
import android.app.assist.AssistStructure;
import android.content.Intent;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.InlinePresentation;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.RemoteViews;
import android.widget.inline.InlinePresentationSpec;

import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.v1.InlineSuggestionUi;

import java.util.List;

/**
 * Test-only autofill service for the inline-suggestion scenario (stage 73c). It offers one fixed,
 * obviously fake login for the fields of MainActivity that carry the autofill hints "username" and
 * "password", as an inline suggestion for the keyboard's suggestion strip. No real data, no network,
 * nothing is logged, nothing is saved. It is selected only by devicetest.py, only for that scenario.
 */
public class FakeAutofillService extends AutofillService {
    static final String FAKE_USER = "testnutzer@example.invalid";
    static final String FAKE_PASSWORD = "Testpasswort-73c";
    static final String CHIP_TITLE = "Test 73c";

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal, FillCallback callback) {
        final List<FillContext> contexts = request.getFillContexts();
        final AssistStructure structure = contexts.get(contexts.size() - 1).getStructure();
        final AutofillId[] ids = new AutofillId[2]; // user name, password
        for (int i = 0; i < structure.getWindowNodeCount(); i++) {
            find(structure.getWindowNodeAt(i).getRootViewNode(), ids);
        }
        if (ids[0] == null && ids[1] == null) {
            callback.onSuccess(null);
            return;
        }
        final InlineSuggestionsRequest inline = request.getInlineSuggestionsRequest();
        final InlinePresentation presentation = inline == null ? null : chip(inline);

        final Dataset.Builder dataset = new Dataset.Builder(listItem());
        if (ids[0] != null) {
            dataset.setValue(ids[0], AutofillValue.forText(FAKE_USER));
        }
        if (ids[1] != null) {
            dataset.setValue(ids[1], AutofillValue.forText(FAKE_PASSWORD));
        }
        if (presentation != null) {
            dataset.setInlinePresentation(presentation);
        }
        callback.onSuccess(new FillResponse.Builder().addDataset(dataset.build()).build());
    }

    @Override
    public void onSaveRequest(SaveRequest request, SaveCallback callback) {
        callback.onSuccess();
    }

    private RemoteViews listItem() {
        final RemoteViews views = new RemoteViews(getPackageName(), android.R.layout.simple_list_item_1);
        views.setTextViewText(android.R.id.text1, CHIP_TITLE);
        return views;
    }

    private InlinePresentation chip(InlineSuggestionsRequest request) {
        final List<InlinePresentationSpec> specs = request.getInlinePresentationSpecs();
        if (specs.isEmpty()) return null;
        final InlinePresentationSpec spec = specs.get(0);
        if (!UiVersions.getVersions(spec.getStyle()).contains(UiVersions.INLINE_UI_VERSION_1)) return null;
        final PendingIntent attribution = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new InlinePresentation(
                InlineSuggestionUi.newContentBuilder(attribution).setTitle(CHIP_TITLE).build().getSlice(),
                spec, false);
    }

    private static void find(AssistStructure.ViewNode node, AutofillId[] ids) {
        final String[] hints = node.getAutofillHints();
        if (hints != null && node.getAutofillId() != null) {
            for (String hint : hints) {
                if (View.AUTOFILL_HINT_USERNAME.equals(hint)) ids[0] = node.getAutofillId();
                if (View.AUTOFILL_HINT_PASSWORD.equals(hint)) ids[1] = node.getAutofillId();
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            find(node.getChildAt(i), ids);
        }
    }
}
