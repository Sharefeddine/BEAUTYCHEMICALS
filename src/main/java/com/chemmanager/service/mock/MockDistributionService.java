package com.chemmanager.service.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.RouteQuery;
import com.chemmanager.model.distribution.DeliveryRoute;
import com.chemmanager.model.distribution.Driver;
import com.chemmanager.model.distribution.Vehicle;
import com.chemmanager.service.DistributionService;

/**
 * Demo fleet and tours, used when the backend is not reachable.
 *
 * <p>The three tours cover the states the screen has to explain: one waiting in the yard, one on the
 * road with a stop still to serve, and one closed the day before yesterday. The actions walk them
 * through the same transitions the server enforces.</p>
 */
public final class MockDistributionService implements DistributionService {

    private static final AtomicLong SEQUENCE = new AtomicLong(40);
    private static final List<Vehicle> VEHICLES = new ArrayList<>();
    private static final List<Driver> DRIVERS = List.of(
            new Driver(1, "EMP-0001", "Karim Belkacem", "Chauffeur livreur", "0661 22 44 88"),
            new Driver(2, "EMP-0002", "Rachid Haddad", "Chauffeur livreur", "0770 31 22 09"));
    private static final List<DeliveryRoute> ROUTES = new ArrayList<>();

    static {
        VEHICLES.add(new Vehicle(1, "19-2145-116", "Renault Master", bd("3500.00"), 1L, "Karim Belkacem",
                "0661 22 44 88", true, 6, LocalDate.now().minusDays(1), 1));
        VEHICLES.add(new Vehicle(2, "19-3320-117", "Isuzu NQR", bd("7500.00"), 2L, "Rachid Haddad",
                "0770 31 22 09", true, 4, LocalDate.now().minusDays(2), 1));
        VEHICLES.add(new Vehicle(3, "19-4471-118", "Toyota Dyna", bd("2500.00"), null, "", "", false, 0, null, 0));

        ROUTES.add(new DeliveryRoute(1, "TR-00003", LocalDate.now(), 1, "19-2145-116", "Renault Master", 1L,
                "Karim Belkacem", "Yacine Driouech", "Sétif", "PLANNED", bd("0.00"), null, null, 2, 0,
                bd("30.000"),
                List.of(stop(1, 1, "PENDING", null, "", 3, "BL-00003", "PREPARED", "",
                                "Épicerie Amine", "CL-0003", "Sétif", "0770 12 34 56",
                                "12 rue de la Gare, Sétif", bd("12.000"), 1),
                        stop(2, 2, "PENDING", null, "", 4, "BL-00004", "PREPARED", "",
                                "Lycée El Hidhab", "CL-0005", "Sétif", "0661 45 78 90",
                                "Cité El Hidhab, Sétif", bd("18.000"), 2))));
        ROUTES.add(new DeliveryRoute(2, "TR-00004", LocalDate.now().minusDays(1), 2, "19-3320-117", "Isuzu NQR",
                2L, "Rachid Haddad", "Yacine Driouech", "Béjaïa", "IN_TRANSIT", bd("0.00"),
                LocalDateTime.now().minusHours(6), null, 3, 1, bd("96.000"),
                List.of(stop(3, 1, "DELIVERED", LocalDateTime.now().minusHours(5), "Remis au magasinier",
                                5, "BL-00005", "DELIVERED", "M. Saïdi", "Hôtel Atlas", "CL-0002", "Béjaïa",
                                "034 22 11 33", "Route de la plage, Béjaïa", bd("24.000"), 2),
                        stop(4, 2, "PENDING", null, "", 6, "BL-00006", "IN_TRANSIT", "",
                                "Supérette El Baraka", "CL-0001", "Béjaïa", "034 45 67 89",
                                "Centre-ville, Béjaïa", bd("36.000"), 3),
                        stop(5, 3, "PENDING", null, "", 7, "BL-00007", "IN_TRANSIT", "",
                                "Grossiste Oued Smar", "CL-0004", "Béjaïa", "0550 78 90 12",
                                "Zone industrielle, Béjaïa", bd("36.000"), 2))));
        ROUTES.add(new DeliveryRoute(3, "TR-00002", LocalDate.now().minusDays(2), 1, "19-2145-116",
                "Renault Master", 1L, "Karim Belkacem", "Yacine Driouech", "M'Sila", "COMPLETED",
                bd("4500.00"), LocalDateTime.now().minusDays(2).withHour(8), LocalDateTime.now().minusDays(2)
                        .withHour(17), 1, 1, bd("12.000"),
                List.of(stop(6, 1, "DELIVERED", LocalDateTime.now().minusDays(2).withHour(11), "",
                                1, "BL-00001", "DELIVERED", "M. Belkacem", "Hôtel Atlas", "CL-0002", "M'Sila",
                                "035 11 22 33", "Cité 500 logements, M'Sila", bd("12.000"), 1))));
    }

