package com.chemmanager.controller.distribution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.FormFields;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.RouteQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.distribution.DeliveryRoute;
import com.chemmanager.model.distribution.Driver;
import com.chemmanager.model.distribution.Vehicle;
import com.chemmanager.model.sales.DeliveryNote;
import com.chemmanager.dto.SalesQuery;
import com.chemmanager.service.DistributionService;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * The fleet and the daily tours.
 *
 * <p>A tour is a truck, a zone and the delivery notes loaded on it, served in sequence. The screen
 * shows the notes each stop carries, and lets the driver's office sign a stop, refuse it, or close
 * the day — the same three decisions the server accepts while a tour is on the road.</p>
 */
public final class DistributionController {

    private static final String ROUTES = "routes";
    private static final String FLEET = "fleet";
    private static final List<String> VIEWS = List.of(ROUTES, FLEET);
    private static final List<String> STATUSES = List.of("PLANNED", "LOADING", "IN_TRANSIT", "COMPLETED",
            "CANCELLED");

    @FXML private VBox tableHost;
    @FXML private VBox detailHost;
    @FXML private HBox actionBar;
    @FXML private Label summaryLabel;
    @FXML private ComboBox<String> viewSelector;
    @FXML private ComboBox<String> statusFilter;
    @FXML private DatePicker dateFilter;
    @FXML private Button newButton;
    @FXML private Button refreshButton;

    private final Map<String, List<Button>> actions = new LinkedHashMap<>();

    private DataTable<DeliveryRoute> routeTable;
    private DataTable<Vehicle> fleetTable;

    private RouteQuery query = RouteQuery.initial(25);
    private String view = ROUTES;
    private DeliveryRoute selectedRoute;
    private Vehicle selectedVehicle;

    @FXML
    private void initialize() {
        buildTables();
        buildActions();

        viewSelector.setItems(FXCollections.observableArrayList(VIEWS));
        viewSelector.setButtonCell(viewCell());
        viewSelector.setCellFactory(list -> viewCell());
        viewSelector.getSelectionModel().select(ROUTES);
        viewSelector.setOnAction(event -> {
            String value = viewSelector.getValue();
            if (value != null && !value.equals(view)) {
                showView(value);
            }
        });

        statusFilter.setButtonCell(statusCell());
        statusFilter.setCellFactory(list -> statusCell());
        statusFilter.setOnAction(event -> {
            String value = statusFilter.getValue();
            query = query.withStatus(value == null ? "" : value).withPage(0);
            routeTable.resetToFirstPage();
        });
        dateFilter.setOnAction(event -> {
            query = query.withDate(dateFilter.getValue()).withPage(0);
            routeTable.resetToFirstPage();
        });
        newButton.setOnAction(event -> {
            if (ROUTES.equals(view)) {
                newRoute();
            } else {
                openVehicleDialog(null);
            }
        });
        refreshButton.setOnAction(event -> reload());

        showView(ROUTES);
    }

    // ------------------------------------------------------------------- tables

