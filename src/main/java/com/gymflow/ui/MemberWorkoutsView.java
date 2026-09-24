package com.gymflow.ui;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.gymflow.model.Account;
import com.gymflow.model.SaveWorkoutRequest;
import com.gymflow.model.Workout;
import com.gymflow.model.WorkoutSet;
import com.gymflow.model.WorkoutSetInput;
import com.gymflow.workout.WorkoutService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Member self-service view for completed Workout history and editing. */
final class MemberWorkoutsView {
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
        String completed = "Completed: " + workout.performedAt().atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        String details = workout.sets().size() + " set(s)";
        if (workout.notes() != null) {
            details += " — " + workout.notes();
        }
        VBox card = new VBox(6, UiComponents.cardLabel(completed, "record-title"),
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

    private static void form(javafx.scene.Node owner, WorkoutService service, Account session,
            Workout existing, Runnable refresh) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Record Workout" : "Edit Workout");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        javafx.scene.Node standardCancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        standardCancel.setManaged(false);
        standardCancel.setVisible(false);
        Button save = new Button("Save");
        Button delete = new Button("Delete");
        Button cancel = new Button("Cancel");
        TextField completed = new TextField(completedText(existing));
        TextArea notes = new TextArea(existing == null || existing.notes() == null
                ? "" : existing.notes());
        TextArea sets = new TextArea(existing == null ? "Squat|8||60" : formatSets(existing.sets()));
        sets.setPrefRowCount(6);
        Label status = UiComponents.statusLabel();
        VBox body = new VBox(10, new Label("Completed (yyyy-MM-ddTHH:mm:ss)"), completed,
                new Label("Notes"), notes,
                new Label("Sets: exercise|repetitions|duration seconds|resistance kg (one per line)"),
                sets, status);
        HBox actions = new HBox(8, save, cancel);
        if (existing != null) {
            actions.getChildren().add(delete);
        }
        body.getChildren().add(actions);
        dialog.getDialogPane().setContent(body);
        UiComponents.styleDialog(dialog, owner, "workout-dialog", true);
        save.setOnAction(event -> save(dialog, status, service, session, existing,
                completed, notes, sets, refresh));
        delete.setOnAction(event -> delete(dialog, status, service, session, existing, refresh));
        cancel.setOnAction(event -> close(dialog));
        dialog.show();
    }

    private static String completedText(Workout workout) {
        LocalDateTime value = workout == null ? LocalDateTime.now()
                : workout.performedAt().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return value.withNano(0).toString();
    }

    private static void save(Dialog<Void> dialog, Label status, WorkoutService service,
            Account session, Workout existing, TextField completed, TextArea notes, TextArea sets,
            Runnable refresh) {
        try {
            SaveWorkoutRequest request = request(completed, notes, sets);
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

    private static SaveWorkoutRequest request(TextField completed, TextArea notes, TextArea sets) {
        return new SaveWorkoutRequest(LocalDateTime.parse(completed.getText().trim())
                .atZone(ZoneId.systemDefault()).toInstant(), notes.getText(), parseSets(sets.getText()));
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

    private static List<WorkoutSetInput> parseSets(String text) {
        List<WorkoutSetInput> inputs = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException("Each set needs four pipe-separated columns");
            }
            BigDecimal resistance = parts[3].isBlank() ? null : new BigDecimal(parts[3].trim());
            inputs.add(new WorkoutSetInput(parts[0], number(parts[1]), number(parts[2]), resistance));
        }
        return inputs;
    }

    private static Integer number(String value) {
        return value.isBlank() ? null : Integer.valueOf(value.trim());
    }

    private static String formatSets(List<WorkoutSet> sets) {
        return sets.stream().map(MemberWorkoutsView::formatSet)
                .reduce((first, second) -> first + "\n" + second).orElse("");
    }

    private static String formatSet(WorkoutSet set) {
        return set.exerciseName() + "|" + optional(set.repetitions()) + "|"
                + optional(set.durationSeconds()) + "|" + optional(set.resistanceKilograms());
    }

    private static String optional(Object value) {
        return value == null ? "" : value.toString();
    }
}
