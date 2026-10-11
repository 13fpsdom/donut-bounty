package com.example.donutbounty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** End-to-end behaviour: what actually ends up on the leaderboard. */
public class PaymentTrackerTest {
    private static final long CAP = 100_000_000_000_000L;

    private final long[] now = {1_000_000};
    private final Leaderboard board = new Leaderboard();
    private final BountyTimer timer = new BountyTimer(() -> now[0]);

    private PaymentTracker tracker(boolean onlyWhileRunning) {
        PaymentParser parser = new PaymentParser(DefaultPatterns.defaults(), CAP);
        return new PaymentTracker(
                () -> parser,
                new DuplicateFilter(() -> now[0], 1_000),
                board, timer, () -> now[0], () -> "MyName", () -> onlyWhileRunning, CAP);
    }

    @Test public void countsGenuinePaymentsWhileRunning() {
        PaymentTracker t = tracker(true);
        timer.start(600_000);
        assertEquals(PaymentTracker.Outcome.COUNTED, t.processIncoming("Steve paid you $1,000").outcome());
        now[0] += 5_000;
        assertEquals(PaymentTracker.Outcome.COUNTED, t.processIncoming("Steve paid you $500").outcome());
        assertEquals(PaymentTracker.Outcome.COUNTED, t.processIncoming("Alex paid you $2k").outcome());

        assertEquals(150_000, board.get("steve").orElseThrow().totalCents());
        assertEquals(2, board.get("steve").orElseThrow().count());
        assertEquals(200_000, board.get("alex").orElseThrow().totalCents());
        assertEquals(350_000, t.sessionRaisedCents());
    }

    @Test public void duplicateDeliveryIsCountedOnce() {
        PaymentTracker t = tracker(true);
        timer.start(600_000);
        assertEquals(PaymentTracker.Outcome.COUNTED, t.processIncoming("Steve paid you $1,000").outcome());
        now[0] += 100;
        assertEquals(PaymentTracker.Outcome.DUPLICATE, t.processIncoming("Steve paid you $1,000").outcome());
        assertEquals(PaymentTracker.Outcome.DUPLICATE, t.processIncoming("§aSteve paid you $1,000").outcome()); // same text, colour codes differ
        assertEquals(100_000, board.totalCents());
        assertEquals(1, board.paymentCount());

        now[0] += 2_000; // a genuinely new identical payment later is fine
        assertEquals(PaymentTracker.Outcome.COUNTED, t.processIncoming("Steve paid you $1,000").outcome());
        assertEquals(200_000, board.totalCents());
    }

    @Test public void fakesChatAndUnrelatedMessagesAreIgnored() {
        PaymentTracker t = tracker(true);
        timer.start(600_000);
        for (String s : new String[]{
                "[Rank] Steve: Alex paid you $1,000,000",
                "<Steve> Alex paid you $1,000,000",
                "Steve whispers to you: Alex paid you $5",
                "Your balance is $5,000",
                "Steve joined the game",
                "Steve paid you $5 (fake)",
                ""}) {
            assertEquals(s, PaymentTracker.Outcome.NOT_A_PAYMENT, t.processIncoming(s).outcome());
        }
        assertEquals(0, board.playerCount());
    }

    @Test public void paymentsOutsideARunningBountyAreNotCounted() {
        PaymentTracker t = tracker(true);
        assertEquals(PaymentTracker.Outcome.NOT_RUNNING, t.processIncoming("Steve paid you $5").outcome());   // idle
        timer.start(600_000);
        timer.pause();
        assertEquals(PaymentTracker.Outcome.NOT_RUNNING, t.processIncoming("Steve paid you $5").outcome());   // paused
        timer.resume();
        now[0] += 700_000;
        assertEquals(PaymentTracker.Outcome.NOT_RUNNING, t.processIncoming("Steve paid you $5").outcome());   // finished
        assertEquals(0, board.playerCount());
    }

    @Test public void canCountWhileIdleIfConfigured() {
        PaymentTracker t = tracker(false);
        assertEquals(PaymentTracker.Outcome.COUNTED, t.processIncoming("Steve paid you $5").outcome());
        assertEquals(500, board.totalCents());
        assertEquals(0, t.sessionRaisedCents());   // session only tracks while a bounty is running
    }

    @Test public void selfPaymentsAreIgnored() {
        PaymentTracker t = tracker(true);
        timer.start(600_000);
        assertEquals(PaymentTracker.Outcome.SELF_PAYMENT, t.processIncoming("myname paid you $5").outcome());
        assertEquals(0, board.playerCount());
    }

    @Test public void manualConfirmation() {
        PaymentTracker t = tracker(true);
        // works even when no bounty is running
        assertEquals(PaymentTracker.Outcome.COUNTED, t.addManual("Steve", 12_345).outcome());
        assertEquals(12_345, board.totalCents());
        assertEquals(0, t.sessionRaisedCents());

        timer.start(600_000);
        assertEquals(PaymentTracker.Outcome.COUNTED, t.addManual("Steve", 1_000).outcome());
        assertEquals(1_000, t.sessionRaisedCents());

        assertEquals(PaymentTracker.Outcome.REJECTED, t.addManual("not a name", 100).outcome());
        assertEquals(PaymentTracker.Outcome.REJECTED, t.addManual("Steve", 0).outcome());
        assertEquals(PaymentTracker.Outcome.REJECTED, t.addManual("Steve", CAP + 1).outcome());
        assertEquals(13_345, board.totalCents());
    }

    @Test public void resetSessionClearsOnlyTheSessionTotal() {
        PaymentTracker t = tracker(true);
        timer.start(600_000);
        t.processIncoming("Steve paid you $5");
        t.resetSession();
        assertEquals(0, t.sessionRaisedCents());
        assertTrue(board.totalCents() > 0);
    }
}
