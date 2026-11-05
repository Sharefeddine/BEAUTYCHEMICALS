package com.chemmanager.controller.settings;

import java.math.BigDecimal;
import java.util.Locale;

import com.chemmanager.app.AppContext;
import com.chemmanager.component.Icons;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.config.AppConfig;
import com.chemmanager.config.DataMode;
import com.chemmanager.config.Theme;
import com.chemmanager.config.ThemeManager;
import com.chemmanager.config.UserPreferences;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.CompanySettings;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;
import com.chemmanager.validation.ValidationResult;
import com.chemmanager.validation.Validator;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * System settings: general preferences (theme, language, currency, taxes), company identity used
 * on documents, and the data-source panel that switches between the demo dataset and the PHP API.
 */
public final class SettingsController {

    @FXML private ComboBox<Theme> themeSelector;
    @FXML private ComboBox<Locale> languageSelector;
    @FXML private TextField currencyField;
    @FXML private TextField vatField;
    @FXML private TextField invoicePrefixField;
    @FXML private Button saveGeneralButton;

    @FXML private TextField companyNameField;
    @FXML private TextField legalNameField;
    @FXML private TextField activityField;
    @FXML private TextField addressField;
    @FXML private TextField cityField;
    @FXML private TextField wilayaField;
    @FXML private TextField postalCodeField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField websiteField;
    @FXML private TextField taxIdField;
    @FXML private TextField rcField;
    @FXML private TextField nifField;
    @FXML private TextField aiField;
    @FXML private Button saveCompanyButton;
    @FXML private Label companyPreview;

    @FXML private Label dataModeLabel;
    @FXML private Label apiUrlLabel;
    @FXML private Label apiStatusLabel;
    @FXML private Button testConnectionButton;
    @FXML private Button useDemoButton;
    @FXML private Button useApiButton;
    @FXML private VBox backupInfo;

    private CompanySettings settings;