    private void buildTables() {
        routeTable = new DataTable<>(this::loadRoutes, I18n.get().t("distribution.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("distribution.column.number"), 115,
                                DeliveryRoute::routeNumber),
                        DataTable.Column.date(I18n.get().t("distribution.column.date"), 110,
                                DeliveryRoute::routeDate),
                        DataTable.Column.key(I18n.get().t("distribution.column.plate"), 120,
                                DeliveryRoute::plate),
                        DataTable.Column.text(I18n.get().t("distribution.column.driver"), 175,
                                route -> route.driverName() == null || route.driverName().isBlank()
                                        ? "—" : route.driverName()),
                        DataTable.Column.text(I18n.get().t("distribution.column.zone"), 110,
                                DeliveryRoute::wilayaZone),
                        DataTable.Column.text(I18n.get().t("distribution.column.stops"), 105,
                                route -> route.deliveredCount() + " / " + route.stopCount()),
                        DataTable.Column.quantity(I18n.get().t("distribution.column.quantity"), 110,
                                route -> Formatters.quantity(route.totalQuantity(), "")),
                        DataTable.Column.node(I18n.get().t("distribution.column.status"), 125,
                                route -> badge(Labels.routeStatus(route.status()))))
                .onActivate(this::selectRoute)
                .onLoaded(page -> {
                    summaryLabel.setText(I18n.get().t("distribution.summary.routes", page.total()));
                    if (page.items().isEmpty()) {
                        selectedRoute = null;
                        showHint();
                        updateActions();
                    } else if (selectedRoute == null || page.items().stream()
                            .noneMatch(route -> route.id() == selectedRoute.id())) {
                        selectRoute(page.items().get(0));
                    }
                });

        fleetTable = new DataTable<>((search, page, pageSize) -> {
            List<Vehicle> matches = ServiceFactory.distribution().vehicles().stream()
                    .filter(vehicle -> search == null || search.isBlank()
                            || (vehicle.plate() + " " + vehicle.model() + " " + vehicle.driver())
                                    .toLowerCase(java.util.Locale.ROOT)
                                    .contains(search.toLowerCase(java.util.Locale.ROOT)))
                    .toList();
            return new PagedResult<>(matches, page, pageSize, matches.size());
        }, I18n.get().t("distribution.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("distribution.column.plate"), 130,
                                Vehicle::plate),
                        DataTable.Column.text(I18n.get().t("distribution.column.model"), 175,
                                Vehicle::model),
                        DataTable.Column.quantity(I18n.get().t("distribution.column.capacity"), 120,
                                vehicle -> vehicle.capacityKg().toPlainString() + " kg"),
                        DataTable.Column.text(I18n.get().t("distribution.column.driver"), 180,
                                Vehicle::driver),
                        DataTable.Column.text(I18n.get().t("distribution.column.routes"), 100,
                                vehicle -> Integer.toString(vehicle.routeCount())),
                        DataTable.Column.node(I18n.get().t("distribution.column.status"), 110,
                                vehicle -> StatusBadge.of(
                                        I18n.get().t(vehicle.active()
                                                ? "distribution.vehicle.active" : "distribution.vehicle.inactive"),
                                        vehicle.active() ? "badge-success" : "badge-neutral")))
                .onActivate(this::selectVehicle)
                .onLoaded(page -> summaryLabel.setText(I18n.get().t("distribution.summary.fleet",
                        page.total())));

        VBox.setVgrow(routeTable, Priority.ALWAYS);
        VBox.setVgrow(fleetTable, Priority.ALWAYS);
        fleetTable.setVisible(false);
        fleetTable.setManaged(false);
        tableHost.getChildren().setAll(routeTable, fleetTable);
    }

    private void buildActions() {
        actions.put(ROUTES, new ArrayList<>(List.of(
                action("loadButton", "distribution.action.load", this::loadRoute),
                action("startButton", "distribution.action.start", this::startRoute),
                action("completeButton", "distribution.action.complete", this::completeRoute),
                action("cancelRouteButton", "distribution.action.cancel", this::cancelRoute))));
        actions.put(FLEET, new ArrayList<>(List.of(
                action("editVehicleButton", "distribution.action.edit", () -> openVehicleDialog(selectedVehicle)),
                action("toggleVehicleButton", "distribution.action.toggle", this::toggleVehicle))));
    }

    private Button action(String id, String key, Runnable handler) {
        Button button = new Button(I18n.get().t(key));
        button.setId(id);
        button.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        button.setDisable(true);
        button.setOnAction(event -> handler.run());

        return button;
    }

