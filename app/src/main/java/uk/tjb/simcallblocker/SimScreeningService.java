package uk.tjb.simcallblocker;

import android.net.Uri;
import android.os.SystemClock;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Called by Android for every incoming call it asks us to screen.
 *
 * Android does not tell ordinary (non-system) screening apps which SIM a call came in on:
 * Call.Details.getAccountHandle() is null unless the app holds a privileged permission. So
 * instead we look at which SIM's radio is ringing right now. If it is the chosen SIM (and no
 * other SIM is ringing), the call is rejected or silenced. In every other case, including any
 * error, the call is allowed: the app must never block calls on the other SIM.
 */
public class SimScreeningService extends CallScreeningService {

    /** How long to wait for a SIM's ringing state to show up. Android allows ~5 s in total. */
    private static final long WAIT_MS = 2500;
    private static final long POLL_MS = 100;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    public void onScreenCall(Call.Details details) {
        if (details.getCallDirection() != Call.Details.DIRECTION_INCOMING) {
            allow(details);
            return;
        }
        // Polling may take a moment, so keep it off the main thread.
        try {
            worker.execute(() -> screen(details));
        } catch (RejectedExecutionException e) {
            allow(details);
        }
    }

    @Override
    public void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private void screen(Call.Details details) {
        boolean responded = false;
        try {
            Prefs prefs = new Prefs(this);
            String number = numberOf(details);
            boolean block = false;
            String reason;
            long start = SystemClock.elapsedRealtime();

            try {
                PhoneAccountHandle handle = details.getAccountHandle();
                if (!prefs.isEnabled()) {
                    reason = "allowed: blocking is switched off";
                } else if (handle != null) {
                    // Some phones do pass the SIM through; use it when we get it.
                    block = prefs.matches(handle);
                    reason = block ? "blocked: Android reported the chosen SIM"
                            : "allowed: Android reported a different SIM";
                } else if (prefs.blockedHandle() == null) {
                    reason = "allowed: no SIM chosen yet";
                } else {
                    int target = resolveBlockedSub(prefs);
                    if (target == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        reason = "allowed: chosen SIM not found, open the app and pick it again";
                    } else {
                        Ringing r = waitForRinging(target);
                        long ms = SystemClock.elapsedRealtime() - start;
                        block = r.target && !r.other;
                        if (block) reason = "blocked: chosen SIM was ringing (" + ms + " ms)";
                        else if (r.other) reason = "allowed: another SIM was ringing (" + ms + " ms)";
                        else if (!r.targetActive) reason = "allowed: chosen SIM is not active, pick it again";
                        else reason = "allowed: no SIM showed as ringing within " + ms + " ms";
                    }
                }
            } catch (Exception e) {
                block = false;
                reason = "allowed: error " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }

            CallResponse.Builder r = new CallResponse.Builder();
            if (block) {
                if (Prefs.MODE_SILENCE.equals(prefs.mode())) {
                    // Call still arrives and can be answered, but never rings or vibrates.
                    r.setSilenceCall(true);
                } else {
                    r.setDisallowCall(true)
                            .setRejectCall(true)          // caller hears it rejected / goes to voicemail
                            .setSkipCallLog(!prefs.keepInCallLog())
                            .setSkipNotification(true);   // no missed-call notification
                }
            }
            respondToCall(details, r.build());
            responded = true;

            if (block) prefs.logBlocked(number);
            prefs.setLastCheck(Prefs.now() + "  " + displayNumber(number) + "\n" + reason);
        } catch (Exception e) {
            if (!responded) allow(details);
        }
    }

    private void allow(Call.Details details) {
        try {
            respondToCall(details, new CallResponse.Builder().build());
        } catch (Exception ignored) {
            // Nothing more we can do; Telecom lets the call through when we don't answer.
        }
    }

    /** The chosen SIM's subscription id, working it out from the saved phone account if needed. */
    private int resolveBlockedSub(Prefs prefs) {
        int sub = prefs.blockedSubId();
        if (sub != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return sub;
        PhoneAccountHandle h = prefs.blockedHandle();
        if (h == null) return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        return getSystemService(TelephonyManager.class).getSubscriptionId(h);
    }

    private static final class Ringing {
        boolean target;
        boolean other;
        boolean targetActive;
    }

    /** Polls each active SIM's call state until one of them is ringing, or we give up. */
    private Ringing waitForRinging(int targetSub) {
        SubscriptionManager sm = getSystemService(SubscriptionManager.class);
        TelephonyManager base = getSystemService(TelephonyManager.class);
        long deadline = SystemClock.elapsedRealtime() + WAIT_MS;
        Ringing r = new Ringing();

        while (true) {
            r.target = false;
            r.other = false;
            r.targetActive = false;
            List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
            if (subs != null) {
                for (SubscriptionInfo s : subs) {
                    int id = s.getSubscriptionId();
                    if (id == targetSub) r.targetActive = true;
                    int state = base.createForSubscriptionId(id).getCallStateForSubscription();
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        if (id == targetSub) r.target = true; else r.other = true;
                    }
                }
            }
            if (r.target || r.other || SystemClock.elapsedRealtime() >= deadline) return r;
            SystemClock.sleep(POLL_MS);
        }
    }

    private static String numberOf(Call.Details d) {
        Uri u = d.getHandle();
        return u == null ? null : u.getSchemeSpecificPart();
    }

    private static String displayNumber(String n) {
        return n == null || n.isEmpty() ? "(hidden number)" : n;
    }
}
