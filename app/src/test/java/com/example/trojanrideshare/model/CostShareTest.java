package com.example.trojanrideshare.model;

import static org.junit.Assert.*;
import org.junit.Test;

public class CostShareTest {

    @Test
    public void newShareStartsUnpaid() {
        CostShare share = new CostShare("alice", 2017);

        assertEquals("alice", share.getRiderId());
        assertEquals(2017, share.getAmountCents());
        assertFalse(share.isPaid());
        assertFalse(share.isFlagged());
    }

    @Test
    public void payingClearsFlag() {
        CostShare share = new CostShare("alice", 2017);

        share.setFlagged(true);
        share.markPaid();

        assertTrue(share.isPaid());
        assertFalse(share.isFlagged());
    }

    @Test(expected = IllegalStateException.class)
    public void paidShareCannotBeFlagged() {
        CostShare share = new CostShare("alice", 2017);

        share.markPaid();
        share.setFlagged(true);
    }

    @Test(expected = IllegalStateException.class)
    public void paymentCannotBeReversed() {
        CostShare share = new CostShare("alice", 2017);

        share.markPaid();
        share.setPaid(false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeAmountIsRejected() {
        new CostShare("alice", -100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyRiderIdIsRejected() {
        new CostShare("", 100);
    }
}

