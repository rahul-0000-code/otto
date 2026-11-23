package com.aicallagent.app;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps the service's diagnostic lines in the app's private files so a failed call can be read
 * afterwards. Samsung logs every isInCall() poll, which pushes these lines out of logcat within
 * minutes. Lines carry stages, flags and counts only, never caller or reply text.
 */
final class FlowTrace {
    private static final long MAX_BYTES = 256 * 1024;
    private static FlowTrace shared;

    private final File file;
    private final File previous;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat clock = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    /** One writer per process, shared by the service and the ring receiver. */
    static synchronized FlowTrace get(Context context) {
        if (shared == null) shared = new FlowTrace(context.getApplicationContext());
        return shared;
    }

    private FlowTrace(Context context) {
        file = new File(context.getFilesDir(), "flow-trace.txt");
        previous = new File(context.getFilesDir(), "flow-trace.1.txt");
    }

    void add(String line) {
        String stamped = clock.format(new Date()) + " " + line + "\n";
        writer.execute(() -> {
            try {
                if (file.length() > MAX_BYTES) {
                    previous.delete();
                    file.renameTo(previous);
                }
                try (FileWriter out = new FileWriter(file, true)) {
                    out.write(stamped);
                }
            } catch (IOException ignored) {
                // Diagnostics only; never disturb the call.
            }
        });
    }
}
