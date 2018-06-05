/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.words.utils;

import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 *
 * @author claims
 */
public class LogSetup {

    public static void setupLog(Logger log) {
        log.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            private static final String format = "%1$s %n";

            @Override
            public synchronized String format(LogRecord lr) {
                return String.format(format,
                        lr.getMessage()
                );
            }
        });
        log.addHandler(handler);;
    }

}
