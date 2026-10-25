import java.sql.*;
import java.util.UUID;

/** Read-only verification of payment IDs printed by demo.mjs; PostgreSQL JDBC driver required. */
class VerifyDemo {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Pass demo payment UUIDs");
        String url = System.getenv("DB_URL");
        if (url == null || !(url.startsWith("jdbc:postgresql://127.0.0.1:")
                || url.startsWith("jdbc:postgresql://localhost:"))) {
            throw new IllegalArgumentException("DB_URL must be an explicit loopback PostgreSQL JDBC URL");
        }
        try (Connection db = DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))) {
            db.setReadOnly(true);
            for (String arg : args) {
                UUID id = UUID.fromString(arg);
                try (PreparedStatement query = db.prepareStatement("""
                        SELECT p.status, p.amount_cents, p.refunded_amount_cents,
                               a.balance_cents AS source_balance, b.balance_cents AS destination_balance
                        FROM payments p JOIN accounts a ON a.id=p.source_account_id
                        JOIN accounts b ON b.id=p.destination_account_id WHERE p.id=?
                        """)) {
                    query.setObject(1, id);
                    try (ResultSet row = query.executeQuery()) {
                        require(row.next(), "Payment missing: " + id);
                        require(row.getString("status").equals("REFUNDED"), "Not fully refunded");
                        require(row.getLong("amount_cents") == 3000 && row.getLong("refunded_amount_cents") == 3000,
                                "Unexpected financial totals");
                        require(row.getLong("source_balance") == 10000 && row.getLong("destination_balance") == 0,
                                "Demo balances differ from 100/0 CAD");
                    }
                }
                require(count(db, "SELECT count(*) FROM payment_ledger_postings WHERE payment_id=?", id) == 3,
                        "Expected exactly capture + two refund postings");
                require(count(db, """
                        SELECT count(*) FROM payment_ledger_postings p
                        JOIN ledger_entries d ON d.id=p.debit_entry_id
                        JOIN ledger_entries c ON c.id=p.credit_entry_id
                        WHERE p.payment_id=? AND d.direction='DEBIT' AND c.direction='CREDIT'
                          AND d.amount_cents=p.amount_cents AND c.amount_cents=p.amount_cents
                          AND d.account_id<>c.account_id AND d.payment_id=p.payment_id AND c.payment_id=p.payment_id
                          AND d.source_event_id=p.source_event_id AND c.source_event_id=p.source_event_id
                        """, id) == 3, "Posting sides are not balanced or event-linked");
                require(count(db, "SELECT count(*) FROM settlement_records WHERE payment_id=? AND status='SETTLED'", id) == 1,
                        "Expected one external settlement");
                require(count(db, "SELECT count(*) FROM reconciliation_exceptions WHERE payment_id=? AND status<>'RESOLVED'", id) == 0,
                        "Unresolved reconciliation exception");
                System.out.println("PASS " + id + " postings=3 balanced_pairs=3 settled_records=1 refunded_cents=3000 balances=10000,0 open_exceptions=0");
            }
        }
    }
    private static long count(Connection db, String sql, UUID id) throws SQLException {
        try (PreparedStatement query = db.prepareStatement(sql)) {
            query.setObject(1, id);
            try (ResultSet row = query.executeQuery()) { row.next(); return row.getLong(1); }
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
