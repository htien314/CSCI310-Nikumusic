package com.example.trojanrideshare.model;

public class CostShare {

    private String riderId;
    private long amountCents;
    private boolean paid;
    private boolean flagged;

    // Required by Firestore.
    public CostShare() {}

    public CostShare(String riderId, long amountCents) {
        if (riderId == null || riderId.trim().isEmpty()
                || amountCents < 0) {
            throw new IllegalArgumentException("Invalid cost share.");
        }

        this.riderId = riderId;
        this.amountCents = amountCents;
        this.paid = false;
        this.flagged = false;
    }

    public String getRiderId() {
        return riderId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public boolean isPaid() {
        return paid;
    }

    public boolean isFlagged() {
        return flagged;
    }

    // Payment is permanent once recorded.
    public void markPaid() {
        paid = true;
        flagged = false;
    }

    // Only unpaid shares may be flagged.
    public void setFlagged(boolean flagged) {
        if (paid && flagged) {
            throw new IllegalStateException(
                    "Paid riders cannot be flagged.");
        }
        this.flagged = flagged;
    }

    // Firestore deserialization setters.
    public void setRiderId(String riderId) {
        this.riderId = riderId;
    }

    public void setAmountCents(long amountCents) {
        this.amountCents = amountCents;
    }

    public void setPaid(boolean paid) {
        if (this.paid && !paid) {
            throw new IllegalStateException(
                    "Payment cannot be reversed.");
        }
        this.paid = paid;
        if (paid) flagged = false;
    }
}

