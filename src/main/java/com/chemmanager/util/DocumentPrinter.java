package com.chemmanager.util;

import java.awt.Desktop;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.Toasts;
import com.chemmanager.localization.I18n;
import com.chemmanager.service.ServiceFactory;

import javafx.stage.Window;

/**
 * Opens a printable document (quotation, delivery note, invoice, statement) in the user's browser.
 *
 * <p>The API renders the document as a self-contained HTML page and serves it behind the session
 * token, so the client fetches it here with the usual authenticated call, writes a temporary file
 * and hands that file to the desktop. The printed page therefore shows exactly what the server
 * stamped, not a second rendering done by the client.</p>
 */
public final class DocumentPrinter {

    private DocumentPrinter() {
    }

    /**
     * @param type quotation, order, delivery, invoice or statement
     * @param id   identifier of the document (the customer for a statement)
     */
    public static void open(Window owner, String type, long id, String language) {
        if (!ServiceFactory.isApiMode()) {
            Dialogs.info(owner, I18n.get().t("document.title"),
                    I18n.get().t("document.demo_hint"));
            return;
        }
        try {
            String html = ServiceFactory.apiClient()
                    .getText(ApiEndpoints.document(type, id) + "?lang=" + language);
            Path file = Files.createTempFile("chemmanager-" + type + "-" + id + "-", ".html");
            Files.writeString(file, html, StandardCharsets.UTF_8);
            file.toFile().deleteOnExit();
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.toFile());
                Toasts.success(I18n.get().t("document.opened", file.getFileName().toString()));
            } else {
                Toasts.info(I18n.get().t("document.saved", file.toAbsolutePath().toString()));
            }
        } catch (Exception exception) {
            Toasts.error(exception.getMessage() == null ? I18n.get().t("error.generic") : exception.getMessage());
        }
    }
}
