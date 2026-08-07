package com.cooksync.app.ui.settings;

import android.os.Bundle;

import com.cooksync.app.R;
import com.cooksync.app.ui.common.BaseActivity;

/**
 * Placeholder destination for the "Account details" row in {@link SettingsActivity} and for the
 * top-bar avatar in {@code HomeActivity}. A dedicated screen for editing name, email, password
 * and avatar in one place (matching the design) is planned separately; until then this shows a
 * simple "coming soon" state so both entry points land somewhere real rather than a bare toast.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public class AccountDetailsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_details);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }
}