    private void showView(String value) {
        view = value;
        selectedRoute = null;
        selectedVehicle = null;
        boolean routes = ROUTES.equals(view);
        routeTable.setVisible(routes);
        routeTable.setManaged(routes);
        fleetTable.setVisible(!routes);
        fleetTable.setManaged(!routes);
        actionBar.getChildren().setAll(actions.get(view));
        statusFilter.setDisable(!routes);
        dateFilter.setDisable(!routes);
        newButton.setText(I18n.get().t(routes ? "distribution.action.new_route"
                : "distribution.action.new_vehicle"));
        showHint();
        updateActions();
        DataTable<?> active = routes ? routeTable : fleetTable;
        active.resetToFirstPage();
    }

    private void reload() {
        if (ROUTES.equals(view)) {
            routeTable.reload();
        } else {
            fleetTable.reload();
        }
    }

    private PagedResult<DeliveryRoute> loadRoutes(String search, int page, int pageSize) {
        query = query.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.distribution().routes(query);
    }

    // ---------------------------------------------------------------- selection

    private void selectRoute(DeliveryRoute route) {
        selectedRoute = route;
        selectedVehicle = null;
        updateActions();
        long id = route.id();
        FxTasks.run(() -> ServiceFactory.distribution().route(id), loaded -> {
            if (selectedRoute != null && selectedRoute.id() == id) {
                selectedRoute = loaded;
                updateActions();
                renderRoute(loaded);
            }
        }, error -> Toasts.error(message(error)));
    }

    private void selectVehicle(Vehicle vehicle) {
        selectedVehicle = vehicle;
        selectedRoute = null;
        updateActions();
        renderVehicle(vehicle);
    }

    private void updateActions() {
        List<Button> buttons = actions.get(view);
        if (buttons == null) {
            return;
        }
        for (Button button : buttons) {
            button.setDisable(disabled(button.getId()));
        }
    }

    private boolean disabled(String id) {
        if (ROUTES.equals(view)) {
            if (selectedRoute == null) {
                return true;
            }
            return switch (id) {
                case "loadButton" -> !selectedRoute.loadable();
                case "startButton" -> !selectedRoute.startable();
                case "completeButton" -> !selectedRoute.completable();
                case "cancelRouteButton" -> !selectedRoute.cancellable();
                default -> false;
            };
        }

        return selectedVehicle == null;
    }

    // ------------------------------------------------------------------- detail