    // --------------------------------------------------------------------- fleet

    @Override
    public List<Vehicle> vehicles() {
        return List.copyOf(VEHICLES);
    }

    @Override
    public Vehicle createVehicle(NewVehicle request) {
        String plate = request.plate() == null ? "" : request.plate().trim().toUpperCase(Locale.ROOT);
        if (VEHICLES.stream().anyMatch(vehicle -> vehicle.plate().equalsIgnoreCase(plate))) {
            throw new IllegalArgumentException("A vehicle with this plate already exists.");
        }
        Vehicle created = new Vehicle(SEQUENCE.incrementAndGet(), plate, request.model() == null ? "" : request.model(),
                request.capacityKg(), request.driverId(), driverName(request.driverId()), driverPhone(request.driverId()),
                true, 0, null, 0);
        VEHICLES.add(created);

        return created;
    }

    @Override
    public Vehicle updateVehicle(long id, NewVehicle request) {
        Vehicle current = VEHICLES.stream().filter(vehicle -> vehicle.id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown vehicle " + id));
        Vehicle updated = new Vehicle(current.id(), request.plate() == null ? current.plate()
                : request.plate().trim().toUpperCase(Locale.ROOT),
                request.model() == null ? current.model() : request.model(),
                request.capacityKg() == null ? current.capacityKg() : request.capacityKg(),
                request.driverId(), driverName(request.driverId()), driverPhone(request.driverId()),
                request.active(), current.routeCount(), current.lastRouteDate(), current.openRoutes());
        VEHICLES.replaceAll(vehicle -> vehicle.id() == id ? updated : vehicle);

        return updated;
    }

    @Override
    public List<Driver> drivers() {
        return DRIVERS;
    }

    // --------------------------------------------------------------------- tours

    @Override
    public PagedResult<DeliveryRoute> routes(RouteQuery query) {
        RouteQuery effective = query == null ? RouteQuery.initial(25) : query;
        String needle = effective.search().toLowerCase(Locale.ROOT);
        List<DeliveryRoute> matches = ROUTES.stream()
                .filter(route -> effective.status() == null || effective.status().isBlank()
                        || effective.status().equalsIgnoreCase(route.status()))
                .filter(route -> effective.routeDate() == null
                        || effective.routeDate().equals(route.routeDate()))
                .filter(route -> needle.isEmpty()
                        || (route.routeNumber() + " " + route.plate() + " " + route.wilayaZone())
                                .toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        int from = Math.min(matches.size(), effective.page() * effective.pageSize());
        int to = Math.min(matches.size(), from + effective.pageSize());

        return new PagedResult<>(matches.subList(from, to), effective.page(), effective.pageSize(),
                matches.size());
    }

    @Override
    public DeliveryRoute route(long id) {
        return ROUTES.stream().filter(route -> route.id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tour " + id));
    }

    @Override
    public DeliveryRoute createRoute(NewRoute request) {
        if (request.deliveryNoteIds().isEmpty()) {
            throw new IllegalArgumentException("A tour needs at least one delivery note.");
        }
        long id = SEQUENCE.incrementAndGet();
        List<DeliveryRoute.Stop> stops = new ArrayList<>();
        int sequence = 1;
        for (Long noteId : request.deliveryNoteIds()) {
            stops.add(stop(SEQUENCE.incrementAndGet(), sequence++, "PENDING", null, "", noteId,
                    String.format("BL-%05d", noteId), "PREPARED", "", "Client " + noteId, "CL-0000", "",
                    "", "", bd("0.000"), 1));
        }
        Vehicle vehicle = VEHICLES.stream().filter(row -> row.id() == request.vehicleId()).findFirst()
                .orElse(VEHICLES.get(0));
        DeliveryRoute created = new DeliveryRoute(id, String.format("TR-%05d", id % 100000),
                request.routeDate() == null ? LocalDate.now() : request.routeDate(), vehicle.id(),
                vehicle.plate(), vehicle.model(), request.driverId(), driverName(request.driverId()),
                "Yacine Driouech", request.wilayaZone() == null ? "" : request.wilayaZone(), "PLANNED",
                BigDecimal.ZERO, null, null, stops.size(), 0,
                stops.stream().map(DeliveryRoute.Stop::totalQuantity).reduce(BigDecimal.ZERO, BigDecimal::add),
                stops);
        ROUTES.add(0, created);

        return created;
    }

    @Override
    public DeliveryRoute loadRoute(long id) {
        return replace(route(id), "LOADING", null, null, null);
    }

    @Override
    public DeliveryRoute startRoute(long id) {
        return replace(route(id), "IN_TRANSIT", LocalDateTime.now(), null, null);
    }

    @Override
    public DeliveryRoute deliverStop(long routeId, long stopId, StopOutcome outcome) {
        DeliveryRoute current = route(routeId);
        if (!current.inTransit()) {
            throw new IllegalStateException("This tour is " + current.status() + "; the action needs IN_TRANSIT.");
        }
        String status = outcome.status() == null ? "DELIVERED" : outcome.status().toUpperCase(Locale.ROOT);
        List<DeliveryRoute.Stop> stops = new ArrayList<>();
        boolean found = false;
        for (DeliveryRoute.Stop stop : current.stops()) {
            if (stop.id() == stopId) {
                found = true;
                stops.add(new DeliveryRoute.Stop(stop.id(), stop.sequence(), status, LocalDateTime.now(),
                        outcome.remarks() == null ? "" : outcome.remarks(), stop.deliveryNoteId(),
                        stop.noteNumber(), status, outcome.signedBy() == null ? "" : outcome.signedBy(),
                        stop.customerName(), stop.customerCode(), stop.customerWilaya(), stop.customerPhone(),
                        stop.customerAddress(), stop.totalQuantity(), stop.lineCount()));
            } else {
                stops.add(stop);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown stop " + stopId);
        }
        int delivered = (int) stops.stream().filter(stop -> !stop.pending()).count();
        DeliveryRoute updated = new DeliveryRoute(current.id(), current.routeNumber(), current.routeDate(),
                current.vehicleId(), current.plate(), current.model(), current.driverId(), current.driverName(),
                current.representativeName(), current.wilayaZone(), current.status(), current.fuelCost(),
                current.openedAt(), current.closedAt(), stops.size(), delivered, current.totalQuantity(), stops);
        ROUTES.replaceAll(route -> route.id() == routeId ? updated : route);

        return updated;
    }

    @Override
    public DeliveryRoute completeRoute(long id, BigDecimal fuelCost) {
        return replace(route(id), "COMPLETED", null, LocalDateTime.now(), fuelCost);
    }

    @Override
    public DeliveryRoute cancelRoute(long id) {
        return replace(route(id), "CANCELLED", null, LocalDateTime.now(), null);
    }

    // ------------------------------------------------------------------- helpers

    private DeliveryRoute replace(DeliveryRoute current, String status, LocalDateTime opened,
                                  LocalDateTime closed, BigDecimal fuelCost) {
        DeliveryRoute updated = new DeliveryRoute(current.id(), current.routeNumber(), current.routeDate(),
                current.vehicleId(), current.plate(), current.model(), current.driverId(), current.driverName(),
                current.representativeName(), current.wilayaZone(), status,
                fuelCost == null ? current.fuelCost() : fuelCost,
                opened == null ? current.openedAt() : opened,
                closed == null ? current.closedAt() : closed,
                current.stopCount(), current.deliveredCount(), current.totalQuantity(), current.stops());
        ROUTES.replaceAll(route -> route.id() == current.id() ? updated : route);

        return updated;
    }

    private static String driverName(Long driverId) {
        if (driverId == null) {
            return "";
        }

        return DRIVERS.stream().filter(driver -> driver.id() == driverId).findFirst()
                .map(Driver::fullName).orElse("");
    }

    private static String driverPhone(Long driverId) {
        if (driverId == null) {
            return "";
        }

        return DRIVERS.stream().filter(driver -> driver.id() == driverId).findFirst()
                .map(Driver::phone).orElse("");
    }

    private static DeliveryRoute.Stop stop(long id, int sequence, String status, LocalDateTime arrivedAt,
                                           String remarks, long noteId, String noteNumber, String noteStatus,
                                           String signedBy, String customerName, String customerCode,
                                           String wilaya, String phone, String address, BigDecimal quantity,
                                           int lineCount) {
        return new DeliveryRoute.Stop(id, sequence, status, arrivedAt, remarks, noteId, noteNumber, noteStatus,
                signedBy, customerName, customerCode, wilaya, phone, address, quantity, lineCount);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
