/**
 * Provides the GymFlow JavaFX desktop application.
 */
module com.gymflow {
    requires java.logging;
    requires java.prefs;
    requires java.sql;
    requires transitive javafx.graphics;
    requires javafx.controls;
    requires org.xerial.sqlitejdbc;

    exports com.gymflow.auth;
    exports com.gymflow.announcement;
    exports com.gymflow.data;
    exports com.gymflow.expense;
    exports com.gymflow.member;
    exports com.gymflow.metric;
    exports com.gymflow.model;
    exports com.gymflow.ui to javafx.graphics;
    exports com.gymflow.visit;
}
