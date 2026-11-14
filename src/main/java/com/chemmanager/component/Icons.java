package com.chemmanager.component;

import java.io.InputStream;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;

/**
 * Central icon catalogue. One semantic name per concept used across the application so the
 * visual language stays consistent and icons can be swapped in a single place.
 */
public final class Icons {

    /** Semantic icons (mapped to FontAwesome glyphs). */
    public enum Icon {
        DASHBOARD(FontAwesomeIcon.DASHBOARD),
        PRODUCTS(FontAwesomeIcon.CUBES),
        MATERIALS(FontAwesomeIcon.FLASK),
        FORMULATIONS(FontAwesomeIcon.SITEMAP),
        PRODUCTION(FontAwesomeIcon.INDUSTRY),
        INVENTORY(FontAwesomeIcon.ARCHIVE),
        PURCHASING(FontAwesomeIcon.SHOPPING_CART),
        SUPPLIERS(FontAwesomeIcon.HANDSHAKE_ALT),
        SALES(FontAwesomeIcon.MONEY),
        CUSTOMERS(FontAwesomeIcon.USERS),
        DISTRIBUTION(FontAwesomeIcon.TRUCK),
        FINANCE(FontAwesomeIcon.BANK),
        EXPENSES(FontAwesomeIcon.CREDIT_CARD),
        EMPLOYEES(FontAwesomeIcon.USER),
        REPORTS(FontAwesomeIcon.LINE_CHART),
        SAFETY(FontAwesomeIcon.SHIELD),
        SETTINGS(FontAwesomeIcon.COG),
        USERS(FontAwesomeIcon.USER_CIRCLE),
        SEARCH(FontAwesomeIcon.SEARCH),
        BELL(FontAwesomeIcon.BELL),
        SUN(FontAwesomeIcon.SUN_ALT),
        MOON(FontAwesomeIcon.MOON_ALT),
        GLOBE(FontAwesomeIcon.GLOBE),
        LOGIN(FontAwesomeIcon.SIGN_IN),
        LOGOUT(FontAwesomeIcon.SIGN_OUT),
        MENU(FontAwesomeIcon.BARS),
        REFRESH(FontAwesomeIcon.REFRESH),
        DOWNLOAD(FontAwesomeIcon.DOWNLOAD),
        EXCEL(FontAwesomeIcon.FILE_EXCEL_ALT),
        PDF(FontAwesomeIcon.FILE_PDF_ALT),
        PRINT(FontAwesomeIcon.PRINT),
        FILTER(FontAwesomeIcon.FILTER),
        CALENDAR(FontAwesomeIcon.CALENDAR),
        PLUS(FontAwesomeIcon.PLUS),
        CLOSE(FontAwesomeIcon.TIMES),
        CHECK(FontAwesomeIcon.CHECK),
        CHECK_CIRCLE(FontAwesomeIcon.CHECK_CIRCLE),
        INFO(FontAwesomeIcon.INFO_CIRCLE),
        WARNING(FontAwesomeIcon.EXCLAMATION_TRIANGLE),
        DANGER(FontAwesomeIcon.EXCLAMATION_CIRCLE),
        ARROW_UP(FontAwesomeIcon.ARROW_UP),
        ARROW_DOWN(FontAwesomeIcon.ARROW_DOWN),
        MONEY(FontAwesomeIcon.MONEY),
        CUBE(FontAwesomeIcon.CUBE),
        PERCENT(FontAwesomeIcon.PERCENT),
        SCALE(FontAwesomeIcon.BALANCE_SCALE),
        CLOCK(FontAwesomeIcon.CLOCK_ALT),
        LIST(FontAwesomeIcon.LIST_ALT),
        DOTS(FontAwesomeIcon.ELLIPSIS_H),
        CHEVRON_LEFT(FontAwesomeIcon.CHEVRON_LEFT),
        CHEVRON_RIGHT(FontAwesomeIcon.CHEVRON_RIGHT),
        CHEVRON_DOWN(FontAwesomeIcon.CHEVRON_DOWN),
        DOCUMENT(FontAwesomeIcon.FILE_TEXT_ALT),
        CLIPBOARD(FontAwesomeIcon.CLIPBOARD),
        BARCODE(FontAwesomeIcon.BARCODE),
        HISTORY(FontAwesomeIcon.HISTORY),
        DATABASE(FontAwesomeIcon.DATABASE),
        SAVE(FontAwesomeIcon.SAVE),
        EDIT(FontAwesomeIcon.PENCIL),
        DELETE(FontAwesomeIcon.TRASH_ALT),
        EYE(FontAwesomeIcon.EYE),
        LOCK(FontAwesomeIcon.LOCK),
        TAG(FontAwesomeIcon.TAG),
        TASKS(FontAwesomeIcon.TASKS),
        TROPHY(FontAwesomeIcon.TROPHY),
        FIRE(FontAwesomeIcon.FIRE_EXTINGUISHER),
        DROP(FontAwesomeIcon.TINT),
        RECYCLE(FontAwesomeIcon.RECYCLE),
        SPEED(FontAwesomeIcon.TACHOMETER),
        GRID(FontAwesomeIcon.TH_LARGE),
        BELL_SLASH(FontAwesomeIcon.BELL_SLASH_ALT),
        SHIELD_CHECK(FontAwesomeIcon.SHIELD);

