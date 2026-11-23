package com.aicallagent.app;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Switches Otto's accessibility service on from outside the service. Needs WRITE_SECURE_SETTINGS,
 * which only the owner can grant (once, over adb). Switching off needs no permission: the service
 * calls disableSelf(). Other accessibility services in the setting are always kept.
 */
public final class AccessibilitySwitch {
    public static final String GRANT_COMMAND =
            "adb shell pm grant com.saathi.callassistant android.permission.WRITE_SECURE_SETTINGS";

    private AccessibilitySwitch() { }

    public static boolean canSwitch(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isOn(Context context) {
        ComponentName self = self(context);
        for (String entry : enabled(context)) {
            if (self.equals(ComponentName.unflattenFromString(entry))) return true;
        }
        return false;
    }

    /** Returns true when the service is on afterwards. */
    public static boolean turnOn(Context context) {
        if (isOn(context)) return true;
        if (!canSwitch(context)) return false;
        List<String> services = enabled(context);
        services.add(self(context).flattenToString());
        try {
            Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, TextUtils.join(":", services));
            Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (SecurityException revoked) {
            return false;
        }
    }

    private static ComponentName self(Context context) {
        return new ComponentName(context, CallAccessibilityService.class);
    }

    private static List<String> enabled(Context context) {
        List<String> services = new ArrayList<>();
        String value = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (value == null) return services;
        for (String entry : value.split(":")) {
            if (!entry.trim().isEmpty()) services.add(entry.trim());
        }
        return services;
    }
}
