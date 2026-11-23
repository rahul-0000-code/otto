package com.aicallagent.app;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * WhatsApp's chat screen, for Samy's "WhatsApp Soham …" command. Otto opens the chat with the
 * message already typed; this finds the message box and the Send button so the service can press it.
 */
final class WhatsAppUi {
    private WhatsAppUi() { }

    static boolean isWhatsApp(CharSequence pkg) {
        return pkg != null && ("com.whatsapp".contentEquals(pkg) || "com.whatsapp.w4b".contentEquals(pkg));
    }

    static AccessibilityNodeInfo entry(AccessibilityNodeInfo root) {
        return visible(root, root.getPackageName() + ":id/entry");
    }

    static AccessibilityNodeInfo send(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo send = visible(root, root.getPackageName() + ":id/send");
        if (send != null && send.isEnabled() && send.isClickable()) return send;
        // Older layouts: an unlabelled image button described as "Send".
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("Send")) {
            if (node != null && node.isVisibleToUser() && node.isClickable() && node.isEnabled()
                    && node.getContentDescription() != null && "Send".contentEquals(node.getContentDescription())) {
                return node;
            }
        }
        return null;
    }

    /** True only when the box holds exactly the dictated message (ignoring outer spaces). */
    static boolean holds(AccessibilityNodeInfo entry, String message) {
        CharSequence text = entry == null ? null : entry.getText();
        return text != null && text.toString().trim().equals(message.trim());
    }

    static boolean empty(AccessibilityNodeInfo entry) {
        return entry != null && (entry.isShowingHintText() || entry.getText() == null || entry.getText().length() == 0);
    }

    private static AccessibilityNodeInfo visible(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        for (AccessibilityNodeInfo node : nodes) {
            if (node != null && node.isVisibleToUser()) return node;
        }
        return null;
    }
}
