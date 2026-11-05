package com.chemmanager.controller;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.chemmanager.app.AppContext;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.Icons;
import com.chemmanager.component.Toasts;
import com.chemmanager.config.AppConfig;
import com.chemmanager.config.Theme;
import com.chemmanager.config.ThemeManager;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.NotificationItem;
import com.chemmanager.model.User;
import com.chemmanager.navigation.NavGroup;
import com.chemmanager.navigation.NavigationManager;
import com.chemmanager.navigation.NavigationModule;
import com.chemmanager.security.Session;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;

import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.fxml.FXML;

/**
 * The application shell: sidebar navigation, header (search, notifications, theme, language,
 * user menu), content host, status bar and toast layer.
 *
 * <p>Navigation is permission driven: a module is only listed when the signed-in user holds
 * the matching {@link com.chemmanager.security.Permission}.</p>
 */
public final class MainWindowController {

    @FXML private VBox sidebar;
    @FXML private VBox navContainer;
    @FXML private ScrollPane navScroll;
    @FXML private HBox brandBox;
    @FXML private Label brandTitle;
    @FXML private Label brandSubtitle;
    @FXML private Label pageTitle;
    @FXML private Label pageSubtitle;
    @FXML private HBox header;
    @FXML private StackPane contentHost;
    @FXML private StackPane overlayLayer;
    @FXML private TextField globalSearch;
    @FXML private Button menuButton;
    @FXML private Button themeButton;
    @FXML private Button notificationButton;
    @FXML private Label notificationBadge;
    @FXML private MenuButton languageButton;
    @FXML private MenuButton userMenu;
    @FXML private HBox statusBar;
    @FXML private Label dataSourceLabel;
    @FXML private Label connectionLabel;
    @FXML private Label versionLabel;
    @FXML private Label clockLabel;

    private final Map<NavigationModule, HBox> navItems = new EnumMap<>(NavigationModule.class);
    private final AtomicBoolean backendChecked = new AtomicBoolean(false);
    private boolean collapsed;
    private Popup activePopup;

