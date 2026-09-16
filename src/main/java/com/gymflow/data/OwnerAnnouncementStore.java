package com.gymflow.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.gymflow.model.Announcement;

/** Persists Owner-published announcements. */
public final class OwnerAnnouncementStore {
    private final GymFlowDatabase database;

    /** Creates a store backed by the supplied database. */
    public OwnerAnnouncementStore(GymFlowDatabase database) {
        this.database = database;
    }

    /** Publishes an announcement when requested by an active Owner. */
    public Announcement publish(String title, String content, long ownerAccountId) {
        String sql = """
                INSERT INTO announcements(title, content, published_at, created_by_account_id,
                    created_at, updated_at)
                SELECT ?, ?, ?, id, ?, ? FROM accounts
                WHERE id = ? AND role = 'OWNER' AND is_active = 1
                """;
        Instant now = Instant.now();
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, title);
            statement.setString(2, content);
            statement.setString(3, now.toString());
            statement.setString(4, now.toString());
            statement.setString(5, now.toString());
            statement.setLong(6, ownerAccountId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("An active Owner is required");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated Announcement ID returned");
                }
                return new Announcement(keys.getLong(1), title, content, now,
                        ownerAccountId, null, now, now);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to publish Announcement", exception);
        }
    }

    /** Lists published or withdrawn announcements newest first. */
    public List<Announcement> list(boolean withdrawn) {
        String sql = "SELECT * FROM announcements WHERE withdrawn_at IS "
                + (withdrawn ? "NOT NULL ORDER BY withdrawn_at DESC, id DESC"
                        : "NULL ORDER BY published_at DESC, id DESC");
        try (Connection connection = database.connect();
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery(sql)) {
            List<Announcement> found = new ArrayList<>();
            while (results.next()) {
                found.add(read(results));
            }
            return found;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load Announcements", exception);
        }
    }

    /** Withdraws one published announcement when requested by an active Owner. */
    public Announcement withdraw(long announcementId, long ownerAccountId) {
        String sql = """
                UPDATE announcements SET withdrawn_at = ?, updated_at = ?
                WHERE id = ? AND withdrawn_at IS NULL AND EXISTS (
                    SELECT 1 FROM accounts WHERE id = ? AND role = 'OWNER' AND is_active = 1)
                RETURNING *
                """;
        Instant now = Instant.now();
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, now.toString());
            statement.setString(2, now.toString());
            statement.setLong(3, announcementId);
            statement.setLong(4, ownerAccountId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Published Announcement not found");
                }
                return read(result);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to withdraw Announcement", exception);
        }
    }

    private static Announcement read(ResultSet result) throws SQLException {
        String withdrawnAt = result.getString("withdrawn_at");
        return new Announcement(result.getLong("id"), result.getString("title"),
                result.getString("content"), Instant.parse(result.getString("published_at")),
                result.getLong("created_by_account_id"),
                withdrawnAt == null ? null : Instant.parse(withdrawnAt),
                Instant.parse(result.getString("created_at")),
                Instant.parse(result.getString("updated_at")));
    }
}
