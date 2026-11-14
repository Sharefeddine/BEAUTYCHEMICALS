package com.chemmanager.model.distribution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A tour: one truck, one zone, and the delivery notes loaded on it, in the order they are served.
 *
 * <p>The tour drives the notes with it: loading and departing put every note of the tour in the same
 * state, and closing the day sends the notes nobody settled back to the yard.</p>
 */
public record DeliveryRoute(
        long id,
        String routeNumber,
        LocalDate routeDate,
        long vehicleId,
        String plate,
        String model,
        Long driverId,
        String driverName,
        String representativeName,
        String wilayaZone,
        String status,
        BigDecimal fuelCost,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        int stopCount,
        int deliveredCount,
        BigDecimal totalQuantity,
        List<Stop> stops) {

    public DeliveryRoute {
        stops = stops == null ? List.of() : List.copyOf(stops);
        fuelCost = fuelCost == null ? BigDecimal.ZERO : fuelCost;
        totalQuantity = totalQuantity == null ? BigDecimal.ZERO : totalQuantity;
    }

    public boolean loadable() {
        return "PLANNED".equals(status);
    }

    public boolean startable() {
        return "PLANNED".equals(status) || "LOADING".equals(status);
    }

    public boolean completable() {
        return "IN_TRANSIT".equals(status) || "LOADING".equals(status);
    }

    public boolean cancellable() {
        return "PLANNED".equals(status) || "LOADING".equals(status);
    }

    public boolean inTransit() {
        return "IN_TRANSIT".equals(status);
    }

    /** How much of the tour is done, in per cent. */
    public double progress() {
        return stopCount <= 0 ? 0 : Math.round(deliveredCount * 1000.0 / stopCount) / 10.0;
    }

    /** One stop: the customer to serve, and what happened there. */
    public record Stop(
            long id,
            int sequence,
            String status,
            LocalDateTime arrivedAt,
            String remarks,
            long deliveryNoteId,
            String noteNumber,
            String noteStatus,
            String signedBy,
            String customerName,
            String customerCode,
            String customerWilaya,
            String customerPhone,
            String customerAddress,
            BigDecimal totalQuantity,
            int lineCount) {

        public Stop {
            totalQuantity = totalQuantity == null ? BigDecimal.ZERO : totalQuantity;
        }

        /** Only a stop that has not been settled can still be signed or refused. */
        public boolean pending() {
            return "PENDING".equals(status);
        }
    }
}
