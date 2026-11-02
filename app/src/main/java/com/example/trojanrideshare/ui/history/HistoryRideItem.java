
package com.example.trojanrideshare.ui.history;

public class HistoryRideItem {

    private final String title;
    private final String route;
    private final long departureMillis;
    private final String status;
    private final long costCents;

    public HistoryRideItem(
            String title,
            String route,
            long departureMillis,
            String status,
            long costCents) {

        this.title = title;
        this.route = route;
        this.departureMillis = departureMillis;
        this.status = status;
        this.costCents = costCents;
    }

    public String getTitle() {
        return title;
    }

    public String getRoute() {
        return route;
    }

    public long getDepartureMillis() {
        return departureMillis;
    }

    public String getStatus() {
        return status;
    }

    public long getCostCents() {
        return costCents;
    }
}
