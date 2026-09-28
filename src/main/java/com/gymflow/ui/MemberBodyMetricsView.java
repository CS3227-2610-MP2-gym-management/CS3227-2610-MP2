package com.gymflow.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import com.gymflow.metric.BodyMetricService;
import com.gymflow.member.MemberAccountService;
import com.gymflow.model.Account;
import com.gymflow.model.BodyMetric;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Member self-service view for body-mass readings and recent trends. */
final class MemberBodyMetricsView {
    private static final DateTimeFormatter CHART_DATE = DateTimeFormatter.ofPattern("d MMM");

    private MemberBodyMetricsView() {
    }

    static Parent create(BodyMetricService service, MemberAccountService accounts, Account session,
            Consumer<Screen> navigate,
            Runnable logout) {
        Label status = new Label("Loading body-mass readings…");
        status.getStyleClass().add("muted-text");
        VBox membershipNotice = MemberHomeView.membershipRequiredNotice();
        Label latest = new Label("—");
        latest.getStyleClass().add("body-mass-latest");
        Label changeSummary = new Label();
        changeSummary.getStyleClass().add("body-mass-change");
        Label latestDate = new Label("Your latest measurement");
        latestDate.getStyleClass().add("muted-text");
        DatePicker date = new DatePicker(LocalDate.now());
        date.setAccessibleText("Measurement date");
        UiComponents.calendarOnly(date);
        TextField kilograms = new TextField();
        kilograms.setPromptText("e.g. 70.5");
        kilograms.setAccessibleText("Body mass in kilograms");
        kilograms.setTextFormatter(new javafx.scene.control.TextFormatter<>(change ->
                change.getControlNewText().matches("\\d*(\\.\\d{0,3})?") ? change : null));
        Button save = new Button("Save reading");
        save.getStyleClass().add("primary-button");
        requireMembership(save, membershipNotice, accounts, session);
        ListView<BodyMetric> readings = UiComponents.cardList("No body-mass readings are recorded.",
                MemberBodyMetricsView::card);
        ChartPanel chart = new ChartPanel();
        EditorState editor = new EditorState();
        Runnable load = () -> Thread.startVirtualThread(() -> {
            try {
                List<BodyMetric> history = service.history(session);
                Platform.runLater(() -> refresh(history, latest, latestDate, changeSummary, kilograms, readings,
                        chart, editor, status));
            } catch (RuntimeException exception) {
                Platform.runLater(() -> status.setText("Unable to load body-mass readings."));
            }
        });
        save.setOnAction(event -> save(service, session, date, kilograms, save, editor, load));
        readings.setOnMouseClicked(event -> {
            BodyMetric selected = readings.getSelectionModel().getSelectedItem();
            if (selected != null) {
                edit(readings, service, session, selected, load);
            }
        });
        VBox history = new VBox(10, UiComponents.cardLabel("Weight history", "section-title"),
                status, readings);
        history.getStyleClass().add("body-mass-history");
        VBox content = new VBox(20, UiComponents.header("Measurements",
                "Track your weight over time", null),
                membershipNotice,
                editor(latest, latestDate, changeSummary, date, kilograms, save), UiComponents.card(chart),
                UiComponents.card(history));
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        load.run();
        return MemberHomeView.shell(content, Screen.MEMBER_WORKOUTS, navigate, logout);
    }

    private static void requireMembership(Button button, VBox notice, MemberAccountService accounts,
            Account session) {
        button.setDisable(true);
        Thread.startVirtualThread(() -> {
            boolean current;
            try {
                current = accounts.hasCurrentMembership(session);
            } catch (RuntimeException exception) {
                current = false;
            }
            boolean enabled = current;
            Platform.runLater(() -> {
                button.setDisable(!enabled);
                MemberHomeView.showMembershipNotice(notice, !enabled);
            });
        });
    }

    private static VBox editor(Label latest, Label latestDate, Label change, DatePicker date,
            TextField kilograms, Button save) {
        Label eyebrow = new Label("WEIGHT");
        eyebrow.getStyleClass().add("body-mass-eyebrow");
        VBox summary = new VBox(2, eyebrow, latest, change, latestDate);
        VBox dateInput = new VBox(5, new Label("Measurement date"), date);
        dateInput.setVisible(false);
        dateInput.setManaged(false);
        Button changeDate = new Button("Change date");
        changeDate.getStyleClass().add("body-mass-change-date");
        changeDate.setOnAction(event -> {
            boolean showDate = !dateInput.isVisible();
            dateInput.setVisible(showDate);
            dateInput.setManaged(showDate);
            changeDate.setText(showDate ? "Use today" : "Change date");
            if (!showDate) {
                date.setValue(LocalDate.now());
            } else {
                date.show();
            }
        });
        VBox massInput = new VBox(5, new Label("Weight (kg)"), kilograms);
        HBox inputs = new HBox(10, massInput, save, changeDate, dateInput);
        inputs.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(massInput, Priority.ALWAYS);
        kilograms.setMaxWidth(Double.MAX_VALUE);
        VBox card = UiComponents.card(summary, inputs);
        card.getStyleClass().add("body-mass-editor");
        return card;
    }