    @FXML
    private void initialize() {

        themeSelector.getItems().setAll(Theme.values());
        themeSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(Theme theme) {
                return theme == null ? "" : I18n.get().t(theme.labelKey());
            }

            @Override
            public Theme fromString(String text) {
                return null;
            }
        });
        themeSelector.getSelectionModel().select(ThemeManager.current());
        themeSelector.setOnAction(event -> {
            Theme selected = themeSelector.getValue();
            if (selected != null) {
                ThemeManager.apply(selected);
            }
        });

        languageSelector.getItems().setAll(I18n.get().supportedLocales());
        languageSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(Locale locale) {
                return locale == null ? "" : I18n.get().displayName(locale);
            }

            @Override
            public Locale fromString(String text) {
                return null;
            }
        });
        languageSelector.getSelectionModel().select(I18n.get().locale());
        languageSelector.setOnAction(event -> {
            Locale selected = languageSelector.getValue();
            if (selected != null) {
                I18n.get().setLocale(selected);
            }
        });

        saveGeneralButton.setGraphic(Icons.glyph(Icons.Icon.SAVE, 12));
        saveGeneralButton.setOnAction(event -> saveGeneral());
        saveCompanyButton.setGraphic(Icons.glyph(Icons.Icon.SAVE, 12));
        saveCompanyButton.setOnAction(event -> saveCompany());

        testConnectionButton.setGraphic(Icons.glyph(Icons.Icon.DATABASE, 12));
        testConnectionButton.setOnAction(event -> testConnection());
        useDemoButton.setGraphic(Icons.glyph(Icons.Icon.CUBE, 12));
        useDemoButton.setOnAction(event -> switchMode(DataMode.MOCK));
        useApiButton.setGraphic(Icons.glyph(Icons.Icon.GLOBE, 12));
        useApiButton.setOnAction(event -> switchMode(DataMode.API));

        loadSettings();
        refreshDataPanel();
        buildBackupInfo();
        I18n.get().localeProperty().addListener((observable, oldValue, newValue) -> {
        });
    }

    private void loadSettings() {
        FxTasks.run(() -> ServiceFactory.settings().load(), loaded -> {
            settings = loaded;
            currencyField.setText(loaded.currency());
            vatField.setText(loaded.defaultVatRate().toPlainString());
            invoicePrefixField.setText(loaded.invoicePrefix());
            companyNameField.setText(loaded.companyName());
            legalNameField.setText(loaded.legalName());
            activityField.setText(loaded.activity());
            addressField.setText(loaded.address());
            cityField.setText(loaded.city());
            wilayaField.setText(loaded.wilaya());
            postalCodeField.setText(loaded.postalCode());
            phoneField.setText(loaded.phone());
            emailField.setText(loaded.email());
            websiteField.setText(loaded.website());
            taxIdField.setText(loaded.taxId());
            rcField.setText(loaded.rc());
            nifField.setText(loaded.nif());
            aiField.setText(loaded.ai());
            updatePreview();
        }, error -> Toasts.error(I18n.get().t("error.loading")));
    }

    private void updatePreview() {
        if (settings == null) {
            return;
        }
        companyPreview.setText(I18n.get().t("settings.company.preview",
                companyNameField.getText(), legalNameField.getText(), settings.fullAddress(),
                phoneField.getText(), emailField.getText()));
    }

    private void saveGeneral() {
        if (settings == null) {
            return;
        }
        ValidationResult validation = Validator.create()
                .required("currency", currencyField.getText())
                .decimal("vat", vatField.getText())
                .between("vat", vatField.getText(), BigDecimal.ZERO, BigDecimal.valueOf(100))
                .maxLength("prefix", invoicePrefixField.getText(), 8)
                .result();
        if (!validation.isValid()) {
            Toasts.warning(validation.firstError());
            return;
        }
        CompanySettings updated = new CompanySettings(settings.companyName(), settings.legalName(),
                settings.activity(), settings.address(), settings.city(), settings.wilaya(),
                settings.postalCode(), settings.phone(), settings.email(), settings.website(),
                settings.taxId(), settings.rc(), settings.nif(), settings.ai(),
                currencyField.getText().trim().toUpperCase(Locale.ROOT),
                Formatters.parseDecimal(vatField.getText()), invoicePrefixField.getText().trim().toUpperCase(Locale.ROOT),
                settings.logoPath(), I18n.get().locale().toLanguageTag(), ThemeManager.current().name());
        persist(updated, () -> {
            UserPreferences.get().setLocale(I18n.get().locale());
            Toasts.success(I18n.get().t("settings.saved"));
        });
    }

    private void saveCompany() {
        if (settings == null) {
            return;
        }
        ValidationResult validation = Validator.create()
                .required("companyName", companyNameField.getText())
                .email("email", emailField.getText())
                .phone("phone", phoneField.getText())
                .required("phone", phoneField.getText())
                .result();
        if (!validation.isValid()) {
            Toasts.warning(validation.firstError());
            return;
        }
        CompanySettings updated = new CompanySettings(companyNameField.getText().trim(), legalNameField.getText().trim(),
                activityField.getText().trim(), addressField.getText().trim(), cityField.getText().trim(),
                wilayaField.getText().trim(), postalCodeField.getText().trim(), phoneField.getText().trim(),
                emailField.getText().trim(), websiteField.getText().trim(), taxIdField.getText().trim(),
                rcField.getText().trim(), nifField.getText().trim(), aiField.getText().trim(),
                settings.currency(), settings.defaultVatRate(), settings.invoicePrefix(), settings.logoPath(),
                settings.localeCode(), settings.theme());
        persist(updated, () -> {
            updatePreview();
            Toasts.success(I18n.get().t("settings.saved"));
        });
    }

    private void persist(CompanySettings updated, Runnable onSuccess) {
        FxTasks.runVoid(() -> ServiceFactory.settings().save(updated), () -> {
            settings = updated;
            onSuccess.run();
        }, error -> Toasts.error(I18n.get().t("settings.save_failed", String.valueOf(error.getMessage()))));
    }

    private void refreshDataPanel() {
        dataModeLabel.setText(ServiceFactory.isApiMode()
                ? I18n.get().t("settings.data.api_mode") : I18n.get().t("settings.data.demo_mode"));
        apiUrlLabel.setText(I18n.get().t("settings.data.api_url", AppConfig.get().apiBaseUrl()));
        apiStatusLabel.setText(I18n.get().t("settings.data.unknown"));
        apiStatusLabel.getStyleClass().removeAll("badge-success", "badge-danger", "badge-neutral");
        apiStatusLabel.getStyleClass().add("badge-neutral");
    }

    private void testConnection() {
        apiStatusLabel.setText(I18n.get().t("settings.data.checking"));
        FxTasks.run(() -> ServiceFactory.backendReachable(), reachable -> {
            apiStatusLabel.setText(reachable
                    ? I18n.get().t("settings.data.reachable") : I18n.get().t("settings.data.unreachable"));
            apiStatusLabel.getStyleClass().removeAll("badge-success", "badge-danger", "badge-neutral");
            apiStatusLabel.getStyleClass().add(reachable ? "badge-success" : "badge-danger");
        }, error -> Toasts.error(I18n.get().t("error.network")));
    }

    private void switchMode(DataMode mode) {
        ServiceFactory.reinitialise(mode);
        refreshDataPanel();
        Toasts.info(I18n.get().t("settings.data.switched", mode.name()));
        if (AppContext.get().navigation() != null) {
            AppContext.get().navigation().refresh();
        }
    }

    private void buildBackupInfo() {
        backupInfo.getChildren().clear();
        Label body = new Label(I18n.get().t("settings.backup.body"));
        body.getStyleClass().add("text-muted");
        body.setWrapText(true);
        HBox status = new HBox(8, StatusBadge.info(I18n.get().t("settings.backup.status")),
                new Label(I18n.get().t("settings.backup.schedule")));
        status.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        backupInfo.getChildren().addAll(body, status);
    }
}
