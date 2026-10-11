package com.example.donutbounty.core;

import java.util.List;

/**
 * Built-in payment formats.
 *
 * IMPORTANT: the enabled default is based on how third-party DonutSMP mods describe the incoming-payment
 * message ("<Player> paid you $<amount>", amounts such as $2.5m). It has NOT been checked against the live server
 * by the author. If your server prints something different, edit the patterns in config/donutbounty/config.json.
 */
public final class DefaultPatterns {
    public static final String PLAYER = "(?<player>\\.?[A-Za-z0-9_]{1,16})";
    public static final String AMOUNT = "(?<amount>[0-9][0-9,]*(?:\\.[0-9]+)?[KkMmBbTt]?)";

    private DefaultPatterns() {}

    public static List<PaymentPattern> defaults() {
        return List.of(
                new PaymentPattern("paid_you", "^" + PLAYER + " paid you \\$" + AMOUNT + "[.!]?$", true),
                // Unverified guess kept disabled as a template; enable it only if your server prints this form.
                new PaymentPattern("received_from_UNVERIFIED", "^You received \\$" + AMOUNT + " from " + PLAYER + "[.!]?$", false));
    }
}
