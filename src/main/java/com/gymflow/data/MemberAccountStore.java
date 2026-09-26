package com.gymflow.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.gymflow.model.Member;
import com.gymflow.model.Membership;

/** Reads account-owned profile and Membership data for Member screens. */
public final class MemberAccountStore {
    private final GymFlowDatabase database;

    /** Creates a Member account store backed by the supplied database. */
    public MemberAccountStore(GymFlowDatabase database) {
        this.database = database;
    }

    /** Loads the profile belonging to the supplied active Member account. */
    public Member profile(long accountId) {
        String sql = """
                SELECT a.id, a.email, p.member_number, p.full_name, p.phone_number, p.date_of_birth
                FROM accounts a JOIN member_profiles p ON p.account_id = a.id
                WHERE a.id = ? AND a.role = 'MEMBER' AND a.is_active = 1
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, accountId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new IllegalArgumentException("Active Member account is required");
                }
                return new Member(results.getLong("id"), results.getString("member_number"),
                        results.getString("email"), results.getString("full_name"),
                        results.getString("phone_number"), date(results, "date_of_birth"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load Member profile", exception);
        }
    }

    /** Loads only the supplied Member account's Memberships, newest first. */
    public List<Membership> membershipHistory(long accountId) {
        String sql = """
                SELECT id, member_account_id, start_date, expiry_date, is_active, created_at, updated_at
                FROM memberships WHERE member_account_id = ?
                ORDER BY start_date DESC, id DESC
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, accountId);
            try (ResultSet results = statement.executeQuery()) {
                List<Membership> memberships = new ArrayList<>();
                while (results.next()) {
                    memberships.add(new Membership(results.getLong("id"),
                            results.getLong("member_account_id"),
                            LocalDate.parse(results.getString("start_date")),
                            LocalDate.parse(results.getString("expiry_date")),
                            results.getBoolean("is_active"), Instant.parse(results.getString("created_at")),
                            Instant.parse(results.getString("updated_at"))));
                }
                return memberships;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load Member Memberships", exception);
        }
    }

    /** Updates only the supplied active Member's email and phone number. */
    public Member updateContact(long accountId, String email, String phoneNumber) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                Instant updatedAt = Instant.now();
                try (PreparedStatement account = connection.prepareStatement("""
                        UPDATE accounts SET email = ?, updated_at = ?
                        WHERE id = ? AND role = 'MEMBER' AND is_active = 1
                        """)) {
                    account.setString(1, email);
                    account.setString(2, updatedAt.toString());
                    account.setLong(3, accountId);
                    if (account.executeUpdate() != 1) {
                        throw new IllegalArgumentException("An active Member account is required");
                    }
                } catch (SQLException exception) {
                    if (exception.getMessage().contains("UNIQUE")) {
                        throw new IllegalArgumentException("A Member with that email already exists", exception);
                    }
                    throw exception;
                }
                try (PreparedStatement profile = connection.prepareStatement(
                        "UPDATE member_profiles SET phone_number = ? WHERE account_id = ?")) {
                    profile.setString(1, phoneNumber);
                    profile.setLong(2, accountId);
                    if (profile.executeUpdate() != 1) {
                        throw new IllegalArgumentException("An active Member account is required");
                    }
                }
                connection.commit();
                return profile(accountId);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update Member contact details", exception);
        }
    }

    private static LocalDate date(ResultSet results, String column) throws SQLException {
        String value = results.getString(column);
        return value == null ? null : LocalDate.parse(value);
    }
}
