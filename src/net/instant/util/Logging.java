package net.instant.util;

import java.io.OutputStream;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

public final class Logging {

    private Logging() {}

    public static void initFormat() {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                           "[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL " +
                           "%4$s %3$s] %5$s%6$s%n");
    }

    public static void redirectToStream(OutputStream os) {
        Logger rootLogger = Logger.getLogger("");
        Handler newhnd = new StreamHandler(os, new SimpleFormatter()) {
            public synchronized void publish(LogRecord record) {
                // HACK: Force quick flushing.
                super.publish(record);
                flush();
            }
        };
        for (Handler hnd : rootLogger.getHandlers()) {
            rootLogger.removeHandler(hnd);
        }
        rootLogger.addHandler(newhnd);
    }

}