        private final FontAwesomeIcon glyph;

        Icon(FontAwesomeIcon glyph) {
            this.glyph = glyph;
        }

        public FontAwesomeIcon glyph() {
            return glyph;
        }
    }

    /**
     * Physical path of the glyph font inside the FontAwesomeFX artifact. JavaFX does not
     * register fonts shipped in a jar automatically, so it is loaded once here: without this
     * the glyphs render as empty squares on every platform.
     */
    private static final String FONT_RESOURCE = "/de/jensd/fx/glyphs/fontawesome/fontawesome-webfont.ttf";

    private static String fontFamily;

    private Icons() {
    }

    public static FontAwesomeIconView glyph(Icon icon) {
        return glyph(icon, 14);
    }

    public static FontAwesomeIconView glyph(Icon icon, double sizePx) {
        FontAwesomeIconView view = new FontAwesomeIconView(icon.glyph());
        view.getStyleClass().add("glyph-icon");
        // The glyph size is an API argument, not a design decision, so it is set on the font
        // rather than through an inline style: Icons is the only place allowed to size a glyph.
        String family = fontFamily();
        view.setFont(family.isEmpty() ? Font.font(sizePx) : Font.font(family, sizePx));
        view.setMouseTransparent(true);
        return view;
    }

    /** Loads the glyph font on first use and remembers its family name. */
    private static synchronized String fontFamily() {
        if (fontFamily == null) {
            String family = "";
            try (InputStream stream = Icons.class.getResourceAsStream(FONT_RESOURCE)) {
                if (stream != null) {
                    Font loaded = Font.loadFont(stream, 16);
                    if (loaded != null && loaded.getFamily() != null && !loaded.getFamily().isBlank()) {
                        family = loaded.getFamily();
                    }
                }
            } catch (RuntimeException | java.io.IOException ex) {
                family = "";
            }
            fontFamily = family;
        }
        return fontFamily;
    }

    /** Round coloured container used by KPI cards and the placeholder screens. */
    public static StackPane badge(Icon icon, String styleClass, double iconSize) {
        StackPane pane = new StackPane(glyph(icon, iconSize));
        pane.getStyleClass().addAll("kpi-icon-wrap", styleClass);
        return pane;
    }

    public static Node node(Icon icon, double sizePx, String... styleClasses) {
        FontAwesomeIconView view = glyph(icon, sizePx);
        view.getStyleClass().addAll(styleClasses);
        return view;
    }
}
