/**
 * Provides the GymFlow JavaFX desktop application.
 */
module com.gymflow {
    requires java.sql;
    requires javafx.controls;
    requires org.xerial.sqlitejdbc;

    exports com.gymflow.auth;
    exports com.gymflow.model;
    exports com.gymflow.ui to javafx.graphics;
}
