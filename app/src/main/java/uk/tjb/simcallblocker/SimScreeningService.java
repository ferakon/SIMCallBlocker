package uk.tjb.simcallblocker;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.PhoneAccountHandle;

/**
 * Called by Android for every incoming call it asks us to screen. If the call came in on
 * the chosen SIM, it is rejected (or silenced); everything else is allowed through untouched.
 */
public class SimScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(Call.Details details) {
        Prefs prefs = new Prefs(this);
        PhoneAccountHandle handle = details.getAccountHandle();

        boolean incoming = details.getCallDirection() == Call.Details.DIRECTION_INCOMING;
        boolean block = incoming && prefs.isEnabled() && prefs.matches(handle);

        CallResponse.Builder r = new CallResponse.Builder();
        if (block) {
            if (Prefs.MODE_SILENCE.equals(prefs.mode())) {
                // Call still arrives and can be answered, but never rings or vibrates.
                r.setSilenceCall(true);
            } else {
                boolean keepLog = prefs.keepInCallLog();
                r.setDisallowCall(true)
                        .setRejectCall(true)          // caller hears it rejected / goes to voicemail
                        .setSkipCallLog(!keepLog)
                        .setSkipNotification(true);   // no missed-call notification
            }
            prefs.logBlocked(numberOf(details));
        }
        respondToCall(details, r.build());
    }

    private static String numberOf(Call.Details d) {
        Uri u = d.getHandle();
        return u == null ? null : u.getSchemeSpecificPart();
    }
}
