package uk.tjb.simcallblocker;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_ROLE = 1;
    private static final int REQ_PERM = 2;

    private Prefs prefs;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, p, p, p);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        root.removeAllViews();

        title("SIM Call Blocker");
        body("Rejects every incoming call on one chosen SIM. Calls on your other SIM ring as normal.");

        // ---- Step 1: call-screening role ----
        heading("1. Allow call screening");
        RoleManager rm = getSystemService(RoleManager.class);
        boolean hasRole = rm != null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        if (hasRole) {
            body("✓ This app is your call screening app.");
        } else {
            body("Android only lets one app screen calls. Tap below and choose SIM Call Blocker.");
            button("Set as call screening app", v -> {
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    startActivityForResult(
                            rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE);
                }
            });
        }

        // ---- Step 2: choose SIM ----
        heading("2. Choose the SIM to block");
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            body("The app needs the Phone permission to see which SIMs you have.");
            button("Grant permission", v ->
                    requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, REQ_PERM));
        } else {
            simPicker();
        }

        // ---- Step 3: behaviour ----
        heading("3. Blocking");
        Switch sw = new Switch(this);
        sw.setText("Block calls on the chosen SIM");
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sw.setChecked(prefs.isEnabled());
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setOnCheckedChangeListener((x, on) -> { prefs.setEnabled(on); render(); });
        root.addView(sw);

        RadioGroup modes = new RadioGroup(this);
        RadioButton reject = radio("Reject (caller is cut off / sent to voicemail)");
        RadioButton silence = radio("Silence (call arrives but never rings)");
        modes.addView(reject);
        modes.addView(silence);
        (Prefs.MODE_SILENCE.equals(prefs.mode()) ? silence : reject).setChecked(true);
        modes.setOnCheckedChangeListener((g, id) ->
                prefs.setMode(id == silence.getId() ? Prefs.MODE_SILENCE : Prefs.MODE_REJECT));
        root.addView(modes);

        CheckBox keep = new CheckBox(this);
        keep.setText("Keep rejected calls in the call log");
        keep.setChecked(prefs.keepInCallLog());
        keep.setOnCheckedChangeListener((x, on) -> prefs.setKeepInCallLog(on));
        root.addView(keep);

        // ---- Status ----
        heading("Status");
        String label = prefs.blockedLabel();
        String status;
        if (!hasRole) status = "Not active: grant call screening (step 1).";
        else if (label == null) status = "Not active: choose a SIM (step 2).";
        else if (!prefs.isEnabled()) status = "Paused. Turn on blocking (step 3).";
        else status = "Active: blocking calls on " + label + ".";
        body(status);

        body("Blocked so far: " + prefs.blockedCount());
        String recent = prefs.recent();
        if (!recent.isEmpty()) {
            TextView t = body(recent);
            t.setTypeface(Typeface.MONOSPACE);
            button("Clear list", v -> { prefs.clearLog(); render(); });
        }
    }

    private void simPicker() {
        TelecomManager tm = getSystemService(TelecomManager.class);
        List<PhoneAccountHandle> handles;
        try {
            handles = tm.getCallCapablePhoneAccounts();
        } catch (SecurityException e) {
            handles = Collections.emptyList();
        }
        if (handles.isEmpty()) {
            body("No SIMs found.");
            return;
        }
        RadioGroup g = new RadioGroup(this);
        for (PhoneAccountHandle h : handles) {
            String label = describe(tm, h);
            RadioButton rb = radio(label);
            g.addView(rb);
            if (prefs.isSelected(h)) rb.setChecked(true);
            rb.setOnClickListener(v -> { prefs.setBlockedAccount(h, label); render(); });
        }
        root.addView(g);
    }

    /** Human-readable name for a SIM, e.g. "Movistar (SIM 2)". */
    private String describe(TelecomManager tm, PhoneAccountHandle h) {
        String label = null;
        try {
            PhoneAccount pa = tm.getPhoneAccount(h);
            if (pa != null && pa.getLabel() != null) label = pa.getLabel().toString();
        } catch (Exception ignored) { }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                TelephonyManager tel = getSystemService(TelephonyManager.class);
                int subId = tel.getSubscriptionId(h);
                SubscriptionManager sm = getSystemService(SubscriptionManager.class);
                SubscriptionInfo info = sm.getActiveSubscriptionInfo(subId);
                if (info != null) {
                    String name = String.valueOf(info.getDisplayName());
                    String carrier = String.valueOf(info.getCarrierName());
                    String slot = info.isEmbedded() ? "eSIM" : "SIM " + (info.getSimSlotIndex() + 1);
                    String extra = carrier.equals(name) ? slot : carrier + ", " + slot;
                    return name + " (" + extra + ")";
                }
            } catch (Exception ignored) { }
        }
        return label != null ? label : "SIM " + h.getId();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        render();
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        render();
    }

    // ---- tiny view helpers ----
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void title(String s) {
        TextView t = text(s, 24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
    }

    private void heading(String s) {
        TextView t = text(s, 18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(20), 0, dp(4));
    }

    private TextView body(String s) {
        TextView t = text(s, 15);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        root.addView(t);
        return t;
    }

    private RadioButton radio(String s) {
        RadioButton rb = new RadioButton(this);
        rb.setId(View.generateViewId());
        rb.setText(s);
        rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        rb.setPadding(0, dp(6), 0, dp(6));
        return rb;
    }

    private void button(String s, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(s);
        btn.setOnClickListener(l);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(btn);
    }
}
