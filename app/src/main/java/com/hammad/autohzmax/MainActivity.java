package com.hammad.autohzmax;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

/** Status page: nothing to configure — enable the module in LSPosed with scope "System Framework" and reboot. */
public class MainActivity extends Activity {
    /** Replaced by the hook when the module is active (the module hooks its own isActive()). */
    static boolean isActive() { return false; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        t.setPadding(p, p, p, p);
        t.setTextSize(15);
        t.setText("AutoHz Max\n\n"
                + "Module status: " + (isActive() ? "ACTIVE" : "NOT ACTIVE — enable it in LSPosed, tick \"System Framework\" (android) in its scope, then reboot.") + "\n\n"
                + "What it does: in Settings > Display > Refresh rate choose Auto. The phone then switches between 60 Hz and the highest rate the panel offers (144 Hz stock, 165 Hz with a 165 Hz DTBO) instead of stopping at 120 Hz. Idle still drops to 60; games keep their own Game Space setting.\n\n"
                + "Nothing to configure. Check LSPosed > Logs for lines starting with \"AutoHzMax:\".");
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        setContentView(sv);
    }
}
