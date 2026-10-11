package com.example.donutbounty.core;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The decision point: takes a raw incoming message and decides whether it is a genuine payment to count.
 * Checks, in order: parse (full match + validation) -> not from yourself -> bounty running -> not a duplicate.
 */
public final class PaymentTracker {
    public enum Outcome { COUNTED, NOT_A_PAYMENT, SELF_PAYMENT, NOT_RUNNING, DUPLICATE, REJECTED }

    public record Result(Outcome outcome, ParsedPayment payment) {}

    private final Supplier<PaymentParser> parser;
    private final DuplicateFilter duplicates;
    private final Leaderboard leaderboard;
    private final BountyTimer timer;
    private final LongSupplier wallClockMillis;
    private final Supplier<String> selfName;
    private final BooleanSupplier countOnlyWhileRunning;
    private final long maxAmountCents;
    private long sessionRaisedCents;

    public PaymentTracker(Supplier<PaymentParser> parser, DuplicateFilter duplicates, Leaderboard leaderboard, BountyTimer timer,
                          LongSupplier wallClockMillis, Supplier<String> selfName,
                          BooleanSupplier countOnlyWhileRunning, long maxAmountCents) {
        this.parser = parser;
        this.duplicates = duplicates;
        this.leaderboard = leaderboard;
        this.timer = timer;
        this.wallClockMillis = wallClockMillis;
        this.selfName = selfName;
        this.countOnlyWhileRunning = countOnlyWhileRunning;
        this.maxAmountCents = maxAmountCents;
    }

    public synchronized Result processIncoming(String raw) {
        Optional<ParsedPayment> parsed = parser.get().parse(raw);
        if (parsed.isEmpty()) return new Result(Outcome.NOT_A_PAYMENT, null);
        ParsedPayment p = parsed.get();

        String me = selfName.get();
        if (me != null && me.equalsIgnoreCase(p.player())) return new Result(Outcome.SELF_PAYMENT, p);

        if (countOnlyWhileRunning.getAsBoolean() && timer.state() != BountyTimer.State.RUNNING) return new Result(Outcome.NOT_RUNNING, p);

        if (duplicates.isDuplicate(PaymentParser.normalise(raw))) return new Result(Outcome.DUPLICATE, p);

        leaderboard.record(p.player(), p.amountCents(), wallClockMillis.getAsLong());
        if (timer.state() == BountyTimer.State.RUNNING) sessionRaisedCents += p.amountCents();
        return new Result(Outcome.COUNTED, p);
    }

    /** A payment the player confirmed by hand. Bypasses the message parser, but not username/amount validation. */
    public synchronized Result addManual(String player, long amountCents) {
        if (!PaymentParser.isValidUsername(player) || amountCents <= 0 || amountCents > maxAmountCents) {
            return new Result(Outcome.REJECTED, null);
        }
        ParsedPayment p = new ParsedPayment(player, amountCents, "manual");
        leaderboard.record(player, amountCents, wallClockMillis.getAsLong());
        BountyTimer.State s = timer.state();
        if (s == BountyTimer.State.RUNNING || s == BountyTimer.State.PAUSED) sessionRaisedCents += amountCents;
        return new Result(Outcome.COUNTED, p);
    }

    public synchronized long sessionRaisedCents() {
        return sessionRaisedCents;
    }

    public synchronized void resetSession() {
        sessionRaisedCents = 0;
    }
}
