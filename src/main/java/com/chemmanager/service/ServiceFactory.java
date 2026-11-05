package com.chemmanager.service;

import java.time.Duration;

import com.chemmanager.api.ApiClient;
import com.chemmanager.config.AppConfig;
import com.chemmanager.config.DataMode;
import com.chemmanager.security.Session;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.api.ApiAuthService;
import com.chemmanager.service.api.ApiDashboardService;
import com.chemmanager.service.api.ApiNotificationService;
import com.chemmanager.service.api.ApiProductService;
import com.chemmanager.service.api.ApiRawMaterialService;
import com.chemmanager.service.api.ApiSettingsService;
import com.chemmanager.service.api.ApiUserService;
import com.chemmanager.service.mock.MockAuthService;
import com.chemmanager.service.mock.MockDashboardService;
import com.chemmanager.service.mock.MockNotificationService;
import com.chemmanager.service.mock.MockProductService;
import com.chemmanager.service.mock.MockRawMaterialService;
import com.chemmanager.service.mock.MockSettingsService;
import com.chemmanager.service.mock.MockUserService;

/**
 * Composition root of the service layer. Controllers never instantiate a service directly:
 * they ask the factory, which returns either the offline demo implementation or the PHP API
 * implementation depending on {@link AppConfig#dataMode()}.
 *
 * <p>{@link DataMode#AUTO} probes the backend once at start-up and falls back to the demo
 * dataset when it is unreachable, so the desktop client always starts.</p>
 */
public final class ServiceFactory {

    private static DataMode activeMode;
    private static ApiClient apiClient;
    private static boolean initialised;

    private static AuthService authService;
    private static DashboardService dashboardService;
    private static ProductService productService;
    private static RawMaterialService rawMaterialService;
    private static NotificationService notificationService;
    private static UserService userService;
    private static SettingsService settingsService;

    private ServiceFactory() {
    }

    public static synchronized void initialise() {
        if (initialised) {
            return;
        }
        AppConfig config = AppConfig.get();
        DataMode requested = config.dataMode();
        apiClient = new ApiClient(config.apiBaseUrl(), config.apiTimeout(),
                () -> {
                    Session session = SessionContext.get().session();
                    return session == null ? null : session.token();
                });

        DataMode resolved = requested;
        if (requested == DataMode.AUTO) {
            resolved = apiClient.ping() ? DataMode.API : DataMode.MOCK;
        }
        configure(resolved);
        initialised = true;
    }

    /** Switches the whole layer between demo and API mode (used by Settings / troubleshooting). */
    public static synchronized void reinitialise(DataMode mode) {
        initialised = false;
        configure(mode);
        initialised = true;
    }

    private static void configure(DataMode mode) {
        activeMode = mode;
        if (mode == DataMode.API) {
            authService = new ApiAuthService(apiClient);
            dashboardService = new ApiDashboardService(apiClient);
            productService = new ApiProductService(apiClient);
            rawMaterialService = new ApiRawMaterialService(apiClient);
            notificationService = new ApiNotificationService(apiClient);
            userService = new ApiUserService(apiClient);
            settingsService = new ApiSettingsService(apiClient);
        } else {
            authService = new MockAuthService();
            dashboardService = new MockDashboardService();
            productService = new MockProductService();
            rawMaterialService = new MockRawMaterialService();
            notificationService = new MockNotificationService();
            userService = new MockUserService();
            settingsService = new MockSettingsService();
        }
    }

    public static DataMode mode() {
        initialise();
        return activeMode;
    }

    public static boolean isApiMode() {
        return mode() == DataMode.API;
    }

    public static String modeLabel() {
        return isApiMode() ? "API" : "DEMO";
    }

    public static ApiClient apiClient() {
        initialise();
        return apiClient;
    }

    public static AuthService auth() {
        initialise();
        return authService;
    }

    public static DashboardService dashboard() {
        initialise();
        return dashboardService;
    }

    public static ProductService products() {
        initialise();
        return productService;
    }

    public static RawMaterialService rawMaterials() {
        initialise();
        return rawMaterialService;
    }

    public static NotificationService notifications() {
        initialise();
        return notificationService;
    }

    public static UserService users() {
        initialise();
        return userService;
    }

    public static SettingsService settings() {
        initialise();
        return settingsService;
    }

    /** Short timeout used by the connectivity indicator in the status bar. */
    public static boolean backendReachable() {
        return apiClient != null && apiClient.ping();
    }

    public static Duration defaultTimeout() {
        return AppConfig.get().apiTimeout();
    }
}
