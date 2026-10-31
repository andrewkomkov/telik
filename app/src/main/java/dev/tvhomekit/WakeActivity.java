package dev.tvhomekit;

import android.app.Activity;
import android.os.Bundle;

/** Прозрачный экран: система включает дисплей при его запуске (turnScreenOn), сразу закрывается. */
public final class WakeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
