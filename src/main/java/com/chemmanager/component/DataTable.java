package com.chemmanager.component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.localization.I18n;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;

import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Paginated, searchable data table with a consistent look across modules.
 *
 * <p>Data is loaded through a {@link PageSource} on a background thread, so switching pages or
 * typing a search never blocks the JavaFX thread. Columns can render text, currency amounts,
 * dates or arbitrary nodes (badges, progress bars).</p>
 *
 * @param <T> row type
 */
public final class DataTable<T> extends VBox {

    /** Supplies one page of rows for the current search text and page index. */
    @FunctionalInterface
    public interface PageSource<T> {
        PagedResult<T> load(String search, int page, int pageSize);
    }

    /** Column definition supporting text, amount, date and custom node renderers. */
    public static final class Column<T> {

        private final String title;
        private final double width;
        private final Function<T, String> text;
        private final Function<T, Node> graphic;
        private final boolean rightAligned;

        private Column(String title, double width, Function<T, String> text, Function<T, Node> graphic,
                       boolean rightAligned) {
            this.title = title;
            this.width = width;
            this.text = text;
            this.graphic = graphic;
            this.rightAligned = rightAligned;
        }

        public static <T> Column<T> text(String title, double width, Function<T, String> value) {
            return new Column<>(title, width, value, null, false);
        }

        public static <T> Column<T> key(String title, double width, Function<T, String> value) {
            return new Column<>(title, width, value, null, false);
        }

        public static <T> Column<T> amount(String title, double width, Function<T, BigDecimal> value) {
            return new Column<>(title, width, row -> Formatters.currency(value.apply(row)), null, true);
        }

        public static <T> Column<T> quantity(String title, double width, Function<T, String> value) {
            return new Column<>(title, width, value, null, true);
        }

        public static <T> Column<T> date(String title, double width, Function<T, LocalDate> value) {
            return new Column<>(title, width, row -> {
                LocalDate date = value.apply(row);
                return date == null ? "—" : Formatters.shortDate(date);
            }, null, false);
        }

        public static <T> Column<T> node(String title, double width, Function<T, Node> graphic) {
            return new Column<>(title, width, null, graphic, false);
        }
    }

