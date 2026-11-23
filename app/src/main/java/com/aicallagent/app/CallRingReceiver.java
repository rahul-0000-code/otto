package com.aicallagent.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.aicallagent.app.agent.AgentConfig;
import com.aicallagent.app.agent.AgentSettings;
import com.aicallagent.app.agent.AnswerMode;

/**
 * With "accessibility only during calls" on, Otto's accessibility is off between calls. Android
 * still tells the app when a call starts ringing, and this switches the service back on in time
 * for the answer countdown. The service switches itself off again once the call is over.
 */
public final class CallRingReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;
        if (!TelephonyManager.EXTRA_STATE_RINGING.equals(intent.getStringExtra(TelephonyManager.EXTRA_STATE))) return;
        AgentConfig config = AgentSettings.load(context);
        if (!config.getAccessibilityOnlyDuringCalls() || config.effectiveMode() == AnswerMode.OFF) return;
        if (AccessibilitySwitch.isOn(context)) return;
        boolean on = AccessibilitySwitch.turnOn(context);
        String line = "AccessibilityAutoOn reason=RINGING switched=" + on
                + (on ? "" : " permission=" + AccessibilitySwitch.canSwitch(context));
        Log.i("CallAccessibility", line);
        FlowTrace.get(context).add(line);
    }
}
