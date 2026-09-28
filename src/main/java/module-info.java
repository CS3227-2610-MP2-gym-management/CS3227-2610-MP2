/**
 * Provides the GymFlow JavaFX desktop application.
 */
module com.gymflow {
    requires java.logging;
    requires java.net.http;
    requires java.prefs;
    requires java.sql;
    requires static jdk.httpserver;
    requires transitive javafx.graphics;
    requires javafx.controls;
    requires org.xerial.sqlitejdbc;
    requires transitive com.fasterxml.jackson.databind;

    exports com.gymflow.auth;
    exports com.gymflow.announcement;
    exports com.gymflow.config;
    exports com.gymflow.data;
    exports com.gymflow.expense;
    exports com.gymflow.member;
    exports com.gymflow.metric;
    exports com.gymflow.model;
    exports com.gymflow.ui to javafx.graphics;
    exports com.gymflow.visit;
    exports com.gymflow.workout;
}
