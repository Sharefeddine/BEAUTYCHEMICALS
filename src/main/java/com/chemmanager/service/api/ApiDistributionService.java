package com.chemmanager.service.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.RouteQuery;
import com.chemmanager.model.distribution.DeliveryRoute;
import com.chemmanager.model.distribution.Driver;
import com.chemmanager.model.distribution.Vehicle;
import com.chemmanager.service.DistributionService;
import com.fasterxml.jackson.databind.JsonNode;

/** The fleet and its tours, served by {@code /api/vehicles}, {@code /api/drivers} and {@code /api/routes}. */
public final class ApiDistributionService implements DistributionService {

    private final ApiClient client;

    public ApiDistributionService(ApiClient client) {
        this.client = client;
    }

    @Override
    public PagedResult<DeliveryRoute> routes(RouteQuery query) {
        RouteQuery effective = query == null ? RouteQuery.initial(25) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search());
        params.put("status", effective.status());
        if (effective.routeDate() != null) {
            params.put("routeDate", effective.routeDate().toString());
        }
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.ROUTES, params, JsonNode.class).map(ApiDistributionService::route);
    }

    @Override
    public DeliveryRoute route(long id) {
        return route(client.get(ApiEndpoints.ROUTES + "/" + id, JsonNode.class));
    }

    @Override
    public List<Vehicle> vehicles() {
        JsonNode rows = client.get(ApiEndpoints.VEHICLES, JsonNode.class);
        List<Vehicle> vehicles = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                vehicles.add(new Vehicle(
                        row.path("id").asLong(),
                        ApiSalesService.text(row, "plate"),
                        ApiSalesService.text(row, "model"),
                        ApiSalesService.decimal(row, "capacityKg"),
                        row.hasNonNull("driverId") ? row.path("driverId").asLong() : null,
                        ApiSalesService.text(row, "driverName"),
                        ApiSalesService.text(row, "driverPhone"),
                        row.path("isActive").asBoolean(true),
                        row.path("routeCount").asInt(),
                        ApiSalesService.date(row, "lastRouteDate"),
                        row.path("openRoutes").asInt()));
            }
        }

        return vehicles;
    }

    @Override
    public Vehicle createVehicle(NewVehicle request) {
        return vehicle(client.post(ApiEndpoints.VEHICLES, vehicleBody(request, false), JsonNode.class));
    }

    @Override
    public Vehicle updateVehicle(long id, NewVehicle request) {
        return vehicle(client.put(ApiEndpoints.VEHICLES + "/" + id, vehicleBody(request, true), JsonNode.class));
    }

    @Override
    public List<Driver> drivers() {
        JsonNode rows = client.get(ApiEndpoints.DRIVERS, JsonNode.class);
        List<Driver> drivers = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                drivers.add(new Driver(row.path("id").asLong(), ApiSalesService.text(row, "employeeNumber"),
                        ApiSalesService.text(row, "fullName"), ApiSalesService.text(row, "position"),
                        ApiSalesService.text(row, "phone")));
            }
        }

        return drivers;
    }

    @Override
    public DeliveryRoute createRoute(NewRoute request) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (request.routeDate() != null) {
            body.put("routeDate", request.routeDate().toString());
        }
        body.put("vehicleId", request.vehicleId());
        if (request.driverId() != null && request.driverId() > 0) {
            body.put("driverId", request.driverId());
        }
        body.put("wilayaZone", request.wilayaZone() == null ? "" : request.wilayaZone());
        body.put("deliveryNoteIds", request.deliveryNoteIds());

        return route(client.post(ApiEndpoints.ROUTES, body, JsonNode.class));
    }

    @Override
    public DeliveryRoute loadRoute(long id) {
        return route(client.post(ApiEndpoints.ROUTES + "/" + id + "/load", Map.of(), JsonNode.class));
    }

    @Override
    public DeliveryRoute startRoute(long id) {
        return route(client.post(ApiEndpoints.ROUTES + "/" + id + "/start", Map.of(), JsonNode.class));
    }

    @Override
    public DeliveryRoute deliverStop(long routeId, long stopId, StopOutcome outcome) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", outcome.status() == null ? "DELIVERED" : outcome.status());
        body.put("signedBy", outcome.signedBy() == null ? "" : outcome.signedBy());
        body.put("remarks", outcome.remarks() == null ? "" : outcome.remarks());

        return route(client.post(ApiEndpoints.ROUTES + "/" + routeId + "/stops/" + stopId + "/deliver", body,
                JsonNode.class));
    }

    @Override
    public DeliveryRoute completeRoute(long id, BigDecimal fuelCost) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fuelCost", fuelCost == null ? BigDecimal.ZERO : fuelCost);

        return route(client.post(ApiEndpoints.ROUTES + "/" + id + "/complete", body, JsonNode.class));
    }

    @Override
    public DeliveryRoute cancelRoute(long id) {
        return route(client.post(ApiEndpoints.ROUTES + "/" + id + "/cancel", Map.of(), JsonNode.class));
    }

    private static Map<String, Object> vehicleBody(NewVehicle request, boolean updating) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plate", request.plate() == null ? "" : request.plate().trim().toUpperCase());
        body.put("model", request.model() == null ? "" : request.model());
        body.put("capacityKg", request.capacityKg() == null ? BigDecimal.ZERO : request.capacityKg());
        if (request.driverId() != null && request.driverId() > 0) {
            body.put("driverId", request.driverId());
        }
        if (updating) {
            body.put("isActive", request.active());
        }

        return body;
    }

    private static Vehicle vehicle(JsonNode node) {
        return new Vehicle(
                node.path("id").asLong(),
                ApiSalesService.text(node, "plate"),
                ApiSalesService.text(node, "model"),
                ApiSalesService.decimal(node, "capacityKg"),
                node.hasNonNull("driverId") ? node.path("driverId").asLong() : null,
                ApiSalesService.text(node, "driverName"),
                ApiSalesService.text(node, "driverPhone"),
                node.path("isActive").asBoolean(true),
                node.path("routeCount").asInt(),
                ApiSalesService.date(node, "lastRouteDate"),
                node.path("openRoutes").asInt());
    }

    static DeliveryRoute route(JsonNode node) {
        List<DeliveryRoute.Stop> stops = new ArrayList<>();
        for (JsonNode row : node.path("stops")) {
            stops.add(new DeliveryRoute.Stop(
                    row.path("id").asLong(),
                    row.path("sequence").asInt(),
                    ApiSalesService.text(row, "status"),
                    dateTime(row, "arrivedAt"),
                    ApiSalesService.text(row, "remarks"),
                    row.path("deliveryNoteId").asLong(),
                    ApiSalesService.text(row, "noteNumber"),
                    ApiSalesService.text(row, "noteStatus"),
                    ApiSalesService.text(row, "signedBy"),
                    ApiSalesService.text(row, "customerName"),
                    ApiSalesService.text(row, "customerCode"),
                    ApiSalesService.text(row, "customerWilaya"),
                    ApiSalesService.text(row, "customerPhone"),
                    ApiSalesService.text(row, "customerAddress"),
                    ApiSalesService.decimal(row, "totalQuantity"),
                    row.path("lineCount").asInt()));
        }

        return new DeliveryRoute(
                node.path("id").asLong(),
                ApiSalesService.text(node, "routeNumber"),
                ApiSalesService.date(node, "routeDate"),
                node.path("vehicleId").asLong(),
                ApiSalesService.text(node, "plate"),
                ApiSalesService.text(node, "model"),
                node.hasNonNull("driverId") ? node.path("driverId").asLong() : null,
                ApiSalesService.text(node, "driverName"),
                ApiSalesService.text(node, "representativeName"),
                ApiSalesService.text(node, "wilayaZone"),
                ApiSalesService.text(node, "status"),
                ApiSalesService.decimal(node, "fuelCost"),
                dateTime(node, "openedAt"),
                dateTime(node, "closedAt"),
                node.path("stopCount").asInt(stops.size()),
                node.path("deliveredCount").asInt(),
                ApiSalesService.decimal(node, "totalQuantity"),
                stops);
    }

    static LocalDateTime dateTime(JsonNode node, String field) {
        LocalDate date = ApiSalesService.date(node, field);
        if (date == null) {
            return null;
        }
        String raw = ApiSalesService.text(node, field);
        try {
            return LocalDateTime.parse(raw.replace(' ', 'T'));
        } catch (RuntimeException exception) {
            return date.atStartOfDay();
        }
    }
}
