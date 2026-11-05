package com.chemmanager.controller.auth;

import com.chemmanager.app.AppContext;
import com.chemmanager.component.Icons;
import com.chemmanager.config.Theme;
import com.chemmanager.config.ThemeManager;
import com.chemmanager.config.UserPreferences;
import com.chemmanager.localization.I18n;
import com.chemmanager.security.AuthException;
import com.chemmanager.security.Session;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.FxTasks;
import com.chemmanager.validation.ValidationResult;
import com.chemmanager.validation.Validator;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Sign-in screen. Validates input locally, authenticates on a background thread, stores the
 * session and opens the shell. Failed attempts show a translated, non-revealing error and the
 * service applies throttling after repeated failures.
 */
public final class LoginController {

    @FXML private VBox loginCard;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private Button togglePasswordButton;
    @FXML private Button signInButton;
    @FXML private Label errorLabel;
    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label demoTitleLabel;
    @FXML private Label demoBodyLabel;
    @FXML private Label heroTitle;
    @FXML private Label heroSubtitle;
    @FXML private Label versionLabel;
    @FXML private CheckBox rememberCheck;
    @FXML private MenuButton languageButton;
    @FXML private Button themeButton;

    private boolean passwordVisible;

    @FXML
    private void initialize() {
        UserPreferences preferences = UserPreferences.get();
        if (preferences.rememberUsername()) {
            usernameField.setText(preferences.lastUsername());
        }
        rememberCheck.setSelected(preferences.rememberUsername());
        rememberCheck.textProperty().set(I18n.get().t("login.remember"));

        signInButton.setGraphic(Icons.glyph(Icons.Icon.LOGIN, 13));
        signInButton.setOnAction(event -> signIn());
        passwordField.setOnAction(event -> signIn());
        passwordVisibleField.setOnAction(event -> signIn());

        togglePasswordButton.setGraphic(Icons.glyph(Icons.Icon.EYE, 12));
        togglePasswordButton.setOnAction(event -> togglePasswordVisibility());
        themeButton.setGraphic(Icons.glyph(ThemeManager.current() == Theme.DARK ? Icons.Icon.SUN : Icons.Icon.MOON, 14));
        themeButton.setOnAction(event -> {
            ThemeManager.toggle();
            themeButton.setGraphic(Icons.glyph(ThemeManager.current() == Theme.DARK ? Icons.Icon.SUN : Icons.Icon.MOON, 14));
        });

        languageButton.setGraphic(Icons.glyph(Icons.Icon.GLOBE, 14));
        for (var locale : I18n.get().supportedLocales()) {
            MenuItem item = new MenuItem(I18n.get().displayName(locale));
            item.setOnAction(event -> I18n.get().setLocale(locale));
            languageButton.getItems().add(item);
        }

        versionLabel.setText(I18n.get().t("login.version",
                com.chemmanager.config.AppConfig.get().appVersion(), ServiceFactory.modeLabel()));

        passwordVisibleField.setVisible(false);
        passwordVisibleField.setManaged(false);
        retranslate();
        I18n.get().localeProperty().addListener((observable, oldValue, newValue) -> {
            retranslate();
            buildLanguageMenu();
        });
    }

    private void buildLanguageMenu() {
        languageButton.getItems().clear();
        for (var locale : I18n.get().supportedLocales()) {
            MenuItem item = new MenuItem(I18n.get().displayName(locale));
            item.setOnAction(event -> I18n.get().setLocale(locale));
            languageButton.getItems().add(item);
        }
    }

    private void retranslate() {
        titleLabel.setText(I18n.get().t("login.title"));
        subtitleLabel.setText(I18n.get().t("login.subtitle"));
        heroTitle.setText(I18n.get().t("login.hero_title"));
        heroSubtitle.setText(I18n.get().t("login.hero_subtitle"));
        demoTitleLabel.setText(I18n.get().t("login.demo_title"));
        demoBodyLabel.setText(I18n.get().t("login.demo_body"));
        usernameField.setPromptText(I18n.get().t("login.username"));
        passwordField.setPromptText(I18n.get().t("login.password"));
        passwordVisibleField.setPromptText(I18n.get().t("login.password"));
        signInButton.setText(I18n.get().t("login.sign_in"));
        rememberCheck.setText(I18n.get().t("login.remember"));
        versionLabel.setText(I18n.get().t("login.version",
                com.chemmanager.config.AppConfig.get().appVersion(), ServiceFactory.modeLabel()));
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            passwordVisibleField.setText(passwordField.getText());
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            passwordVisibleField.requestFocus();
        } else {
            passwordField.setText(passwordVisibleField.getText());
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            passwordField.requestFocus();
        }
        togglePasswordButton.setGraphic(Icons.glyph(passwordVisible ? Icons.Icon.LOCK : Icons.Icon.EYE, 12));
    }

    private void signIn() {
        String username = usernameField.getText();
        String password = passwordVisible ? passwordVisibleField.getText() : passwordField.getText();
        ValidationResult validation = Validator.create()
                .required("username", username)
                .required("password", password)
                .result();
        if (!validation.isValid()) {
            showError(validation.firstError());
            return;
        }
        setBusy(true);
        final String capturedPassword = password;
        FxTasks.run(() -> ServiceFactory.auth().login(username.trim(), capturedPassword),
                this::onAuthenticated,
                error -> {
                    setBusy(false);
                    showError(messageFor(error));
                });
    }

    private void onAuthenticated(Session session) {
        setBusy(false);
        SessionContext.get().open(session);
        UserPreferences preferences = UserPreferences.get();
        preferences.setRememberUsername(rememberCheck.isSelected());
        preferences.setLastUsername(rememberCheck.isSelected() ? session.user().username() : "");
        AppContext.get().showShell();
    }

    private String messageFor(Throwable error) {
        if (error instanceof AuthException authException) {
            String translated = I18n.get().t(authException.messageKey());
            return translated.startsWith("!") ? authException.getMessage() : translated;
        }
        String translated = I18n.get().t("error.network");
        return translated.startsWith("!") ? error.getMessage() : translated;
    }

    private void setBusy(boolean busy) {
        signInButton.setDisable(busy);
        signInButton.setText(busy ? I18n.get().t("login.signing_in") : I18n.get().t("login.sign_in"));
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
        passwordVisibleField.setDisable(busy);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        passwordField.getStyleClass().add("error");
        passwordVisibleField.getStyleClass().add("error");
    }
}
