package com.cooksync.app.ui.settings;

import android.os.Bundle;

import com.cooksync.app.R;
import com.cooksync.app.data.local.CookingPreferencesStore;
import com.cooksync.app.ui.common.BaseActivity;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Device-level cooking preferences: whether cooking mode keeps the screen awake, and whether a
 * finished step timer plays a sound and vibrates. Reads and writes {@link CookingPreferencesStore}
 * directly rather than through a ViewModel/repository, since these are local device settings with
 * no network or account dependency.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public class CookingPreferencesActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cooking_preferences);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        MaterialSwitch switchScreenAwake = findViewById(R.id.switch_screen_awake);
        switchScreenAwake.setChecked(CookingPreferencesStore.isScreenAwakeEnabled());
        switchScreenAwake.setOnCheckedChangeListener((button, checked) ->
                CookingPreferencesStore.setScreenAwakeEnabled(checked));

        MaterialSwitch switchTimerSound = findViewById(R.id.switch_timer_sound);
        switchTimerSound.setChecked(CookingPreferencesStore.isTimerSoundEnabled());
        switchTimerSound.setOnCheckedChangeListener((button, checked) ->
                CookingPreferencesStore.setTimerSoundEnabled(checked));
    }
}
