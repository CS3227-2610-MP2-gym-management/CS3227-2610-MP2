package com.gymflow.ui;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.gymflow.model.Account;
import com.gymflow.model.SaveWorkoutRequest;
import com.gymflow.model.Workout;
import com.gymflow.model.WorkoutSet;
import com.gymflow.model.WorkoutSetInput;
import com.gymflow.workout.WorkoutService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Member self-service view for completed Workout history and editing. */
final class MemberWorkoutsView {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM uuuu, h:mm a");
    private static final List<String> EXERCISES = List.of("Back Squat", "Barbell Row", "Bench Press",
            "Deadlift", "Lat Pulldown", "Overhead Press", "Plank", "Pull-up", "Running", "Squat");

    private MemberWorkoutsView() {
    }

    static Parent create(WorkoutService service, Account session, Consumer<Screen> navigate,
            Runnable logout) {
        ListView<Workout> list = UiComponents.cardList("No Workouts recorded yet.",
                MemberWorkoutsView::card);
        Label status = new Label("Loading Workouts…");
        status.getStyleClass().add("muted-text");
        Button add = new Button("Record Workout");
        add.getStyleClass().add("primary-button");
        add.setOnAction(event -> form(add, service, session, null,
                () -> load(service, session, list, status)));
        list.setOnMouseClicked(event -> openSelected(list, service, session,
                () -> load(service, session, list, status), event.getClickCount()));
        VBox content = new VBox(20,
                UiComponents.header("Workouts", "Completed sessions, newest first", add),
                UiComponents.card(status, list));
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        load(service, session, list, status);
        return MemberHomeView.shell(content, Screen.MEMBER_WORKOUTS, navigate, logout);
    }

    private static void openSelected(ListView<Workout> list, WorkoutService service,
            Account session, Runnable refresh, int clickCount) {
        Workout workout = list.getSelectionModel().getSelectedItem();
        if (workout != null && clickCount == 2) {
            form(list, service, session, workout, refresh);
        }
    }

    private static VBox card(Workout workout) {
        String range = DATE_TIME.format(local(workout.startedAt())) + " – "
                + DATE_TIME.format(local(workout.endedAt()));
        String details = workout.sets().stream().map(WorkoutSet::exerciseName).distinct()
                .collect(Collectors.joining(" · "));
        if (workout.notes() != null) {
            details += " — " + workout.notes();
        }
        VBox card = new VBox(6, UiComponents.cardLabel(range, "record-title"),
                UiComponents.cardLabel(details, "record-meta"));
        card.getStyleClass().add("record-card");
        return card;
    }

    private static void load(WorkoutService service, Account session, ListView<Workout> list,
            Label status) {
        Thread.startVirtualThread(() -> {
            try {
                List<Workout> workouts = service.history(session);
                Platform.runLater(() -> showLoadedWorkouts(list, status, workouts));
            } catch (RuntimeException exception) {
                Platform.runLater(() -> status.setText("Unable to load your Workouts."));
            }
        });
    }

    private static void showLoadedWorkouts(ListView<Workout> list, Label status,
            List<Workout> workouts) {
        list.getItems().setAll(workouts);
        status.setText(workouts.isEmpty() ? "No Workouts are recorded."
                : "Double-click a Workout to edit or delete it.");
    }

