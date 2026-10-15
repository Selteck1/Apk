package ru.selteck.nativeapk;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public final class MainActivity extends Activity {
    static {
        System.loadLibrary("selteck");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView message = new TextView(this);
        message.setGravity(Gravity.CENTER);
        message.setTextSize(18);
        message.setPadding(32, 32, 32, 32);
        message.setText("Selteck Native\n\nAPK build test\nNative library loaded.");
        setContentView(message);
    }
}