    private void showHint() {
        Label hint = new Label(I18n.get().t(ROUTES.equals(view) ? "distribution.detail.hint"
                : "distribution.detail.fleet_hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        detailHost.getChildren().setAll(hint);
    }

    private void renderRoute(DeliveryRoute route) {
        Card card = new Card(route.routeNumber() + " · " + route.wilayaZone(),
                route.plate() + (route.model() == null || route.model().isBlank() ? "" : " · " + route.model()));
        card.action(badge(Labels.routeStatus(route.status())));
        card.body(new VBox(6,
                fact("distribution.field.date", date(route.routeDate())),
                fact("distribution.field.driver", route.driverName()),
                fact("distribution.field.representative", route.representativeName()),
                fact("distribution.field.stops", route.deliveredCount() + " / " + route.stopCount()),
                fact("distribution.field.progress", Formatters.percent(BigDecimal.valueOf(route.progress()))),
                fact("distribution.field.quantity", Formatters.quantity(route.totalQuantity(), "")),
                fact("distribution.field.fuel", Formatters.currency(route.fuelCost()))));

        VBox box = new VBox(10, card);
        if (route.inTransit()) {
            Label hint = new Label(I18n.get().t("distribution.detail.on_the_road"));
            hint.getStyleClass().add("text-muted");
            hint.setWrapText(true);
            box.getChildren().add(hint);
        }
        VBox stops = new VBox(6, FormFields.sectionTitle("distribution.detail.stops"));
        if (route.stops().isEmpty()) {
            Label empty = new Label(I18n.get().t("distribution.detail.no_stops"));
            empty.getStyleClass().add("text-muted");
            stops.getChildren().add(empty);
        }
        for (DeliveryRoute.Stop stop : route.stops()) {
            stops.getChildren().add(stopRow(route, stop));
        }
        box.getChildren().add(stops);
        detailHost.getChildren().setAll(box);
    }

    /** One stop: who to serve, what the note carries, and the two verdicts the driver can report. */
    private Node stopRow(DeliveryRoute route, DeliveryRoute.Stop stop) {
        Label name = new Label(stop.sequence() + ". " + stop.customerName() + " · " + stop.noteNumber()
                + " · " + Formatters.quantity(stop.totalQuantity(), "")
                + (stop.customerWilaya() == null || stop.customerWilaya().isBlank()
                        ? "" : " · " + stop.customerWilaya()));
        name.getStyleClass().add("text-muted");
        name.setWrapText(true);
        HBox.setHgrow(name, Priority.ALWAYS);

        HBox row = new HBox(8, name, StatusBadge.of(stopStatus(stop.status()), stopStyle(stop.status())));
        if (route.inTransit() && stop.pending()) {
            Button sign = new Button(I18n.get().t("distribution.action.sign_stop"));
            sign.setId("signStop" + stop.id());
            sign.getStyleClass().addAll("btn", "btn-primary", "btn-sm");
            sign.setOnAction(event -> settleStop(route, stop, "DELIVERED"));
            Button refuse = new Button(I18n.get().t("distribution.action.refuse_stop"));
            refuse.setId("refuseStop" + stop.id());
            refuse.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
            refuse.setOnAction(event -> settleStop(route, stop, "REFUSED"));
            row.getChildren().addAll(sign, refuse);
        } else if (stop.remarks() != null && !stop.remarks().isBlank()) {
            Label remark = new Label(stop.remarks());
            remark.getStyleClass().add("text-muted");
            row.getChildren().add(remark);
        }

        return row;
    }

    private String stopStatus(String status) {
        return switch (status == null ? "" : status) {
            case "DELIVERED" -> I18n.get().t("distribution.stop.delivered");
            case "PARTIAL" -> I18n.get().t("distribution.stop.partial");
            case "REFUSED" -> I18n.get().t("distribution.stop.refused");
            default -> I18n.get().t("distribution.stop.pending");
        };
    }

    private String stopStyle(String status) {
        return switch (status == null ? "" : status) {
            case "DELIVERED" -> "badge-success";
            case "PARTIAL" -> "badge-warning";
            case "REFUSED" -> "badge-danger";
            default -> "badge-neutral";
        };
    }

    private void renderVehicle(Vehicle vehicle) {
        Card card = new Card(vehicle.plate(), vehicle.model());
        card.action(StatusBadge.of(I18n.get().t(vehicle.active() ? "distribution.vehicle.active"
                : "distribution.vehicle.inactive"), vehicle.active() ? "badge-success" : "badge-neutral"));
        card.body(new VBox(6,
                fact("distribution.field.capacity", vehicle.capacityKg().toPlainString() + " kg"),
                fact("distribution.field.driver", vehicle.driver()),
                fact("distribution.field.phone", vehicle.driverPhone()),
                fact("distribution.field.routes", Integer.toString(vehicle.routeCount())),
                fact("distribution.field.last_route", date(vehicle.lastRouteDate())),
                fact("distribution.field.open_routes", Integer.toString(vehicle.openRoutes()))));
        VBox box = new VBox(10, card);
        if (!vehicle.deactivatable() && vehicle.active()) {
            Label warn = new Label(I18n.get().t("distribution.warning.open_route"));
            warn.getStyleClass().add("text-muted");
            warn.setWrapText(true);
            box.getChildren().add(StatusBadge.warning(warn.getText()));
        }
        detailHost.getChildren().setAll(box);
    }

    // ------------------------------------------------------------------ actions

    private void loadRoute() {
        if (selectedRoute == null) {
            return;
        }
        DeliveryRoute current = selectedRoute;
        FxTasks.run(() -> ServiceFactory.distribution().loadRoute(current.id()), saved -> {
            Toasts.success(I18n.get().t("distribution.toast.loaded", saved.routeNumber()));
            routeTable.reload();
        }, error -> Toasts.error(message(error)));
    }

    private void startRoute() {
        if (selectedRoute == null) {
            return;
        }
        DeliveryRoute current = selectedRoute;
        FxTasks.run(() -> ServiceFactory.distribution().startRoute(current.id()), saved -> {
            Toasts.success(I18n.get().t("distribution.toast.started", saved.routeNumber()));
            routeTable.reload();
        }, error -> Toasts.error(message(error)));
    }

    private void completeRoute() {
        if (selectedRoute == null) {
            return;
        }
        DeliveryRoute current = selectedRoute;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window());
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("distribution.dialog.complete.title", current.routeNumber()));
        ButtonType close = new ButtonType(I18n.get().t("distribution.action.complete"),
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(close, ButtonType.CANCEL);

        TextField fuel = new TextField(current.fuelCost().toPlainString());
        Label hint = new Label(I18n.get().t("distribution.dialog.complete.hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(10, hint,
                FormFields.field("distribution.field.fuel", fuel)));
        dialog.getDialogPane().setPrefWidth(480);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != close) {
            return;
        }
        BigDecimal cost = Formatters.parseDecimal(fuel.getText());
        FxTasks.run(() -> ServiceFactory.distribution().completeRoute(current.id(), cost), saved -> {
            Toasts.success(I18n.get().t("distribution.toast.completed", saved.routeNumber(),
                    saved.deliveredCount(), saved.stopCount()));
            routeTable.reload();
        }, error -> Toasts.error(message(error)));
    }

