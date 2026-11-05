package com.chemmanager.controller.users;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.chemmanager.app.AppContext;
import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.Icons;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.User;
import com.chemmanager.security.Permission;
import com.chemmanager.security.Role;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.ExcelExporter;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;
import com.chemmanager.util.Paging;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * Users &amp; permissions: user accounts with their role, and the role/permission matrix that
 * drives navigation and (authoritatively) the PHP API guards. Phase 1 is read-only; credential
 * management and per-action editing arrive with the administration phase.
 */
public final class UsersController {

    @FXML private VBox tableHost;
    @FXML private VBox rolesHost;
    @FXML private Label summaryLabel;
    @FXML private Button refreshButton;
    @FXML private Button exportButton;

    private DataTable<User> table;
    private List<User> users = List.of();

    @FXML
    private void initialize() {

        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> load());
        exportButton.setGraphic(Icons.glyph(Icons.Icon.EXCEL, 12));
        exportButton.setOnAction(event -> export());

        table = new DataTable<>(this::page, I18n.get().t("users.search_prompt"))
                .columns(
                        DataTable.Column.text(I18n.get().t("user.full_name"), 165, User::fullName),
                        DataTable.Column.key(I18n.get().t("login.username"), 100, User::username),
                        DataTable.Column.node(I18n.get().t("user.role"), 165,
                                user -> StatusBadge.primary(Labels.get("role." + user.primaryRoleCode().toLowerCase(),
                                        user.primaryRoleCode()))),
                        DataTable.Column.text(I18n.get().t("user.job_title"), 120, User::jobTitle),
                        DataTable.Column.text(I18n.get().t("user.last_login"), 110,
                                user -> Formatters.dateTime(user.lastLogin())),
                        DataTable.Column.node(I18n.get().t("common.status"), 75,
                                user -> StatusBadge.active(user.active())))
                .onActivate(this::showDetail);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        tableHost.getChildren().setAll(table);
        load();
    }

    private void load() {
        FxTasks.run(() -> ServiceFactory.users().users(), loaded -> {
            users = loaded;
            table.resetToFirstPage();
            summaryLabel.setText(I18n.get().t("users.summary", loaded.size()));
            loadRoles();
        }, error -> Toasts.error(I18n.get().t("error.loading")));
    }

    private PagedResult<User> page(String search, int page, int pageSize) {
        String needle = search == null ? "" : search.trim().toLowerCase();
        List<User> filtered = users.stream()
                .filter(user -> needle.isEmpty()
                        || user.fullName().toLowerCase().contains(needle)
                        || user.username().toLowerCase().contains(needle)
                        || user.email().toLowerCase().contains(needle)
                        || user.primaryRoleCode().toLowerCase().contains(needle))
                .toList();
        return Paging.slice(filtered, page, pageSize);
    }

    private void loadRoles() {
        FxTasks.run(() -> ServiceFactory.users().roles(), roles -> {
            rolesHost.getChildren().clear();
            for (Role role : roles) {
                rolesHost.getChildren().add(roleCard(role));
            }
        }, error -> Toasts.error(I18n.get().t("error.loading")));
    }

    private Card roleCard(Role role) {
        Card card = new Card(Labels.get(role.nameKey(), role.code()),
                I18n.get().t("role.permission_count", role.permissions().size()));
        card.setPrefWidth(330);
        FlowPane chips = new FlowPane(6, 6);
        role.permissions().stream().sorted().limit(14).forEach(permission ->
                chips.getChildren().add(StatusBadge.neutral(shortPermission(permission))));
        if (role.permissions().size() > 14) {
            chips.getChildren().add(StatusBadge.primary("+" + (role.permissions().size() - 14)));
        }
        card.body(chips);
        return card;
    }

    private String shortPermission(Permission permission) {
        String translated = I18n.get().t(permission.labelKey());
        if (!translated.startsWith("!")) {
            return translated;
        }
        return Labels.humanize(permission.action().name());
    }

    private void showDetail(User user) {
        String message = String.join("\n",
                I18n.get().t("user.detail.header", user.fullName(), user.username()),
                I18n.get().t("user.detail.contact", user.email(), user.phone()),
                I18n.get().t("user.detail.role", user.roleCodes(), user.jobTitle()),
                I18n.get().t("user.detail.last_login", Formatters.dateTime(user.lastLogin())),
                I18n.get().t("user.detail.status", user.active()
                        ? I18n.get().t("common.active") : I18n.get().t("common.inactive")));
        Dialogs.info(AppContext.get().stage(), I18n.get().t("user.detail.title"), message);
    }

    private void export() {
        if (users.isEmpty()) {
            Toasts.warning(I18n.get().t("dashboard.export_empty"));
            return;
        }
        Optional<Path> target = Dialogs.chooseSaveFile(AppContext.get().stage(),
                I18n.get().t("users.export"), "users.xlsx", I18n.get().t("dashboard.export_filter"), "xlsx");
        if (target.isEmpty()) {
            return;
        }
        List<String> headers = List.of(I18n.get().t("user.full_name"), I18n.get().t("login.username"),
                I18n.get().t("user.role"), I18n.get().t("user.job_title"), I18n.get().t("user.email"),
                I18n.get().t("user.phone"), I18n.get().t("user.last_login"));
        List<List<Object>> rows = users.stream()
                .map(user -> List.<Object>of(user.fullName(), user.username(), user.roleCodes(),
                        user.jobTitle(), user.email(), user.phone(),
                        user.lastLogin() == null ? "" : user.lastLogin().toString()))
                .toList();
        Path file = target.get();
        FxTasks.runVoid(() -> ExcelExporter.exportRows(file, I18n.get().t("nav.users"), headers, rows),
                () -> Toasts.success(I18n.get().t("dashboard.export_done", file.getFileName().toString())),
                error -> Toasts.error(I18n.get().t("dashboard.export_failed", String.valueOf(error.getMessage()))));
    }
}
