package com.cooksync.app.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.cooksync.app.R;
import com.cooksync.app.ui.base.Navigator;

/**
 * Turns the "Terms of Use" and "Privacy Policy" mentions inside a piece of text into tappable,
 * bold accent-colored links opening {@link LegalDocumentActivity}. Shared by every screen that
 * needs this exact treatment — the sign-up screen's terms checkbox label and the Settings
 * screen's footer — rather than each screen re-implementing the same span logic.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 11/08/2026
 */
public final class LegalLinkSpanner {

    private LegalLinkSpanner() {
    }

    /**
     * Renders {@code fullTextRes} into {@code view} with its "Terms of Use" and "Privacy Policy"
     * substrings turned into clickable links. {@link LinkMovementMethod} intercepts touches that
     * land on a span, so tapping a link opens the document instead of triggering the view's own
     * click behavior (e.g. toggling a checkbox).
     *
     * Complexity:
     * Time: O(n) — n is the length of the resolved text
     * Space: O(n)
     *
     * @param view       the text view to render the linked text into
     * @param host       the hosting activity, used to resolve strings/colors and start
     *                   {@link LegalDocumentActivity}
     * @param fullTextRes the string resource containing both "Terms of Use" and "Privacy Policy"
     *                     as literal substrings
     */
    public static void apply(@NonNull TextView view, @NonNull Activity host, @StringRes int fullTextRes) {
        String fullText = host.getString(fullTextRes);
        SpannableString spannable = new SpannableString(fullText);

        linkify(spannable, fullText, host, host.getString(R.string.legal_terms_of_use_title),
                LegalDocumentActivity.DOCUMENT_TERMS_OF_USE);
        linkify(spannable, fullText, host, host.getString(R.string.legal_privacy_policy_title),
                LegalDocumentActivity.DOCUMENT_PRIVACY_POLICY);

        view.setText(spannable);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Styles the first occurrence of {@code linkText} within {@code fullText} as a bold,
     * accent-colored {@link ClickableSpan} that opens {@code documentType} on
     * {@link LegalDocumentActivity}. No-op if {@code linkText} isn't found.
     *
     * Complexity:
     * Time: O(n) — n is the length of {@code fullText}
     * Space: O(1)
     *
     * @param spannable    the spannable being built, mutated in place
     * @param fullText     the full text {@code spannable} wraps
     * @param host         the hosting activity to start {@link LegalDocumentActivity} from
     * @param linkText     the substring to turn into a link
     * @param documentType the document to open, one of {@link LegalDocumentActivity}'s
     *                     {@code DOCUMENT_*} constants
     */
    private static void linkify(SpannableString spannable, String fullText, Activity host,
                                 String linkText, String documentType) {
        int start = fullText.indexOf(linkText);
        if (start < 0) return;
        int end = start + linkText.length();

        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Intent intent = new Intent(host, LegalDocumentActivity.class);
                intent.putExtra(LegalDocumentActivity.EXTRA_DOCUMENT_TYPE, documentType);
                Navigator.start(host, intent);
            }
        }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(host, R.color.color_accent)),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
