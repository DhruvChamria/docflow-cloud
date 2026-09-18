package edu.manipal.ccd.local;

import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Local account/session persistence. Session tokens are stored only as SHA-256 hashes. */
public class AccountStore {
    private final JdbcTemplate db;
    public record Account(UUID id,String email) {}
    public record StoredAccount(UUID id,String email,String passwordHash) {}
    public AccountStore(String url,String user,String password) { this(new DriverManagerDataSource(url,user,password)); }
    public AccountStore(DataSource source) {
        db=new JdbcTemplate(source);
        db.execute("""
            CREATE TABLE IF NOT EXISTS accounts(
              id uuid PRIMARY KEY,email varchar(254) NOT NULL UNIQUE,password_hash text NOT NULL,
              created_at timestamptz NOT NULL DEFAULT now())
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS account_sessions(
              token_hash varchar(64) PRIMARY KEY,account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
              created_at timestamptz NOT NULL DEFAULT now(),expires_at timestamptz NOT NULL)
            """);
        db.execute("CREATE INDEX IF NOT EXISTS account_sessions_expiry ON account_sessions(expires_at)");
    }
    public Optional<Account> register(String email,String passwordHash) {
        return db.query("INSERT INTO accounts(id,email,password_hash) VALUES(?,?,?) ON CONFLICT(email) DO NOTHING RETURNING id,email",
            (rs,row)->new Account(rs.getObject("id",UUID.class),rs.getString("email")),UUID.randomUUID(),email,passwordHash).stream().findFirst();
    }
    public Optional<StoredAccount> byEmail(String email) {
        return db.query("SELECT id,email,password_hash FROM accounts WHERE email=?",(rs,row)->new StoredAccount(
            rs.getObject("id",UUID.class),rs.getString("email"),rs.getString("password_hash")),email).stream().findFirst();
    }
    public void createSession(UUID account,String tokenHash,Instant expires) {
        db.update("DELETE FROM account_sessions WHERE expires_at<=now()");
        db.update("INSERT INTO account_sessions(token_hash,account_id,expires_at) VALUES(?,?,?)",tokenHash,account,java.sql.Timestamp.from(expires));
    }
    public Optional<Account> session(String tokenHash) {
        return db.query("""
            SELECT a.id,a.email FROM account_sessions s JOIN accounts a ON a.id=s.account_id
            WHERE s.token_hash=? AND s.expires_at>now()
            """,(rs,row)->new Account(rs.getObject("id",UUID.class),rs.getString("email")),tokenHash).stream().findFirst();
    }
    public void invalidate(String tokenHash) { db.update("DELETE FROM account_sessions WHERE token_hash=?",tokenHash); }
}
