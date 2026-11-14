package com.chemmanager.model.distribution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A truck of the fleet. It can be planned on tours, and the plate is the identity the customer sees
 * on the delivery note.
 */
public record Vehicle(
        long id,
        String plate,
        String model,
        BigDecimal capacityKg,
        Long driverId,
        String driverName,
        String driverPhone,
        boolean active,
        int routeCount,
        LocalDate lastRouteDate,
        int openRoutes) {

    public Vehicle {
        Objects.requireNonNull(plate, "plate");
        capacityKg = capacityKg == null ? BigDecimal.ZERO : capacityKg;
    }

    /** A vehicle with an open tour cannot be switched off mid-delivery. */
    public boolean deactivatable() {
        return active && openRoutes == 0;
    }

    public String driver() {
        return driverName == null || driverName.isBlank() ? "—" : driverName;
    }
}
