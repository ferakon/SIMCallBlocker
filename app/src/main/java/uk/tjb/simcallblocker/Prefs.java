package uk.tjb.simcallblocker;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small wrapper around SharedPreferences for the app's settings and block log. */
final class Prefs {
    static final String MODE_REJECT = "reject";
    static final String MODE_SILENCE = "silence";

    private static final String FILE = "settings";
    private static final String K_ENABLED = "enabled";
    private static final String K_COMPONENT = "account_component";
    private static final String K_ID = "account_id";
    private static final String K_SUB_ID = "sub_id";
    private static final String K_LABEL = "account_label";
    private static final String K_MODE = "mode";
    private static final String K_KEEP_LOG = "keep_in_call_log";
    private static final String K_COUNT = "blocked_count";
    private static final String K_RECENT = "recent";
    private static final String K_LAST_CHECK = "last_check";
    private static final int MAX_RECENT = 15;

    private final SharedPreferences sp;

    Prefs(Context c) {
        // Device-protected storage so blocking also works before the phone is first unlocked.
        Context dc = c.createDeviceProtectedStorageContext();
        dc.moveSharedPreferencesFrom(c, FILE);
        sp = dc.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static String now() {
        return new SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(new Date());
    }

    boolean isEnabled() { return sp.getBoolean(K_ENABLED, false); }
    void setEnabled(boolean v) { sp.edit().putBoolean(K_ENABLED, v).apply(); }

    String mode() { return sp.getString(K_MODE, MODE_REJECT); }
    void setMode(String m) { sp.edit().putString(K_MODE, m).apply(); }

    boolean keepInCallLog() { return sp.getBoolean(K_KEEP_LOG, true); }
    void setKeepInCallLog(boolean v) { sp.edit().putBoolean(K_KEEP_LOG, v).apply(); }

    String blockedLabel() { return sp.getString(K_LABEL, null); }

    void setBlockedAccount(PhoneAccountHandle h, String label, int subId) {
        sp.edit()
                .putString(K_COMPONENT, h.getComponentName().flattenToString())
                .putString(K_ID, h.getId())
                .putString(K_LABEL, label)
                .putInt(K_SUB_ID, subId)
                .apply();
    }

    /** Subscription id of the chosen SIM, or INVALID_SUBSCRIPTION_ID if not known. */
    int blockedSubId() {
        return sp.getInt(K_SUB_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    /** The chosen SIM's phone account, or null if none chosen. */
    PhoneAccountHandle blockedHandle() {
        String comp = sp.getString(K_COMPONENT, null);
        String id = sp.getString(K_ID, null);
        if (comp == null || id == null) return null;
        ComponentName cn = ComponentName.unflattenFromString(comp);
        return cn == null ? null : new PhoneAccountHandle(cn, id);
    }

    /** True if this phone account is the SIM the user chose to block. */
    boolean matches(PhoneAccountHandle h) {
        PhoneAccountHandle mine = blockedHandle();
        return h != null && mine != null
                && h.getComponentName().equals(mine.getComponentName())
                && h.getId().equals(mine.getId());
    }

    int blockedCount() { return sp.getInt(K_COUNT, 0); }

    String recent() { return sp.getString(K_RECENT, ""); }

    synchronized void logBlocked(String number) {
        String line = now() + "  " + (number == null || number.isEmpty() ? "(hidden number)" : number);
        String[] old = recent().isEmpty() ? new String[0] : recent().split("\n");
        StringBuilder sb = new StringBuilder(line);
        for (int i = 0; i < old.length && i < MAX_RECENT - 1; i++) sb.append('\n').append(old[i]);
        sp.edit().putInt(K_COUNT, blockedCount() + 1).putString(K_RECENT, sb.toString()).apply();
    }

    void clearLog() { sp.edit().putInt(K_COUNT, 0).putString(K_RECENT, "").apply(); }

    /** One-line record of the most recent call the app was asked about, for troubleshooting. */
    String lastCheck() { return sp.getString(K_LAST_CHECK, null); }
    void setLastCheck(String s) { sp.edit().putString(K_LAST_CHECK, s).apply(); }
}
