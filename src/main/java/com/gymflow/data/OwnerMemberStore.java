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
import com.gymflow.member.AddMembershipRequest;
import com.gymflow.model.Member;
import com.gymflow.model.MemberPayment;
import com.gymflow.model.Membership;
import com.gymflow.model.MembershipOverview;
import com.gymflow.model.OwnerDashboard;
import com.gymflow.model.PaymentMethod;
import com.gymflow.model.PaymentOverview;

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
                SELECT p.id, p.membership_id, p.amount_cents, p.method, p.paid_at,
                    p.reference, p.recorded_by_account_id, p.created_at
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

    /** Searches all Payments by Member name or email. */
    public List<PaymentOverview> searchPayments(String query) {
        String pattern = "%" + escape(query.toLowerCase(Locale.ROOT)) + "%";
        String sql = """
                SELECT pay.*, p.member_number, p.full_name, a.email,
                    m.start_date, m.expiry_date
                FROM payments pay
                JOIN memberships m ON m.id = pay.membership_id
                JOIN member_profiles p ON p.account_id = m.member_account_id
                JOIN accounts a ON a.id = p.account_id
                WHERE lower(p.full_name) LIKE ? ESCAPE '\\'
                   OR lower(a.email) LIKE ? ESCAPE '\\'
                ORDER BY pay.paid_at DESC, pay.id DESC
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            try (ResultSet results = statement.executeQuery()) {
                List<PaymentOverview> found = new ArrayList<>();
                while (results.next()) {
                    found.add(new PaymentOverview(readPayment(results),
                            results.getString("member_number"), results.getString("full_name"),
                            results.getString("email"), LocalDate.parse(results.getString("start_date")),
                            LocalDate.parse(results.getString("expiry_date"))));
                }
                return found;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to search Payments", exception);
        }
    }

    /** Loads the summary figures and recent Members shown on the Owner dashboard. */
    public OwnerDashboard ownerDashboard(LocalDate today, Instant monthStart,
            Instant nextMonthStart) {
        try (Connection connection = database.connect()) {
            long totalMembers = scalar(connection,
                    "SELECT COUNT(*) FROM member_profiles");
            long activeMemberships = scalar(connection, """
                    SELECT COUNT(DISTINCT member_account_id) FROM memberships
                    WHERE is_active = 1 AND start_date <= ? AND expiry_date >= ?
                    """, today.toString(), today.toString());
            long revenueCents = scalar(connection, """
                    SELECT COALESCE(SUM(amount_cents), 0) FROM payments
                    WHERE paid_at >= ? AND paid_at < ?
                    """, monthStart.toString(), nextMonthStart.toString());
            return new OwnerDashboard(totalMembers, activeMemberships,
                    BigDecimal.valueOf(revenueCents, 2), recentMembers(connection, today));
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load Owner overview", exception);
        }
    }

    private static List<MembershipOverview> recentMembers(Connection connection, LocalDate today)
            throws SQLException {
        String sql = """
                SELECT a.email, p.member_number, p.full_name,
                    m.id AS membership_id, m.member_account_id, m.start_date, m.expiry_date,
                    m.is_active, m.created_at AS membership_created_at,
                    m.updated_at AS membership_updated_at
                FROM member_profiles p
                JOIN accounts a ON a.id = p.account_id
                LEFT JOIN memberships m ON m.id = (
                    SELECT candidate.id FROM memberships candidate
                    WHERE candidate.member_account_id = p.account_id
                    ORDER BY
                        CASE
                            WHEN candidate.is_active = 1
                                AND candidate.start_date <= ? AND candidate.expiry_date >= ? THEN 0
                            WHEN candidate.is_active = 1 AND candidate.start_date > ? THEN 1
                            ELSE 2
                        END,
                        CASE WHEN candidate.is_active = 1 AND candidate.start_date > ?
                            THEN candidate.start_date END,
                        candidate.expiry_date DESC, candidate.id DESC
                    LIMIT 1
                )
                ORDER BY a.created_at DESC, a.id DESC
                LIMIT 5
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 1; index <= 4; index++) {
                statement.setString(index, today.toString());
            }
            try (ResultSet results = statement.executeQuery()) {
                List<MembershipOverview> found = new ArrayList<>();
                while (results.next()) {
                    Membership membership = results.getObject("membership_id") == null ? null
                            : new Membership(results.getLong("membership_id"),
                                    results.getLong("member_account_id"),
                                    LocalDate.parse(results.getString("start_date")),
                                    LocalDate.parse(results.getString("expiry_date")),
                                    results.getBoolean("is_active"),
                                    Instant.parse(results.getString("membership_created_at")),
                                    Instant.parse(results.getString("membership_updated_at")));
                    found.add(new MembershipOverview(membership,
                            results.getString("member_number"), results.getString("full_name"),
                            results.getString("email")));
                }
                return found;
            }
        }
    }

    private static long scalar(Connection connection, String sql, String... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.getLong(1);
            }
        }
    }

    /** Lists a Member's purchased Membership periods, newest first. */
    public List<Membership> membershipHistory(long memberAccountId) {
        String sql = """
                SELECT * FROM memberships WHERE member_account_id = ?
                ORDER BY start_date DESC, id DESC
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberAccountId);
            try (ResultSet results = statement.executeQuery()) {
                List<Membership> found = new ArrayList<>();
                while (results.next()) {
                    found.add(readMembership(results));
                }
                return found;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load Memberships", exception);
        }
    }

    /** Lists Memberships whose Member name or email matches the query. */
    public List<MembershipOverview> searchMemberships(String query) {
        String pattern = "%" + escape(query.toLowerCase(Locale.ROOT)) + "%";
        String sql = """
                SELECT m.*, p.member_number, p.full_name, a.email
                FROM memberships m
                JOIN member_profiles p ON p.account_id = m.member_account_id
                JOIN accounts a ON a.id = p.account_id
                WHERE lower(p.full_name) LIKE ? ESCAPE '\\'
                   OR lower(a.email) LIKE ? ESCAPE '\\'
                ORDER BY p.full_name, m.start_date DESC, m.id DESC
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            try (ResultSet results = statement.executeQuery()) {
                List<MembershipOverview> found = new ArrayList<>();
                while (results.next()) {
                    found.add(new MembershipOverview(readMembership(results),
                            results.getString("member_number"), results.getString("full_name"),
                            results.getString("email")));
                }
                return found;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to search Memberships", exception);
        }
    }

    /** Atomically creates one Membership and its Payment. */
    public Membership addMembership(AddMembershipRequest request, long ownerAccountId) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                requireOwner(connection, ownerAccountId);
                requireMember(connection, request.memberId());
                requireNoActiveOverlap(connection, request.memberId(), request.startDate(),
                        request.expiryDate(), 0);
                Instant now = Instant.now();
                long membershipId = insertMembership(connection, request.memberId(),
                        request.startDate(), request.expiryDate(), now);
                insertPayment(connection, membershipId, request.paymentAmount(),
                        request.paymentMethod(), request.paidAt(), request.paymentReference(),
                        ownerAccountId, now);
                connection.commit();
                return new Membership(membershipId, request.memberId(), request.startDate(),
                        request.expiryDate(), true, now, now);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to add Membership", exception);
        }
    }

    /** Activates or deactivates one Membership. */
    public Membership setMembershipActive(long membershipId, boolean active,
            long ownerAccountId) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                requireOwner(connection, ownerAccountId);
                Membership membership = findMembership(connection, membershipId);
                if (active) {
                    if (membership.expiryDate().isBefore(LocalDate.now())) {
                        throw new IllegalArgumentException("Expired Memberships cannot be reactivated");
                    }
                    requireNoActiveOverlap(connection, membership.memberId(),
                            membership.startDate(), membership.expiryDate(), membership.id());
                }
                Instant updatedAt = Instant.now();
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE memberships SET is_active = ?, updated_at = ? WHERE id = ?")) {
                    statement.setBoolean(1, active);
                    statement.setString(2, updatedAt.toString());
                    statement.setLong(3, membershipId);
                    statement.executeUpdate();
                }
                connection.commit();
                return new Membership(membership.id(), membership.memberId(), membership.startDate(),
                        membership.expiryDate(), active, membership.createdAt(), updatedAt);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update Membership", exception);
        }
    }

    /** Returns whether any active Membership covers the supplied date. */
    public boolean hasValidMembership(long memberAccountId, LocalDate date) {
        String sql = """
                SELECT 1 FROM memberships
                WHERE member_account_id = ? AND is_active = 1
                  AND start_date <= ? AND expiry_date >= ?
                LIMIT 1
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberAccountId);
            statement.setString(2, date.toString());
            statement.setString(3, date.toString());
            return statement.executeQuery().next();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to check Membership", exception);
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
                        "UPDATE accounts SET email = ?, updated_at = ? WHERE id = ? AND role = 'MEMBER'")) {
                    statement.setString(1, email);
                    statement.setString(2, Instant.now().toString());
                    statement.setLong(3, accountId);
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

    /** Replaces a Member's password when the requesting account is an active Owner. */
    public void updatePassword(long memberAccountId, PasswordHash password,
            long ownerAccountId) {
        String sql = """
                UPDATE accounts
                SET password_hash = ?, password_salt = ?, password_iterations = ?, updated_at = ?
                WHERE id = ? AND role = 'MEMBER'
                  AND EXISTS (
                      SELECT 1 FROM accounts owner
                      WHERE owner.id = ? AND owner.role = 'OWNER' AND owner.is_active = 1
                  )
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, password.hash());
            statement.setString(2, password.salt());
            statement.setInt(3, password.iterations());
            statement.setString(4, Instant.now().toString());
            statement.setLong(5, memberAccountId);
            statement.setLong(6, ownerAccountId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("An active Owner and Member are required");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to reset Member password", exception);
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

    private static void requireMember(Connection connection, long memberAccountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM member_profiles p JOIN accounts a ON a.id = p.account_id
                WHERE p.account_id = ? AND a.role = 'MEMBER'
                """)) {
            statement.setLong(1, memberAccountId);
            if (!statement.executeQuery().next()) {
                throw new IllegalArgumentException("Member not found");
            }
        }
    }

    private static void requireNoActiveOverlap(Connection connection, long memberAccountId,
            LocalDate start, LocalDate expiry, long excludedId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM memberships
                WHERE member_account_id = ? AND is_active = 1 AND id <> ?
                  AND start_date <= ? AND expiry_date >= ?
                LIMIT 1
                """)) {
            statement.setLong(1, memberAccountId);
            statement.setLong(2, excludedId);
            statement.setString(3, expiry.toString());
            statement.setString(4, start.toString());
            if (statement.executeQuery().next()) {
                throw new IllegalArgumentException("Membership dates overlap an active Membership");
            }
        }
    }

    private static long insertAccount(Connection connection, String email,
            PasswordHash password) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO accounts(email, password_hash, password_salt, password_iterations,
                    role, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, 'MEMBER', 1, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, email);
            statement.setString(2, password.hash());
            statement.setString(3, password.salt());
            statement.setInt(4, password.iterations());
            String now = Instant.now().toString();
            statement.setString(5, now);
            statement.setString(6, now);
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
        return insertMembership(connection, accountId, request.membershipStart(),
                request.membershipExpiry(), Instant.now());
    }

    private static long insertMembership(Connection connection, long accountId,
            LocalDate start, LocalDate expiry, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO memberships(member_account_id, start_date, expiry_date, is_active,
                    created_at, updated_at) VALUES (?, ?, ?, 1, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, accountId);
            statement.setString(2, start.toString());
            statement.setString(3, expiry.toString());
            statement.setString(4, now.toString());
            statement.setString(5, now.toString());
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private static void insertPayment(Connection connection, long membershipId,
            CreateMemberRequest request, long ownerAccountId) throws SQLException {
        insertPayment(connection, membershipId, request.paymentAmount(), request.paymentMethod(),
                request.paidAt(), request.paymentReference(), ownerAccountId, Instant.now());
    }

    private static void insertPayment(Connection connection, long membershipId,
            BigDecimal amount, PaymentMethod method, Instant paidAt, String reference,
            long ownerAccountId, Instant createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payments(membership_id, amount_cents, method, paid_at, reference,
                    recorded_by_account_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, membershipId);
            statement.setLong(2, amount.movePointRight(2).longValueExact());
            statement.setString(3, method.name());
            statement.setString(4, paidAt.toString());
            statement.setString(5, blankToNull(reference));
            statement.setLong(6, ownerAccountId);
            statement.setString(7, createdAt.toString());
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
        return new MemberPayment(results.getLong("id"), results.getLong("membership_id"), amount,
                PaymentMethod.valueOf(results.getString("method")),
                Instant.parse(results.getString("paid_at")), reference == null ? "" : reference,
                results.getLong("recorded_by_account_id"),
                Instant.parse(results.getString("created_at")));
    }

    private static Membership readMembership(ResultSet results) throws SQLException {
        return new Membership(results.getLong("id"), results.getLong("member_account_id"),
                LocalDate.parse(results.getString("start_date")),
                LocalDate.parse(results.getString("expiry_date")), results.getBoolean("is_active"),
                Instant.parse(results.getString("created_at")),
                Instant.parse(results.getString("updated_at")));
    }

    private static Membership findMembership(Connection connection, long membershipId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM memberships WHERE id = ?")) {
            statement.setLong(1, membershipId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new IllegalArgumentException("Membership not found");
                }
                return readMembership(results);
            }
        }
    }

    private static String escape(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