    @FXML
    private void initialize() {
        menuButton.setGraphic(Icons.glyph(Icons.Icon.MENU, 15));
        menuButton.setOnAction(event -> toggleSidebar());
        menuButton.setTooltip(new Tooltip(I18n.get().t("header.toggle_sidebar")));

        themeButton.setGraphic(Icons.glyph(ThemeManager.current() == Theme.DARK ? Icons.Icon.SUN : Icons.Icon.MOON, 15));
        themeButton.setOnAction(event -> toggleTheme());
        themeButton.setTooltip(new Tooltip(I18n.get().t("header.theme")));

        notificationButton.setGraphic(Icons.glyph(Icons.Icon.BELL, 15));
        notificationButton.setOnAction(event -> showNotifications());
        notificationButton.setTooltip(new Tooltip(I18n.get().t("header.notifications")));

        languageButton.setGraphic(Icons.glyph(Icons.Icon.GLOBE, 15));
        buildLanguageMenu();

        globalSearch.setOnAction(event -> runSearch());
        globalSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.length() >= 2) {
                showSearchResults(newValue);
            } else {
                hidePopup();
            }
        });

        I18n.get().localeProperty().addListener((observable, oldValue, newValue) -> onLocaleChanged());
    }

    /** Called by {@link AppContext} once the scene is attached and a session exists. */
    public void onShellReady() {
        AppContext.get().setNavigation(new NavigationManager(contentHost, this::onModuleChanged));
        Toasts.install(overlayLayer);
        collapsed = com.chemmanager.config.UserPreferences.get().sidebarCollapsed();
        applyCollapsedState();
        buildNavigation();
        buildUserChip();
        retranslateChrome();
        refreshNotificationBadge();
        updateConnectionStateAsync();
        AppContext.get().navigate(NavigationModule.DASHBOARD);
        if (!ServiceFactory.isApiMode()) {
            Toasts.info(I18n.get().t("toast.demo_mode"));
        }
    }

    // ------------------------------------------------------------------ navigation

    private void buildNavigation() {
        navContainer.getChildren().clear();
        navItems.clear();
        Session session = SessionContext.get().session();
        if (session == null) {
            return;
        }
        NavGroup currentGroup = null;
        for (NavigationModule module : NavigationModule.values()) {
            if (!session.has(module.permission())) {
                continue;
            }
            if (module.group() != currentGroup) {
                currentGroup = module.group();
                Label groupLabel = new Label(I18n.get().t(currentGroup.labelKey()));
                groupLabel.getStyleClass().add("nav-group-label");
                groupLabel.setMaxWidth(Double.MAX_VALUE);
                navContainer.getChildren().add(groupLabel);
            }
            HBox item = buildNavItem(module);
            navItems.put(module, item);
            navContainer.getChildren().add(item);
        }
        highlightActive(AppContext.get().navigation() == null ? null : AppContext.get().navigation().current());
    }

    private HBox buildNavItem(NavigationModule module) {
        Label icon = new Label();
        icon.setGraphic(Icons.glyph(module.icon(), 13));
        Label text = new Label(Labels.nav(module.id()));
        text.getStyleClass().add("nav-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox item = new HBox(11, icon, text, spacer);
        item.getStyleClass().add("nav-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setOnMouseClicked(event -> AppContext.get().navigate(module));
        item.setUserData(module);
        if (!module.implemented()) {
            Label phase = new Label("P" + module.phase());
            phase.getStyleClass().addAll("badge", "badge-neutral");
            phase.setStyle("-fx-font-size: 9px; -fx-padding: 1 5 1 5;");
            item.getChildren().add(item.getChildren().size() - 1, phase);
        }
        Tooltip.install(item, new Tooltip(Labels.nav(module.id())));
        return item;
    }

    private void onModuleChanged(NavigationModule module) {
        pageTitle.setText(Labels.nav(module.id()));
        pageSubtitle.setText(Labels.navSubtitle(module.id()));
        highlightActive(module);
        if (collapsed) {
            applyCollapsedState();
        }
    }

    private void highlightActive(NavigationModule module) {
        navItems.forEach((key, node) -> node.getStyleClass().remove("active"));
        HBox active = navItems.get(module);
        if (active != null && !active.getStyleClass().contains("active")) {
            active.getStyleClass().add("active");
        }
    }

    private void toggleSidebar() {
        collapsed = !collapsed;
        com.chemmanager.config.UserPreferences.get().setSidebarCollapsed(collapsed);
        applyCollapsedState();
    }

    private void applyCollapsedState() {
        sidebar.getStyleClass().remove("collapsed");
        if (collapsed) {
            sidebar.getStyleClass().add("collapsed");
        }
        brandTitle.setVisible(!collapsed);
        brandTitle.setManaged(!collapsed);
        brandSubtitle.setVisible(!collapsed);
        brandSubtitle.setManaged(!collapsed);
        for (Node node : navContainer.getChildren()) {
            if (node instanceof Label label && label.getStyleClass().contains("nav-group-label")) {
                label.setVisible(!collapsed);
                label.setManaged(!collapsed);
            }
            if (node instanceof HBox item) {
                for (Node child : item.getChildren()) {
                    if (child instanceof Label label && label.getStyleClass().contains("nav-label")) {
                        label.setVisible(!collapsed);
                        label.setManaged(!collapsed);
                    }
                    if (child instanceof Label label && label.getStyleClass().contains("badge")) {
                        label.setVisible(!collapsed);
                        label.setManaged(!collapsed);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ header

    private void buildLanguageMenu() {
        languageButton.getItems().clear();
        for (Locale locale : I18n.get().supportedLocales()) {
            MenuItem item = new MenuItem(I18n.get().displayName(locale));
            item.setOnAction(event -> changeLocale(locale));
            languageButton.getItems().add(item);
        }
    }

    private void changeLocale(Locale locale) {
        I18n.get().setLocale(locale);
    }

    private void onLocaleChanged() {
        Node root = sidebar.getScene() == null ? null : sidebar.getScene().getRoot();
        if (root != null) {
            root.setNodeOrientation(I18n.get().isRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        }
        buildLanguageMenu();
        buildUserChip();
        retranslateChrome();
        buildNavigation();
        refreshNotificationBadge();
        NavigationManager navigation = AppContext.get().navigation();
        if (navigation != null) {
            navigation.refresh();
        }
    }

    private void retranslateChrome() {
        brandTitle.setText(AppConfig.get().appName());
        brandSubtitle.setText(I18n.get().t("app.tagline"));
        versionLabel.setText("v" + AppConfig.get().appVersion());
        globalSearch.setPromptText(I18n.get().t("header.search_prompt"));
        dataSourceLabel.setText(I18n.get().t("status.data_source",
                ServiceFactory.isApiMode() ? I18n.get().t("status.source_api") : I18n.get().t("status.source_demo")));
        clockLabel.setText(Formatters.dateTime(java.time.LocalDateTime.now()));
        languageButton.setTooltip(new Tooltip(I18n.get().t("header.language")));
        themeButton.setTooltip(new Tooltip(I18n.get().t("header.theme")));
        notificationButton.setTooltip(new Tooltip(I18n.get().t("header.notifications")));
        if (!backendChecked.get()) {
            connectionLabel.setText(I18n.get().t("status.checking"));
        }
    }

    private void buildUserChip() {
        Session session = SessionContext.get().session();
        if (session == null) {
            return;
        }
        User user = session.user();
        Label avatar = new Label(user.initials());
        avatar.getStyleClass().add("avatar");
        Label name = new Label(user.fullName());
        name.getStyleClass().add("notification-title");
        Label role = new Label(Labels.get("role." + user.primaryRoleCode().toLowerCase(Locale.ROOT),
                user.primaryRoleCode()));
        role.getStyleClass().add("notification-time");
        VBox chip = new VBox(0, name, role);
        HBox graphic = new HBox(8, avatar, chip);
        graphic.setId("userChipGraphic");
        graphic.setAlignment(Pos.CENTER_LEFT);
        userMenu.setGraphic(graphic);
        userMenu.setText("");

        userMenu.getItems().clear();
        MenuItem profile = new MenuItem(I18n.get().t("menu.profile"));
        profile.setOnAction(event -> showProfile());
        MenuItem settings = new MenuItem(I18n.get().t("menu.settings"));
        settings.setOnAction(event -> AppContext.get().navigate(NavigationModule.SETTINGS));
        MenuItem theme = new MenuItem(I18n.get().t("menu.toggle_theme"));
        theme.setOnAction(event -> toggleTheme());
        MenuItem signOut = new MenuItem(I18n.get().t("menu.sign_out"));
        signOut.setOnAction(event -> signOut());
        userMenu.getItems().addAll(profile, settings, theme, new SeparatorMenuItem(), signOut);
    }

    private void toggleTheme() {
        ThemeManager.toggle();
        themeButton.setGraphic(Icons.glyph(ThemeManager.current() == Theme.DARK ? Icons.Icon.SUN : Icons.Icon.MOON, 15));
        Toasts.info(I18n.get().t(ThemeManager.current().labelKey()));
    }

    private void showProfile() {
        Session session = SessionContext.get().session();
        if (session == null) {
            return;
        }
        User user = session.user();
        String message = I18n.get().t("profile.details", user.fullName(), user.username(), user.email(),
                user.roleCodes(), Formatters.relative(session.startedAt()));
        Dialogs.info(AppContext.get().stage(), I18n.get().t("menu.profile"), message);
    }

    private void signOut() {
        if (Dialogs.confirm(AppContext.get().stage(), I18n.get().t("menu.sign_out"),
                I18n.get().t("menu.sign_out_confirm"))) {
            AppContext.get().logout();
        }
    }

    // ------------------------------------------------------------------ search

    private void runSearch() {
        List<NavigationModule> matches = searchModules(globalSearch.getText());
        if (matches.isEmpty()) {
            Toasts.info(I18n.get().t("search.no_results", globalSearch.getText()));
            return;
        }
        hidePopup();
        AppContext.get().navigate(matches.get(0));
        globalSearch.clear();
    }

    private void showSearchResults(String query) {
        List<NavigationModule> matches = searchModules(query);
        if (matches.isEmpty()) {
            hidePopup();
            return;
        }
        VBox results = new VBox();
        results.getStyleClass().add("popover");
        results.setPrefWidth(330);
        Label caption = new Label(I18n.get().t("search.modules"));
        caption.getStyleClass().add("text-muted");
        caption.setStyle("-fx-padding: 12 14 6 14; -fx-font-size: 10.8px;");
        results.getChildren().add(caption);
        for (NavigationModule module : matches) {
            Label icon = new Label();
            icon.setGraphic(Icons.glyph(module.icon(), 12));
            Label label = new Label(Labels.nav(module.id()));
            label.getStyleClass().add("notification-title");
            HBox row = new HBox(10, icon, label);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("notification-row");
            row.setOnMouseClicked(event -> {
                hidePopup();
                globalSearch.clear();
                AppContext.get().navigate(module);
            });
            results.getChildren().add(row);
        }
        showPopup(results, globalSearch);
    }

    private List<NavigationModule> searchModules(String query) {
        List<NavigationModule> matches = new ArrayList<>();
        Session session = SessionContext.get().session();
        if (query == null || query.isBlank() || session == null) {
            return matches;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (NavigationModule module : NavigationModule.values()) {
            if (!session.has(module.permission())) {
                continue;
            }
            String title = Labels.nav(module.id()).toLowerCase(Locale.ROOT);
            if (title.contains(needle) || module.id().contains(needle)) {
                matches.add(module);
            }
        }
        return matches.size() > 6 ? matches.subList(0, 6) : matches;
    }

    // ------------------------------------------------------------------ notifications

    private void refreshNotificationBadge() {
        FxTasks.run(() -> ServiceFactory.notifications().unreadCount(), count -> {
            notificationBadge.setText(count > 9 ? "9+" : String.valueOf(count));
            boolean visible = count > 0;
            notificationBadge.setVisible(visible);
            notificationBadge.setManaged(visible);
        }, error -> notificationBadge.setVisible(false));
    }

    private void showNotifications() {
        VBox panel = new VBox();
        panel.getStyleClass().add("popover");
        panel.setPrefWidth(400);
        panel.setMaxHeight(460);

        Label title = new Label(I18n.get().t("notifications.title"));
        title.getStyleClass().add("card-title");
        Button markAll = new Button(I18n.get().t("notifications.mark_all"));
        markAll.getStyleClass().addAll("link-button");
        markAll.setOnAction(event -> {
            ServiceFactory.notifications().markAllRead();
            refreshNotificationBadge();
            hidePopup();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox headerBox = new HBox(8, title, spacer, markAll);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.getStyleClass().add("popover-header");

        VBox list = new VBox();
        Label loading = new Label(I18n.get().t("common.loading"));
        loading.getStyleClass().add("text-muted");
        loading.setStyle("-fx-padding: 16;");
        list.getChildren().add(loading);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(340);
        scroll.getStyleClass().add("scroll-pane");

        panel.getChildren().addAll(headerBox, scroll);
        showPopup(panel, notificationButton);

        FxTasks.run(() -> ServiceFactory.notifications().latest(25), items -> {
            list.getChildren().clear();
            if (items.isEmpty()) {
                Label empty = new Label(I18n.get().t("notifications.empty"));
                empty.getStyleClass().add("text-muted");
                empty.setStyle("-fx-padding: 16;");
                list.getChildren().add(empty);
                return;
            }
            for (NotificationItem item : items) {
                list.getChildren().add(buildNotificationRow(item));
            }
        }, error -> {
            list.getChildren().setAll(new Label(I18n.get().t("error.loading")));
        });
    }

    private Node buildNotificationRow(NotificationItem item) {
        Label icon = new Label();
        icon.setGraphic(Icons.glyph(iconFor(item), 13));
        StackPane iconBox = new StackPane(icon);
        iconBox.getStyleClass().add("activity-dot");
        iconBox.setStyle("-fx-min-width: 26; -fx-min-height: 26; -fx-max-width: 26; -fx-max-height: 26; -fx-background-radius: 8;");

        Label title = new Label(item.title());
        title.getStyleClass().add("notification-title");
        title.setWrapText(true);
        Label message = new Label(item.message());
        message.getStyleClass().add("notification-message");
        message.setWrapText(true);
        Label time = new Label(Formatters.relative(item.createdAt()) + " · " + Labels.severity(item.severity()));
        time.getStyleClass().add("notification-time");
        VBox text = new VBox(2, title, message, time);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(11, iconBox, text);
        row.getStyleClass().add("notification-row");
        if (!item.isRead()) {
            row.getStyleClass().add("unread");
        }
        row.setOnMouseClicked(event -> {
            ServiceFactory.notifications().markRead(item.id());
            refreshNotificationBadge();
            hidePopup();
            if (item.moduleId() != null) {
                AppContext.get().navigate(NavigationModule.fromId(item.moduleId()));
            }
        });
        return row;
    }

    private Icons.Icon iconFor(NotificationItem item) {
        return switch (item.category()) {
            case STOCK -> Icons.Icon.CUBE;
            case EXPIRY -> Icons.Icon.CLOCK;
            case PRODUCTION -> Icons.Icon.PRODUCTION;
            case QUALITY -> Icons.Icon.SHIELD_CHECK;
            case PAYMENT -> Icons.Icon.MONEY;
            case DELIVERY -> Icons.Icon.DISTRIBUTION;
            case SAFETY -> Icons.Icon.FIRE;
            case SYSTEM -> Icons.Icon.DATABASE;
        };
    }

    private void updateConnectionStateAsync() {
        connectionLabel.setText(I18n.get().t("status.checking"));
        if (!ServiceFactory.isApiMode()) {
            // the demo dataset needs no network: say so instead of pretending to be offline
            backendChecked.set(true);
            connectionLabel.setText(I18n.get().t("status.demo_badge"));
            connectionLabel.getStyleClass().removeAll("badge-success", "badge-danger");
            connectionLabel.getStyleClass().add("badge-neutral");
            return;
        }
        FxTasks.run(() -> ServiceFactory.backendReachable(), reachable -> {
            backendChecked.set(true);
            connectionLabel.setText(reachable ? I18n.get().t("status.api_online") : I18n.get().t("status.api_offline"));
            connectionLabel.getStyleClass().removeAll("badge-success", "badge-neutral");
            connectionLabel.getStyleClass().add(reachable ? "badge-success" : "badge-neutral");
        }, error -> {
            backendChecked.set(true);
            connectionLabel.setText(I18n.get().t("status.api_offline"));
        });
    }

    // ------------------------------------------------------------------ popups

    private void showPopup(Node content, Node anchor) {
        hidePopup();
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(content);
        content.getStyleClass().add("popover");
        if (content instanceof javafx.scene.Parent parent) {
            parent.getStylesheets().setAll(ThemeManager.activeStylesheets());
        }
        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        double x = I18n.get().isRtl() ? bounds.getMinX() - 260 : bounds.getMaxX() - 380;
        popup.show(anchor, Math.max(10, x), bounds.getMaxY() + 8);
        activePopup = popup;
    }

    private void hidePopup() {
        if (activePopup != null) {
            activePopup.hide();
            activePopup = null;
        }
    }
}