    private void cancelRoute() {
        if (selectedRoute == null) {
            return;
        }
        DeliveryRoute current = selectedRoute;
        if (!Dialogs.confirm(window(), I18n.get().t("distribution.action.cancel"),
                I18n.get().t("distribution.confirm.cancel", current.routeNumber()))) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.distribution().cancelRoute(current.id()), saved -> {
            Toasts.success(I18n.get().t("distribution.toast.cancelled", saved.routeNumber()));
            routeTable.reload();
        }, error -> Toasts.error(message(error)));
    }

    private void settleStop(DeliveryRoute route, DeliveryRoute.Stop stop, String status) {
        String remarks = "";
        String signedBy = stop.customerName();
        if ("REFUSED".equals(status)) {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(window());
            Dialogs.applyTheme(dialog.getDialogPane());
            dialog.setTitle(I18n.get().t("distribution.dialog.refuse.title", stop.noteNumber()));
            ButtonType confirm = new ButtonType(I18n.get().t("distribution.action.refuse_stop"),
                    ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(confirm, ButtonType.CANCEL);
            TextField reason = new TextField();
            dialog.getDialogPane().setContent(new VBox(10,
                    new Label(I18n.get().t("distribution.dialog.refuse.hint")),
                    FormFields.field("distribution.field.reason", reason)));
            dialog.getDialogPane().setPrefWidth(480);
            Optional<ButtonType> answer = dialog.showAndWait();
            if (answer.isEmpty() || answer.get() != confirm) {
                return;
            }
            if (reason.getText() == null || reason.getText().isBlank()) {
                Toasts.warning(I18n.get().t("validation.required"));
                return;
            }
            remarks = reason.getText().trim();
        }
        DeliveryRoute current = route;
        DistributionService.StopOutcome outcome = new DistributionService.StopOutcome(status, signedBy, remarks);
        FxTasks.run(() -> ServiceFactory.distribution().deliverStop(current.id(), stop.id(), outcome), saved -> {
            Toasts.success(I18n.get().t("distribution.toast.stop_settled", stop.noteNumber(),
                    stopStatus(status)));
            routeTable.reload();
            selectRoute(saved);
        }, error -> Toasts.error(message(error)));
    }

    private void toggleVehicle() {
        if (selectedVehicle == null) {
            return;
        }
        Vehicle current = selectedVehicle;
        if (!current.active() && current.openRoutes() > 0) {
            Toasts.warning(I18n.get().t("distribution.warning.open_route"));
            return;
        }
        DistributionService.NewVehicle request = new DistributionService.NewVehicle(current.plate(),
                current.model(), current.capacityKg(), current.driverId(), !current.active());
        FxTasks.run(() -> ServiceFactory.distribution().updateVehicle(current.id(), request), saved -> {
            Toasts.success(I18n.get().t(saved.active() ? "distribution.toast.vehicle_active"
                    : "distribution.toast.vehicle_inactive", saved.plate()));
            reload();
            selectVehicle(saved);
        }, error -> Toasts.error(message(error)));
    }

    // ------------------------------------------------------------------ dialogs

    private void openVehicleDialog(Vehicle existing) {
        boolean creating = existing == null;
        List<Driver> drivers = ServiceFactory.distribution().drivers();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window());
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t(creating ? "distribution.dialog.vehicle.title"
                : "distribution.dialog.vehicle.edit"));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField plate = new TextField(creating ? "" : existing.plate());
        TextField model = new TextField(creating ? "" : existing.model());
        TextField capacity = new TextField(creating ? "3500" : existing.capacityKg().toPlainString());
        ComboBox<Driver> driver = new ComboBox<>(FXCollections.observableArrayList(drivers));
        driver.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Driver item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.fullName() + " · " + item.phone());
            }
        });
        driver.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Driver item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.fullName());
            }
        });
        if (!creating && existing.driverId() != null) {
            drivers.stream().filter(row -> row.id() == existing.driverId()).findFirst()
                    .ifPresent(driver.getSelectionModel()::select);
        } else if (!drivers.isEmpty()) {
            driver.getSelectionModel().selectFirst();
        }
        CheckBox active = new CheckBox(I18n.get().t("distribution.field.active"));
        active.setSelected(creating || existing.active());

        VBox content = new VBox(12,
                FormFields.row(FormFields.fieldKey("distribution.field.plate", plate,
                                "distribution.dialog.vehicle.plate_hint"),
                        FormFields.field("distribution.field.model", model)),
                FormFields.row(FormFields.field("distribution.field.capacity", capacity),
                        FormFields.field("distribution.field.driver", driver)),
                active);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(560);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return;
        }
        if (plate.getText() == null || plate.getText().isBlank()) {
            Toasts.warning(I18n.get().t("validation.required"));
            return;
        }
        Driver chosen = driver.getValue();
        DistributionService.NewVehicle request = new DistributionService.NewVehicle(plate.getText().trim(),
                model.getText(), Formatters.parseDecimal(capacity.getText()),
                chosen == null ? null : chosen.id(), active.isSelected());

        FxTasks.run(() -> creating
                        ? ServiceFactory.distribution().createVehicle(request)
                        : ServiceFactory.distribution().updateVehicle(existing.id(), request),
                saved -> {
                    Toasts.success(I18n.get().t(creating ? "distribution.toast.vehicle_created"
                            : "distribution.toast.vehicle_updated", saved.plate()));
                    reload();
                    selectVehicle(saved);
                },
                error -> Toasts.error(message(error)));
    }

    private void newRoute() {
        List<Vehicle> vehicles = ServiceFactory.distribution().vehicles().stream()
                .filter(Vehicle::active).toList();
        List<Driver> drivers = ServiceFactory.distribution().drivers();
        List<DeliveryNote> prepared = ServiceFactory.sales()
                .deliveries(SalesQuery.initial(100).withStatus("PREPARED")).items().stream()
                .filter(note -> note.routeNumber() == null || note.routeNumber().isBlank())
                .toList();
        if (vehicles.isEmpty()) {
            Toasts.warning(I18n.get().t("distribution.warning.no_vehicle"));
            return;
        }
        if (prepared.isEmpty()) {
            Toasts.warning(I18n.get().t("distribution.warning.no_note"));
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window());
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("distribution.dialog.route.title"));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        DatePicker date = new DatePicker(LocalDate.now());
        ComboBox<Vehicle> vehicle = new ComboBox<>(FXCollections.observableArrayList(vehicles));
        vehicle.getSelectionModel().selectFirst();
        vehicle.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Vehicle item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.plate() + " · " + item.model() + " · " + item.capacityKg().toPlainString() + " kg");
            }
        });
        vehicle.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Vehicle item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.plate());
            }
        });
        ComboBox<Driver> driver = new ComboBox<>(FXCollections.observableArrayList(drivers));
        driver.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Driver item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.fullName());
            }
        });
        driver.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Driver item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.fullName());
            }
        });
        if (!drivers.isEmpty()) {
            driver.getSelectionModel().selectFirst();
        }
        TextField zone = new TextField();
        ListView<DeliveryNote> notes = new ListView<>(FXCollections.observableArrayList(prepared));
        notes.setPrefHeight(180);
        notes.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        notes.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(DeliveryNote item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.noteNumber() + " · " + item.customerName() + " · "
                                + Formatters.quantity(item.totalQuantity(), ""));
            }
        });

        VBox content = new VBox(12,
                FormFields.row(FormFields.field("distribution.field.date", date),
                        FormFields.field("distribution.field.vehicle", vehicle)),
                FormFields.row(FormFields.field("distribution.field.driver", driver),
                        FormFields.fieldKey("distribution.field.zone", zone,
                                "distribution.dialog.route.zone_hint")),
                FormFields.field("distribution.field.notes", notes));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(660);
        dialog.getDialogPane().setPrefHeight(560);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return;
        }
        List<Long> noteIds = notes.getSelectionModel().getSelectedItems().stream()
                .map(DeliveryNote::id).toList();
        if (noteIds.isEmpty()) {
            Toasts.warning(I18n.get().t("distribution.warning.no_note"));
            return;
        }
        Vehicle chosen = vehicle.getValue();
        Driver chosenDriver = driver.getValue();
        DistributionService.NewRoute request = new DistributionService.NewRoute(date.getValue(),
                chosen == null ? 0 : chosen.id(), chosenDriver == null ? null : chosenDriver.id(),
                zone.getText(), noteIds);
        FxTasks.run(() -> ServiceFactory.distribution().createRoute(request), created -> {
            Toasts.success(I18n.get().t("distribution.toast.route_created", created.routeNumber(),
                    created.stopCount()));
            routeTable.reload();
            selectRoute(created);
        }, error -> Toasts.error(message(error)));
    }

    // ------------------------------------------------------------------ helpers

    private ListCell<String> viewCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : I18n.get().t("distribution.view." + item));
            }
        };
    }

    private ListCell<String> statusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null || item.isEmpty()
                        ? I18n.get().t("distribution.filter.all") : Labels.routeStatus(item));
            }
        };
    }

    private static Node badge(String text) {
        return StatusBadge.of(text, Labels.commercialStatusStyle(text));
    }

    private static String date(LocalDate value) {
        return value == null ? "—" : Formatters.shortDate(value);
    }

    private Window window() {
        return tableHost.getScene() == null ? null : tableHost.getScene().getWindow();
    }

    private Label fact(String labelKey, String value) {
        Label node = new Label(I18n.get().t(labelKey) + " : "
                + (value == null || value.isBlank() ? "—" : value));
        node.getStyleClass().add("text-muted");
        node.setWrapText(true);

        return node;
    }

    private static String message(Throwable error) {
        String message = error == null ? null : error.getMessage();

        return message == null || message.isBlank() ? I18n.get().t("error.generic") : message;
    }
}