    private static void form(Node owner, WorkoutService service, Account session,
            Workout existing, Runnable refresh) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Record Workout" : "Edit Workout");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Node standardCancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        standardCancel.setManaged(false);
        standardCancel.setVisible(false);
        LocalDateTime end = existing == null ? roundedNow() : local(existing.endedAt());
        LocalDateTime start = existing == null ? end.minusHours(1) : local(existing.startedAt());
        DatePicker date = new DatePicker(end.toLocalDate());
        UiComponents.calendarOnly(date);
        ComboBox<LocalTime> startTime = timePicker(start.toLocalTime(), "Workout start time");
        ComboBox<LocalTime> endTime = timePicker(end.toLocalTime(), "Workout end time");
        Label overnight = new Label("An earlier end time means the workout ends the following day.");
        overnight.getStyleClass().add("muted-text");
        TextArea notes = new TextArea(existing == null || existing.notes() == null ? "" : existing.notes());
        notes.setPromptText("Optional notes");
        VBox exerciseGroups = new VBox(12);
        List<WorkoutSet> savedSets = existing == null ? List.of() : existing.sets();
        if (savedSets.isEmpty()) {
            exerciseGroups.getChildren().add(exerciseGroup(exerciseGroups, null, List.of()));
        } else {
            groupedSets(savedSets).forEach((exercise, sets) ->
                    exerciseGroups.getChildren().add(exerciseGroup(exerciseGroups, exercise, sets)));
        }
        Button addExercise = new Button("Add exercise");
        addExercise.getStyleClass().add("secondary-button");
        addExercise.setOnAction(event -> exerciseGroups.getChildren().add(
                exerciseGroup(exerciseGroups, null, List.of())));
        Label status = UiComponents.statusLabel();
        Button save = new Button("Save Workout");
        save.getStyleClass().add("primary-button");
        Button cancel = new Button("Cancel");
        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger-button");
        javafx.scene.layout.Region actionSpacer = new javafx.scene.layout.Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        HBox actions = new HBox(8, cancel, actionSpacer, save);
        actions.getStyleClass().add("workout-actions");
        if (existing != null) {
            actions.getChildren().add(0, delete);
        }
        Label exercisesLabel = new Label("Exercises and sets");
        exercisesLabel.getStyleClass().add("section-title");
        VBox body = new VBox(12, labeledField("Date", date), labeledField("Time",
                timeFields(startTime, endTime)), overnight, exercisesLabel, exerciseGroups,
                addExercise, new Label("Notes"), notes, status, actions);
        body.getStyleClass().add("dialog-content");
        body.setSpacing(16);
        dialog.getDialogPane().setContent(body);
        UiComponents.styleDialog(dialog, owner, "workout-dialog", true);
        save.setOnAction(event -> save(dialog, status, service, session, existing, date,
                startTime, endTime, notes, exerciseGroups, refresh));
        delete.setOnAction(event -> delete(dialog, status, service, session, existing, refresh));
        cancel.setOnAction(event -> close(dialog));
        dialog.show();
    }

    private static HBox timeFields(ComboBox<LocalTime> start, ComboBox<LocalTime> end) {
        Label startClock = new Label("◷");
        startClock.setAccessibleText("Start time");
        Label endClock = new Label("◷");
        endClock.setAccessibleText("End time");
        HBox fields = new HBox(8, new Label("Start"), startClock, start, new Label("End"), endClock, end);
        fields.getStyleClass().add("workout-time-fields");
        return fields;
    }

    private static HBox labeledField(String label, Node field) {
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("workout-field-label");
        HBox row = new HBox(12, fieldLabel, field);
        row.getStyleClass().add("workout-field");
        return row;
    }

    private static ComboBox<LocalTime> timePicker(LocalTime selected, String accessibleText) {
        ComboBox<LocalTime> picker = new ComboBox<>();
        for (int minutes = 0; minutes < 24 * 60; minutes += 15) {
            picker.getItems().add(LocalTime.of(minutes / 60, minutes % 60));
        }
        picker.setValue(roundDown(selected));
        picker.setAccessibleText(accessibleText + ", 15 minute intervals");
        picker.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(LocalTime value) {
                return value == null ? "" : value.format(DateTimeFormatter.ofPattern("h:mm a"));
            }

            @Override
            public LocalTime fromString(String value) {
                throw new UnsupportedOperationException("Time entry is selected from the list");
            }
        });
        return picker;
    }

    private static Map<String, List<WorkoutSet>> groupedSets(List<WorkoutSet> sets) {
        Map<String, List<WorkoutSet>> grouped = new LinkedHashMap<>();
        for (WorkoutSet set : sets) {
            grouped.computeIfAbsent(set.exerciseName(), ignored -> new ArrayList<>()).add(set);
        }
        return grouped;
    }

    private static VBox exerciseGroup(VBox groups, String exerciseName, List<WorkoutSet> savedSets) {
        ComboBox<String> exercise = new ComboBox<>();
        exercise.getItems().addAll(EXERCISES);
        exercise.setEditable(true);
        exercise.setPromptText("Exercise");
        exercise.setValue(exerciseName);
        exercise.setAccessibleText("Exercise; select or enter a new exercise");
        exercise.getStyleClass().add("workout-exercise-name");
        VBox rows = new VBox(0);
        if (savedSets.isEmpty()) {
            rows.getChildren().add(setRow(rows, new SetValues(true, "", "")));
        } else {
            savedSets.forEach(set -> rows.getChildren().add(setRow(rows, set)));
        }
        Button addSet = new Button("+ Add Set");
        addSet.getStyleClass().add("secondary-button");
        Label count = new Label();
        count.getStyleClass().add("workout-set-count");
        updateSetCount(count, rows);
        rows.getChildren().addListener((javafx.collections.ListChangeListener<Node>) change ->
                updateSetCount(count, rows));
        addSet.setOnAction(event -> rows.getChildren().add(setRow(rows, previous(rows))));
        Button removeExercise = trashButton("Remove exercise");
        HBox heading = new HBox(8, exercise, count, removeExercise);
        heading.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(exercise, Priority.ALWAYS);
        exercise.setMaxWidth(Double.MAX_VALUE);
        VBox group = new VBox(6, heading, setHeader(), rows, addSet);
        group.getStyleClass().add("workout-exercise");
        removeExercise.setOnAction(event -> {
            groups.getChildren().remove(group);
        });
        group.setUserData(new ExerciseFields(exercise, rows));
        return group;
    }

    private static void updateSetCount(Label count, VBox rows) {
        int total = rows.getChildren().size();
        count.setText(total + (total == 1 ? " set" : " sets"));
        for (int index = 0; index < total; index++) {
            SetFields fields = (SetFields) rows.getChildren().get(index).getUserData();
            fields.number().setText(String.valueOf(index + 1));
        }
    }

    private static SetValues previous(VBox rows) {
        if (rows.getChildren().isEmpty()) {
            return new SetValues(true, "", "");
        }
        SetFields previous = (SetFields) rows.getChildren().getLast().getUserData();
        boolean repetitions = previous.measure().getSelectedToggle() == previous.reps();
        return new SetValues(repetitions, previous.amount().getText(), previous.resistance().getText());
    }

    private static Node setHeader() {
        GridPane header = new GridPane();
        setColumns(header);
        header.setHgap(12);
        header.getStyleClass().add("workout-set-header");
        String[] labels = {"SET", "TYPE", "REPS / DURATION", "WEIGHT (KG)", "ACTIONS"};
        for (int column = 0; column < labels.length; column++) {
            header.add(new Label(labels[column]), column, 0);
        }
        return header;
    }

    private static void setColumns(GridPane grid) {
        for (double width : List.of(48.0, 224.0, 154.0, 148.0, 64.0)) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPrefWidth(width);
            column.setMinWidth(width);
            grid.getColumnConstraints().add(column);
        }
    }

    private static Node setRow(VBox rows, WorkoutSet set) {
        return setRow(rows, set == null ? new SetValues(true, "", "") : new SetValues(
                set.durationSeconds() == null, String.valueOf(set.durationSeconds() == null
                        ? set.repetitions() : set.durationSeconds()), set.resistanceKilograms() == null ? ""
                                : set.resistanceKilograms().stripTrailingZeros().toPlainString()));
    }

    private static Node setRow(VBox rows, SetValues values) {
        ToggleButton reps = new ToggleButton("Reps");
        ToggleButton duration = new ToggleButton("Duration");
        reps.getStyleClass().add("workout-measure-toggle");
        duration.getStyleClass().add("workout-measure-toggle");
        reps.setMinWidth(90);
        duration.setMinWidth(116);
        ToggleGroup measure = new ToggleGroup();
        reps.setToggleGroup(measure);
        duration.setToggleGroup(measure);
        (values.repetitions() ? reps : duration).setSelected(true);
        TextField amount = new TextField(values.amount());
        amount.setPromptText(values.repetitions() ? "Reps" : "Seconds");
        measure.selectedToggleProperty().addListener((ignored, oldValue, selected) ->
                amount.setPromptText(selected == duration ? "Seconds" : "Reps"));
        TextField resistance = new TextField(values.resistance());
        resistance.setPromptText("0");
        resistance.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d*") ? change : null));
        Label number = new Label();
        Label kilograms = new Label("kg");
        HBox weight = new HBox(8, resistance, kilograms);
        Button remove = trashButton("Remove set");
        HBox type = new HBox(8, reps, duration);
        GridPane row = new GridPane();
        setColumns(row);
        row.setHgap(12);
        row.add(number, 0, 0);
        row.add(type, 1, 0);
        row.add(amount, 2, 0);
        row.add(weight, 3, 0);
        row.add(remove, 4, 0);
        row.getStyleClass().add("workout-set");
        remove.setOnAction(event -> {
            rows.getChildren().remove(row);
        });
        row.setUserData(new SetFields(number, measure, reps, amount, resistance));
        return row;
    }

    private static Button trashButton(String accessibleText) {
        Button button = new Button("🗑");
        button.setAccessibleText(accessibleText);
        button.getStyleClass().add("trash-button");
        return button;
    }

    private static void save(Dialog<Void> dialog, Label status, WorkoutService service,
            Account session, Workout existing, DatePicker date, ComboBox<LocalTime> start,
            ComboBox<LocalTime> end, TextArea notes, VBox exerciseGroups, Runnable refresh) {
        try {
            SaveWorkoutRequest request = request(date, start, end, notes, exerciseGroups);
            if (existing == null) {
                service.create(session, request);
            } else {
                service.update(session, existing.id(), request);
            }
            close(dialog);
            refresh.run();
        } catch (RuntimeException exception) {
            UiComponents.showStatus(status, exception.getMessage(), true);
        }
    }

    private static SaveWorkoutRequest request(DatePicker date, ComboBox<LocalTime> start,
            ComboBox<LocalTime> end, TextArea notes, VBox exerciseGroups) {
        if (date.getValue() == null || start.getValue() == null || end.getValue() == null) {
            throw new IllegalArgumentException("Date, start time, and end time are required");
        }
        LocalDate endDate = date.getValue();
        LocalDate startDate = start.getValue().isAfter(end.getValue()) ? endDate.minusDays(1) : endDate;
        Instant startedAt = LocalDateTime.of(startDate, start.getValue()).atZone(ZoneId.systemDefault()).toInstant();
        Instant endedAt = LocalDateTime.of(endDate, end.getValue()).atZone(ZoneId.systemDefault()).toInstant();
        List<WorkoutSetInput> sets = new ArrayList<>();
        for (Node group : exerciseGroups.getChildren()) {
            ExerciseFields exerciseFields = (ExerciseFields) group.getUserData();
            String exercise = exerciseFields.exercise().getEditor().getText();
            for (Node row : exerciseFields.rows().getChildren()) {
                SetFields fields = (SetFields) row.getUserData();
                Integer amount = integer(fields.amount().getText());
                BigDecimal resistance = decimal(fields.resistance().getText());
                boolean repetitions = fields.measure().getSelectedToggle() == fields.reps();
                sets.add(new WorkoutSetInput(exercise, repetitions ? amount : null,
                        repetitions ? null : amount, resistance));
            }
        }
        return new SaveWorkoutRequest(startedAt, endedAt, notes.getText(), sets);
    }

    private static Integer integer(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Set measure must be a whole number");
        }
    }

    private static BigDecimal decimal(String text) {
        try {
            return text.isBlank() ? null : new BigDecimal(text.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Resistance must be a number");
        }
    }

    private static LocalDateTime roundedNow() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        return now.minusMinutes(now.getMinute() % 15);
    }

    private static LocalTime roundDown(LocalTime value) {
        return value.minusMinutes(value.getMinute() % 15).withSecond(0).withNano(0);
    }

    private static LocalDateTime local(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static void delete(Dialog<Void> dialog, Label status, WorkoutService service,
            Account session, Workout existing, Runnable refresh) {
        try {
            service.delete(session, existing.id());
            close(dialog);
            refresh.run();
        } catch (RuntimeException exception) {
            UiComponents.showStatus(status, exception.getMessage(), true);
        }
    }

    private static void close(Dialog<Void> dialog) {
        dialog.setResult(null);
        dialog.close();
    }

    private record ExerciseFields(ComboBox<String> exercise, VBox rows) {
    }

    private record SetValues(boolean repetitions, String amount, String resistance) {
    }

    private record SetFields(Label number, ToggleGroup measure, ToggleButton reps,
            TextField amount, TextField resistance) {
    }
}
