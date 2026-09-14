package com.gymflow.data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.gymflow.auth.PasswordHash;
import com.gymflow.member.CreateMemberRequest;
import com.gymflow.model.Member;
import com.gymflow.model.MemberPayment;
import com.gymflow.model.PaymentMethod;

/** Persists Owner-managed Member records. */
public final class OwnerMemberStore {
    private final GymFlowDatabase database;

    /** Creates a Member store backed by the supplied database. */
    public OwnerMemberStore(GymFlowDatabase database) {
        this.database = database;
    }

    /** Inserts all records required for a newly onboarded Member in one transaction. */
    public Member create(CreateMemberRequest request, String email, String phoneNumber,
            PasswordHash password, long ownerAccountId) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                requireOwner(connection, ownerAccountId);
                long accountId;
                try {
                    accountId = insertAccount(connection, email, password);
                } catch (SQLException exception) {
                    if (exception.getErrorCode() == 19) {
                        throw new IllegalArgumentException("A Member with that email already exists", exception);
                    }
                    throw exception;
                }
                String memberNumber = nextMemberNumber(connection);
                insertProfile(connection, accountId, memberNumber, phoneNumber, request);
                long membershipId = insertMembership(connection, accountId, request);
                insertPayment(connection, membershipId, request, ownerAccountId);
                connection.commit();
                return new Member(accountId, memberNumber, email, request.fullName().trim(),
                        phoneNumber, request.dateOfBirth());
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create Member", exception);
        }
    }

    /** Finds Members matching their name or email. */
    public List<Member> search(String query) {
        String pattern = "%" + escape(query.toLowerCase(Locale.ROOT)) + "%";
        String sql = """
                SELECT a.id, a.email, p.member_number, p.full_name, p.phone_number, p.date_of_birth
                FROM member_profiles p JOIN accounts a ON a.id = p.account_id
                WHERE lower(p.full_name) LIKE ? ESCAPE '\\'
                   OR lower(a.email) LIKE ? ESCAPE '\\'
                ORDER BY p.full_name, p.member_number
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 1; index <= 2; index++) {
                statement.setString(index, pattern);
            }
            try (ResultSet results = statement.executeQuery()) {
                List<Member> found = new ArrayList<>();
                while (results.next()) {
                    found.add(readMember(results));
                }
                return found;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to search Members", exception);
        }
    }

    /** Lists payments recorded for a Member, newest first. */
    public List<MemberPayment> paymentHistory(long memberAccountId) {
        String sql = """
                SELECT p.amount_cents, p.method, p.paid_at, p.reference
                FROM payments p JOIN memberships m ON m.id = p.membership_id
                WHERE m.member_account_id = ?
                ORDER BY p.paid_at DESC, p.id DESC
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberAccountId);
            try (ResultSet results = statement.executeQuery()) {
                List<MemberPayment> found = new ArrayList<>();
                while (results.next()) {
                    found.add(readPayment(results));
                }
                return found;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load Member payments", exception);
        }
    }

    /** Changes only the editable fields of an existing Member. */
    public Member update(long accountId, String email, String fullName,
            String phoneNumber, LocalDate dateOfBirth) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                int accountRows;
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE accounts SET email = ? WHERE id = ? AND role = 'MEMBER'")) {
                    statement.setString(1, email);
                    statement.setLong(2, accountId);
                    try {
                        accountRows = statement.executeUpdate();
                    } catch (SQLException exception) {
                        if (exception.getErrorCode() == 19) {
                            throw new IllegalArgumentException("A Member with that email already exists", exception);
                        }
                        throw exception;
                    }
                }
                if (accountRows != 1) {
                    throw new IllegalArgumentException("Member not found");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE member_profiles SET full_name = ?, phone_number = ?, date_of_birth = ?
                        WHERE account_id = ?
                        """)) {
                    statement.setString(1, fullName);
                    statement.setString(2, phoneNumber);
                    statement.setString(3, dateOfBirth == null ? null : dateOfBirth.toString());
                    statement.setLong(4, accountId);
                    statement.executeUpdate();
                }
                Member updated = find(connection, accountId);
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update Member", exception);
        }
    }

    private static void requireOwner(Connection connection, long ownerAccountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM accounts WHERE id = ? AND role = 'OWNER' AND is_active = 1")) {
            statement.setLong(1, ownerAccountId);
            if (!statement.executeQuery().next()) {
                throw new IllegalArgumentException("An active Owner is required");
            }
        }
    }

    private static long insertAccount(Connection connection, String email,
            PasswordHash password) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO accounts(email, password_hash, password_salt, password_iterations,
                    role, is_active, created_at) VALUES (?, ?, ?, ?, 'MEMBER', 1, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, email);
            statement.setString(2, password.hash());
            statement.setString(3, password.salt());
            statement.setInt(4, password.iterations());
            statement.setString(5, java.time.Instant.now().toString());
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private static String nextMemberNumber(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        SELECT COALESCE(MAX(CAST(SUBSTR(member_number, 2) AS INTEGER)), 0) + 1
                        FROM member_profiles
                        """)) {
            return "M%06d".formatted(result.getLong(1));
        }
    }

    private static void insertProfile(Connection connection, long accountId,
            String memberNumber, String phoneNumber, CreateMemberRequest request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO member_profiles(account_id, member_number, full_name, phone_number, date_of_birth)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, accountId);
            statement.setString(2, memberNumber);
            statement.setString(3, request.fullName().trim());
            statement.setString(4, phoneNumber);
            statement.setString(5, request.dateOfBirth() == null ? null : request.dateOfBirth().toString());
            statement.executeUpdate();
        }
    }

    private static long insertMembership(Connection connection, long accountId,
            CreateMemberRequest request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO memberships(member_account_id, start_date, expiry_date, is_active)
                VALUES (?, ?, ?, 1)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, accountId);
            statement.setString(2, request.membershipStart().toString());
            statement.setString(3, request.membershipExpiry().toString());
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private static void insertPayment(Connection connection, long membershipId,
            CreateMemberRequest request, long ownerAccountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payments(membership_id, amount_cents, method, paid_at, reference,
                    recorded_by_account_id) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, membershipId);
            statement.setLong(2, request.paymentAmount().movePointRight(2).longValueExact());
            statement.setString(3, request.paymentMethod().name());
            statement.setString(4, request.paidAt().toString());
            statement.setString(5, blankToNull(request.paymentReference()));
            statement.setLong(6, ownerAccountId);
            statement.executeUpdate();
        }
    }

    private static long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("No generated ID returned");
            }
            return keys.getLong(1);
        }
    }

    private static Member find(Connection connection, long accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT a.id, a.email, p.member_number, p.full_name, p.phone_number, p.date_of_birth
                FROM member_profiles p JOIN accounts a ON a.id = p.account_id WHERE a.id = ?
                """)) {
            statement.setLong(1, accountId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new IllegalArgumentException("Member not found");
                }
                return readMember(results);
            }
        }
    }

    private static Member readMember(ResultSet results) throws SQLException {
        String birthDate = results.getString("date_of_birth");
        return new Member(results.getLong("id"), results.getString("member_number"),
                results.getString("email"), results.getString("full_name"),
                results.getString("phone_number"), birthDate == null ? null : LocalDate.parse(birthDate));
    }

    private static MemberPayment readPayment(ResultSet results) throws SQLException {
        BigDecimal amount = BigDecimal.valueOf(results.getLong("amount_cents"), 2);
        String reference = results.getString("reference");
        return new MemberPayment(Instant.parse(results.getString("paid_at")), amount,
                PaymentMethod.valueOf(results.getString("method")), reference == null ? "" : reference);
    }

    private static String escape(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