    private final TableView<T> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final HBox toolbar = new HBox(10);
    private final HBox footer = new HBox(10);
    private final Label rangeLabel = new Label();
    private final Button previousButton = new Button();
    private final Button nextButton = new Button();
    private final ComboBox<Integer> pageSizeBox = new ComboBox<>();
    private final ProgressIndicator busy = new ProgressIndicator();
    private final PageSource<T> source;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(320));
    private final VBox emptyState = new VBox(6);
    private final Label emptyTitle = new Label();
    private final Label emptyHint = new Label();

    private int page;
    private int pageSize = 20;
    private String query = "";
    private Consumer<T> onActivate;
    private Consumer<PagedResult<T>> onLoaded;

    public DataTable(PageSource<T> source, String searchPrompt) {
        this.source = source;
        setSpacing(10);

        searchField.setPromptText(searchPrompt);
        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(300);
        debounce.setOnFinished(event -> {
            query = searchField.getText() == null ? "" : searchField.getText().trim();
            page = 0;
            reload();
        });
        searchField.textProperty().addListener((observable, oldValue, newValue) -> debounce.playFromStart());

        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getChildren().addAll(searchField, new Region(), new Region());
        HBox.setHgrow(toolbar.getChildren().get(1), Priority.ALWAYS);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        emptyTitle.getStyleClass().add("empty-state-title");
        emptyHint.getStyleClass().add("empty-state-hint");
        emptyHint.setWrapText(true);
        emptyState.setAlignment(Pos.CENTER);
        emptyState.getStyleClass().add("empty-state");
        emptyState.getChildren().addAll(emptyTitle, emptyHint);
        table.setPlaceholder(emptyState);
        updateEmptyState(0);
        table.setFixedCellSize(-1);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMinHeight(320);

        busy.setPrefSize(18, 18);
        busy.setVisible(false);

        previousButton.getStyleClass().addAll("page-button");
        previousButton.setGraphic(Icons.glyph(Icons.Icon.CHEVRON_LEFT, 11));
        previousButton.setOnAction(event -> {
            if (page > 0) {
                page--;
                reload();
            }
        });
        nextButton.getStyleClass().addAll("page-button");
        nextButton.setGraphic(Icons.glyph(Icons.Icon.CHEVRON_RIGHT, 11));
        nextButton.setOnAction(event -> {
            page++;
            reload();
        });

        pageSizeBox.getItems().setAll(10, 20, 50, 100);
        pageSizeBox.setValue(pageSize);
        pageSizeBox.setOnAction(event -> {
            Integer value = pageSizeBox.getValue();
            if (value != null) {
                pageSize = value;
                page = 0;
                reload();
            }
        });

        rangeLabel.getStyleClass().add("text-muted");
        rangeLabel.setStyle("-fx-font-size: 11.5px;");
        pageSizeBox.setPrefWidth(78);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getStyleClass().add("pagination-bar");
        footer.getChildren().addAll(rangeLabel, spacer, busy, previousButton, nextButton, pageSizeBox);

        getChildren().addAll(toolbar, table, footer);

        // Search first: Ctrl+F focuses it, Escape clears it (documented in docs/08-ux-guidelines.md).
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && !searchField.getText().isEmpty()) {
                searchField.clear();
                event.consume();
            }
        });
        sceneProperty().addListener((observable, previous, scene) -> {
            KeyCombination focusSearch = new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN);
            if (previous != null) {
                previous.getAccelerators().remove(focusSearch);
            }
            if (scene != null) {
                scene.getAccelerators().put(focusSearch, () -> {
                    if (!searchField.isDisabled() && isVisible()) {
                        searchField.requestFocus();
                        searchField.selectAll();
                    }
                });
            }
        });
    }

    /**
     * An empty table is a state, not an absence: it says whether the dataset itself is empty or
     * only the current search is, and how to get back to data.
     */
    private void updateEmptyState(int total) {
        if (total > 0) {
            return;
        }
        if (query.isEmpty()) {
            emptyTitle.setText(I18n.get().t("table.empty"));
            emptyHint.setText(I18n.get().t("table.empty_hint"));
        } else {
            emptyTitle.setText(I18n.get().t("table.empty_search", query));
            emptyHint.setText(I18n.get().t("table.empty_hint"));
        }
    }

    @SafeVarargs
    public final DataTable<T> columns(Column<T>... columns) {
        for (Column<T> column : columns) {
            table.getColumns().add(buildColumn(column));
        }
        return this;
    }

    public DataTable<T> toolbarItem(Node node) {
        toolbar.getChildren().add(node);
        return this;
    }

    public DataTable<T> onActivate(Consumer<T> consumer) {
        this.onActivate = consumer;
        table.setRowFactory(view -> {
            javafx.scene.control.TableRow<T> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && onActivate != null) {
                    onActivate.accept(row.getItem());
                }
            });
            return row;
        });
        return this;
    }

    public DataTable<T> onLoaded(Consumer<PagedResult<T>> consumer) {
        this.onLoaded = consumer;
        return this;
    }

    public DataTable<T> placeholder(String text) {
        emptyTitle.setText(text);
        emptyHint.setText("");
        emptyState.getChildren().setAll(emptyTitle);
        return this;
    }

    public void resetToFirstPage() {
        page = 0;
        reload();
    }

    public void reload() {
        busy.setVisible(true);
        previousButton.setDisable(true);
        nextButton.setDisable(true);
        int requestedPage = page;
        FxTasks.run(() -> source.load(query, requestedPage, pageSize),
                result -> apply(result),
                error -> {
                    busy.setVisible(false);
                    table.setPlaceholder(new Label(I18n.get().t("error.loading") + " "
                            + (error.getMessage() == null ? "" : error.getMessage())));
                    Toasts.error(I18n.get().t("error.loading"));
                });
    }

    public String searchText() {
        return query;
    }

    public TableView<T> tableView() {
        return table;
    }

    public PagedResult<T> currentPage() {
        return new PagedResult<>(table.getItems(), page, pageSize, table.getItems().size());
    }

    private void apply(PagedResult<T> result) {
        busy.setVisible(false);
        page = result.page();
        table.getItems().setAll(result.items());
        rangeLabel.setText(I18n.get().t("table.range", result.firstIndex(), result.lastIndex(), result.total()));
        previousButton.setDisable(!result.hasPrevious());
        nextButton.setDisable(!result.hasNext());
        updateEmptyState(result.total());
        if (onLoaded != null) {
            onLoaded.accept(result);
        }
    }

    private TableColumn<T, String> buildColumn(Column<T> spec) {
        TableColumn<T, String> column = new TableColumn<>(spec.title);
        column.setPrefWidth(spec.width);
        column.setSortable(false);
        column.setReorderable(false);
        if (spec.graphic != null) {
            column.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyStringWrapper(""));
            column.setCellFactory(ignored -> new TableCell<>() {
                @Override
                protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                        setGraphic(null);
                        return;
                    }
                    setGraphic(spec.graphic.apply(getTableRow().getItem()));
                }
            });
        } else {
            column.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyStringWrapper(
                    spec.text.apply(data.getValue())));
            if (spec.rightAligned) {
                column.setStyle("-fx-alignment: center-right;");
            }
        }
        return column;
    }

    /** Convenience: builds a footer text like "21–40 of 240". */
    public String footerSummary() {
        return rangeLabel.getText();
    }

    public List<T> items() {
        return table.getItems();
    }
}
