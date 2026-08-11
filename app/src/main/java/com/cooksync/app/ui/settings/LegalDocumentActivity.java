package com.cooksync.app.ui.settings;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.text.HtmlCompat;

import com.cooksync.app.R;
import com.cooksync.app.ui.base.BaseActivity;

/**
 * Displays a static legal document — either the Terms of Use or the Privacy Policy — as a
 * scrollable, formatted text screen. A single shared Activity renders both documents rather than
 * one Activity per document, since they only differ in title and body content; which document to
 * show is selected via {@link #EXTRA_DOCUMENT_TYPE}.
 *
 * <p>Reached from the "Terms of Use" / "Privacy Policy" rows on {@link SettingsActivity}, and from
 * the clickable links in the sign-up screen's terms checkbox label.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 11/08/2026
 */
public class LegalDocumentActivity extends BaseActivity {

    /** Intent extra: which document to display, one of {@link #DOCUMENT_TERMS_OF_USE} or {@link #DOCUMENT_PRIVACY_POLICY}. */
    public static final String EXTRA_DOCUMENT_TYPE = "extra_document_type";

    /** {@link #EXTRA_DOCUMENT_TYPE} value for the Terms of Use document. */
    public static final String DOCUMENT_TERMS_OF_USE = "terms_of_use";

    /** {@link #EXTRA_DOCUMENT_TYPE} value for the Privacy Policy document. */
    public static final String DOCUMENT_PRIVACY_POLICY = "privacy_policy";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_legal_document);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        boolean isPrivacyPolicy = DOCUMENT_PRIVACY_POLICY.equals(getIntent().getStringExtra(EXTRA_DOCUMENT_TYPE));

        ((TextView) findViewById(R.id.tv_title)).setText(isPrivacyPolicy
                ? R.string.legal_privacy_policy_title
                : R.string.legal_terms_of_use_title);

        ((TextView) findViewById(R.id.tv_updated)).setText(
                getString(R.string.legal_last_updated_format, getString(R.string.legal_last_updated_date)));

        ((TextView) findViewById(R.id.tv_body)).setText(renderBody(isPrivacyPolicy
                ? R.string.legal_privacy_policy_body
                : R.string.legal_terms_of_use_body));
    }

    /**
     * Parses a body string resource's {@code <b>}/{@code <br/>} markup into a styled
     * {@link CharSequence} ready to hand to a {@link TextView}.
     *
     * Complexity:
     * Time: O(n) — n is the body text length
     * Space: O(n)
     *
     * @param bodyRes the string resource id holding the document's HTML-formatted body
     * @return the parsed, styled body text
     */
    private CharSequence renderBody(@StringRes int bodyRes) {
        return HtmlCompat.fromHtml(getString(bodyRes), HtmlCompat.FROM_HTML_MODE_COMPACT);
    }
}