    private static void refresh(List<BodyMetric> history, Label latest, Label latestDate, Label change,
            TextField kilograms,
            ListView<BodyMetric> readings, ChartPanel chart, EditorState editor, Label status) {
        readings.getItems().setAll(history);
        if (history.isEmpty()) {
            latest.setText("No reading yet");
            latestDate.setText("Your latest measurement");
        } else {
            BodyMetric newest = history.getFirst();
            latest.setText(formatMass(newest));
            latestDate.setText("Your latest measurement on " + newest.measurementDate().format(
                    DateTimeFormatter.ofPattern("d MMM uuuu")));
            change.setText(changeText(history));
            if (!editor.touched()) {
                kilograms.setText(newest.weightKilograms().stripTrailingZeros().toPlainString());
            }
        }
        if (history.isEmpty()) {
            change.setText("Add your first measurement to begin tracking.");
        }
        chart.show(history);
        status.setText(history.isEmpty() ? "Add your first reading above."
                : "Select a past reading to edit or delete it.");
    }

    private static void save(BodyMetricService service, Account session, DatePicker date,
            TextField kilograms, Button save, EditorState editor, Runnable reload) {
        try {
            LocalDate selectedDate = date.getValue();
            BigDecimal mass = value(kilograms);
            editor.setTouched(true);
            save.setDisable(true);
            Thread.startVirtualThread(() -> {
                try {
                    service.save(session, selectedDate, mass);
                    Platform.runLater(() -> {
                        editor.setTouched(false);
                        save.setDisable(false);
                        reload.run();
                    });
                } catch (RuntimeException exception) {
                    Platform.runLater(() -> {
                        save.setDisable(false);
                        error(exception);
                    });
                }
            });
        } catch (RuntimeException exception) {
            error(exception);
        }
    }

