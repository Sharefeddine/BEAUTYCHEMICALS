package com.chemmanager.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.RouteQuery;
import com.chemmanager.model.distribution.DeliveryRoute;
import com.chemmanager.model.distribution.Driver;
import com.chemmanager.model.distribution.Vehicle;

/** The fleet and its daily tours. */
public interface DistributionService {

    PagedResult<DeliveryRoute> routes(RouteQuery query);

    DeliveryRoute route(long id);

    List<Vehicle> vehicles();

    Vehicle createVehicle(NewVehicle request);

    Vehicle updateVehicle(long id, NewVehicle request);

    List<Driver> drivers();

    DeliveryRoute createRoute(NewRoute request);

    DeliveryRoute loadRoute(long id);

    DeliveryRoute startRoute(long id);

    DeliveryRoute deliverStop(long routeId, long stopId, StopOutcome outcome);

    DeliveryRoute completeRoute(long id, BigDecimal fuelCost);

    DeliveryRoute cancelRoute(long id);

    /** A vehicle as the fleet dialog types it; {@code active} only matters when updating. */
    record NewVehicle(String plate, String model, BigDecimal capacityKg, Long driverId, boolean active) {
    }

    /** A tour to plan: which truck, which day, which zone and which prepared notes ride along. */
    record NewRoute(LocalDate routeDate, long vehicleId, Long driverId, String wilayaZone,
                    List<Long> deliveryNoteIds) {

        public NewRoute {
            deliveryNoteIds = deliveryNoteIds == null ? List.of() : List.copyOf(deliveryNoteIds);
        }
    }

    /** What happened at a stop: DELIVERED, PARTIAL or REFUSED (which needs a remark). */
    record StopOutcome(String status, String signedBy, String remarks) {
    }
}