    private static void edit(Node owner, BodyMetricService service, Account session,
            BodyMetric existing, Runnable reload) {
        DatePicker date = new DatePicker(existing.measurementDate());
        UiComponents.calendarOnly(date);
        TextField kilograms = new TextField(existing.weightKilograms().stripTrailingZeros().toPlainString());
        Alert dialog = new Alert(Alert.AlertType.NONE);
        dialog.setTitle("Edit body mass");
        dialog.getDialogPane().setContent(new VBox(12, new Label("Date"), date,
                new Label("Body mass (kg)"), kilograms));
        ButtonType update = new ButtonType("Update", ButtonType.OK.getButtonData());
        ButtonType delete = new ButtonType("Delete", ButtonType.NO.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(update, delete, ButtonType.CANCEL);
        dialog.setResultConverter(result -> {
            if (result == delete && confirmDelete(owner)) {
                run(() -> service.delete(session, existing.id()), reload);
            } else if (result == update) {
                run(() -> service.update(session, existing.id(), date.getValue(), value(kilograms)), reload);
            }
            return result;
        });
        UiComponents.styleDialog(dialog, owner, "body-mass-dialog", false);
        dialog.showAndWait();
    }

    private static VBox card(BodyMetric metric) {
        Label mass = UiComponents.cardLabel(formatMass(metric), "record-value");
        HBox.setHgrow(mass, Priority.ALWAYS);
        HBox row = new HBox(12, UiComponents.cardLabel(metric.measurementDate().format(
                DateTimeFormatter.ofPattern("d MMM uuuu")), "record-title"), mass);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("body-mass-history-row");
        return new VBox(row);
    }

    private static String formatMass(BodyMetric metric) {
        return metric.weightKilograms().stripTrailingZeros().toPlainString() + " kg";
    }

    static String changeText(List<BodyMetric> history) {
        if (history.size() < 2) {
            return "First recorded measurement";
        }
        BigDecimal difference = history.getFirst().weightKilograms()
                .subtract(history.get(1).weightKilograms());
        if (difference.signum() == 0) {
            return "No change since your previous reading";
        }
        String direction = difference.signum() < 0 ? "↓ " : "↑ ";
        return direction + difference.abs().stripTrailingZeros().toPlainString()
                + " kg since your previous reading";
    }

    private static boolean confirmDelete(Node owner) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Delete this body-mass reading?",
                ButtonType.YES, ButtonType.NO);
        UiComponents.styleDialog(confirmation, owner, "body-mass-delete-dialog", false);
        return confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private static BigDecimal value(TextField kilograms) {
        try {
            return new BigDecimal(kilograms.getText().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Body mass must be a number", exception);
        }
    }

    private static void run(Runnable action, Runnable reload) {
        Thread.startVirtualThread(() -> {
            try {
                action.run();
                Platform.runLater(reload);
            } catch (RuntimeException exception) {
                Platform.runLater(() -> error(exception));
            }
        });
    }

    private static void error(RuntimeException exception) {
        Alert dialog = new Alert(Alert.AlertType.ERROR, exception.getMessage());
        UiComponents.iconDialog(dialog);
        dialog.showAndWait();
    }

    private static final class ChartPanel extends VBox {
        private final LineChart<String, Number> chart;
        private final Label empty = new Label();
        private List<BodyMetric> history = List.of();
        private LocalDate startDate = LocalDate.now().minusMonths(3);
        private LocalDate endDate = LocalDate.now();

        ChartPanel() {
            Label title = UiComponents.cardLabel("Weight", "section-title");
            ComboBox<String> range = new ComboBox<>();
            range.getItems().addAll("1 month", "3 months", "6 months", "1 year", "All time");
            range.setValue("3 months");
            range.setAccessibleText("Weight chart period");
            Button custom = new Button("Custom");
            custom.getStyleClass().add("body-mass-period");
            DatePicker from = new DatePicker(startDate);
            DatePicker to = new DatePicker(endDate);
            UiComponents.calendarOnly(from, to);
            from.setPrefWidth(150);
            from.setMinWidth(150);
            to.setPrefWidth(150);
            to.setMinWidth(150);
            Button apply = new Button("Apply");
            apply.getStyleClass().add("secondary-button");
            HBox customRange = new HBox(8, new Label("From"), from, new Label("To"), to, apply);
            customRange.setAlignment(Pos.CENTER_LEFT);
            customRange.setVisible(false);
            customRange.setManaged(false);
            Label rangeLabel = new Label("Recent changes");
            rangeLabel.getStyleClass().add("muted-text");
            VBox chartCopy = new VBox(2, title, rangeLabel);
            HBox header = new HBox(10, chartCopy);
            header.setAlignment(Pos.CENTER_LEFT);
            HBox controls = new HBox(8, range, custom, customRange);
            controls.setAlignment(Pos.CENTER_LEFT);
            CategoryAxis xAxis = new CategoryAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setLabel("Date");
            yAxis.setLabel("kg");
            chart = new LineChart<>(xAxis, yAxis);
            chart.setLegendVisible(false);
            chart.setAnimated(false);
            chart.setCreateSymbols(true);
            chart.setPrefHeight(260);
            chart.getStyleClass().add("body-mass-chart");
            empty.getStyleClass().add("muted-text");
            getChildren().addAll(header, controls, chart, empty);
            range.setOnAction(event -> {
                applyPreset(range.getValue());
                customRange.setVisible(false);
                customRange.setManaged(false);
                custom.setText("Custom");
                custom.setAccessibleText("Show custom chart date range");
            });
            custom.setOnAction(event -> {
                boolean showCustomRange = !customRange.isVisible();
                customRange.setVisible(showCustomRange);
                customRange.setManaged(showCustomRange);
                custom.setText("Custom");
                custom.setAccessibleText(showCustomRange ? "Hide custom chart date range"
                        : "Show custom chart date range");
                if (showCustomRange) {
                    from.show();
                }
            });
            apply.setOnAction(event -> {
                if (from.getValue() == null || to.getValue() == null || from.getValue().isAfter(to.getValue())) {
                    error(new IllegalArgumentException("Choose a valid chart date range"));
                    return;
                }
                startDate = from.getValue();
                endDate = to.getValue();
                show(history);
            });
        }

        void show(List<BodyMetric> readings) {
            history = List.copyOf(readings);
            List<BodyMetric> trend = historyBetween(history, startDate, endDate);
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            trend.forEach(metric -> series.getData().add(new XYChart.Data<>(
                    CHART_DATE.format(metric.measurementDate()), metric.weightKilograms())));
            chart.getData().clear();
            chart.getData().add(series);
            boolean visible = trend.size() > 1;
            chart.setVisible(visible);
            chart.setManaged(visible);
            empty.setText(visible ? "" : "Add at least two readings in this period to see your trend.");
        }

        private void applyPreset(String preset) {
            endDate = LocalDate.now();
            startDate = switch (preset) {
            case "1 month" -> endDate.minusMonths(1).plusDays(1);
            case "3 months" -> endDate.minusMonths(3).plusDays(1);
            case "6 months" -> endDate.minusMonths(6).plusDays(1);
            case "1 year" -> endDate.minusYears(1).plusDays(1);
            case "All time" -> null;
            default -> throw new IllegalArgumentException("Unknown chart period");
            };
            show(history);
        }
    }

    static List<BodyMetric> recentHistory(List<BodyMetric> history, int days, LocalDate today) {
        LocalDate first = today.minusDays(days - 1L);
        return history.stream().filter(metric -> !metric.measurementDate().isBefore(first))
                .sorted(Comparator.comparing(BodyMetric::measurementDate)).toList();
    }

    static List<BodyMetric> historyBetween(List<BodyMetric> history, LocalDate start, LocalDate end) {
        return history.stream().filter(metric -> (start == null || !metric.measurementDate().isBefore(start))
                && (end == null || !metric.measurementDate().isAfter(end)))
                .sorted(Comparator.comparing(BodyMetric::measurementDate)).toList();
    }

    private static final class EditorState {
        private boolean touched;

        boolean touched() {
            return touched;
        }

        void setTouched(boolean value) {
            touched = value;
        }
    }
}
