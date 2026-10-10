package edu.manipal.ccd.local;

import java.util.*;
import java.sql.*;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL merge vertical-slice adapter. Dispatch intent lives in the same row. */
public class JobStore {
    private final JdbcTemplate db;
    public record Job(UUID id, String owner, String status, int attempts, UUID dispatch,
                      UUID lease, String output, long outputSize, String outputChecksum, String error, Instant created, Instant expires, int files,
                      String operation,String pages,int angle,int copies,int insertAt,String pageSize,String text,
                      String position,int startNumber,int fontSize,double opacity,UUID correlation) {}
    public record Metrics(long queued,long processing,long retrying,long completed,long failed,long cancelled,
                          long retries,long poison,double oldestQueuedSeconds,boolean workerActive) {}
    public record OperationMetrics(long submitted,long completed,long failed,long retries) {}
    public JobStore(String url, String user, String password) {
        this(source(url,user,password));
    }
    private static DataSource source(String url,String user,String password) {
        var source=new DriverManagerDataSource(url,user,password);
        var properties=new Properties();properties.setProperty("connectTimeout","5");properties.setProperty("socketTimeout","10");
        source.setConnectionProperties(properties);return source;
    }
    public JobStore(DataSource source) {
        db = new JdbcTemplate(source);
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(tx -> {
            db.execute("SELECT pg_advisory_xact_lock(690002)");
            db.execute("""
                CREATE TABLE IF NOT EXISTS merge_jobs (
                  id uuid PRIMARY KEY, owner_key varchar(64) NOT NULL,
                  status varchar(16) NOT NULL DEFAULT 'QUEUED',
                  attempts int NOT NULL DEFAULT 0, dispatch_id uuid NOT NULL,
                  pending boolean NOT NULL DEFAULT true,
                  lease_token uuid, lease_until timestamptz,
                  output_key text, error_code text, file_count int NOT NULL,
                  created_at timestamptz NOT NULL DEFAULT now(),
                  expires_at timestamptz NOT NULL DEFAULT now() + interval '24 hours'
                )
                """);
            db.execute("CREATE INDEX IF NOT EXISTS merge_jobs_owner ON merge_jobs(owner_key,created_at)");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS retry_at timestamptz NOT NULL DEFAULT now()");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS operation varchar(32) NOT NULL DEFAULT 'pdf.merge'");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS pages text NOT NULL DEFAULT ''");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS angle int NOT NULL DEFAULT 0");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS copies int NOT NULL DEFAULT 1");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS insert_at int NOT NULL DEFAULT 1");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS page_size varchar(16) NOT NULL DEFAULT 'A4'");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS text_value text NOT NULL DEFAULT ''");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS position varchar(32) NOT NULL DEFAULT 'CENTER'");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS start_number int NOT NULL DEFAULT 1");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS font_size int NOT NULL DEFAULT 12");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS opacity double precision NOT NULL DEFAULT 0.25");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS correlation_id uuid NOT NULL DEFAULT gen_random_uuid()");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS output_size bigint");
            db.execute("ALTER TABLE merge_jobs ADD COLUMN IF NOT EXISTS output_checksum varchar(64)");
            db.execute("CREATE TABLE IF NOT EXISTS worker_heartbeat(worker_type varchar(16) PRIMARY KEY,last_seen timestamptz NOT NULL)");
            db.execute("CREATE TABLE IF NOT EXISTS job_secrets(job_id uuid PRIMARY KEY REFERENCES merge_jobs(id) ON DELETE CASCADE,ciphertext bytea NOT NULL)");
        });
    }
    private Job map(ResultSet rs, int row) throws SQLException {
        return new Job(rs.getObject("id", UUID.class), rs.getString("owner_key"), rs.getString("status"),
            rs.getInt("attempts"), rs.getObject("dispatch_id", UUID.class), rs.getObject("lease_token", UUID.class),
            rs.getString("output_key"),rs.getLong("output_size"),rs.getString("output_checksum"),rs.getString("error_code"),
            rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("expires_at").toInstant(),
            rs.getInt("file_count"),rs.getString("operation"),rs.getString("pages"),rs.getInt("angle"),rs.getInt("copies"),
            rs.getInt("insert_at"),rs.getString("page_size"),rs.getString("text_value"),rs.getString("position"),
            rs.getInt("start_number"),rs.getInt("font_size"),rs.getDouble("opacity"),rs.getObject("correlation_id",UUID.class));
    }
    public void create(UUID id, String owner, int files) {
        db.update("INSERT INTO merge_jobs(id,owner_key,dispatch_id,file_count) VALUES(?,?,?,?)",
            id, owner, UUID.randomUUID(), files);
    }
    public void beginUpload(UUID id,String owner,int files) {
        beginUpload(id,owner,files,new edu.manipal.ccd.core.PdfOptions("pdf.merge","",0));
    }
    public void beginUpload(UUID id,String owner,int files,edu.manipal.ccd.core.PdfOptions options) {
        beginUpload(id,owner,files,options,UUID.randomUUID());
    }
    public void beginUpload(UUID id,String owner,int files,edu.manipal.ccd.core.PdfOptions options,UUID correlation) {
        db.update("""
            INSERT INTO merge_jobs(id,owner_key,dispatch_id,file_count,status,pending,operation,pages,angle,copies,insert_at,page_size,
                text_value,position,start_number,font_size,opacity,correlation_id) VALUES(?,?,?,?,'CREATED',false,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,owner,UUID.randomUUID(),files,options.operation(),options.pages(),options.angle(),options.copies(),options.insertAt(),
            options.pageSize(),options.text(),options.position(),options.startNumber(),options.fontSize(),options.opacity(),correlation);
    }
    public boolean queueUpload(UUID id,String owner) {
        return db.update("UPDATE merge_jobs SET status='QUEUED',pending=true WHERE id=? AND owner_key=? AND status='CREATED' AND expires_at>now()",id,owner)==1;
    }
    public void failUpload(UUID id,String owner) {
        db.update("UPDATE merge_jobs SET status='FAILED',error_code='UPLOAD_FAILED' WHERE id=? AND owner_key=? AND status='CREATED'",id,owner);
    }
    public boolean cancel(UUID id,String owner) {
        return db.update("""
            UPDATE merge_jobs SET status='CANCELLED',pending=false,lease_token=null,lease_until=null,
                error_code=null,dispatch_id=gen_random_uuid()
            WHERE id=? AND owner_key=? AND status IN ('CREATED','QUEUED','PROCESSING','RETRYING')
            """,id,owner)==1;
    }
    public boolean remove(UUID id,String owner) { return db.update("DELETE FROM merge_jobs WHERE id=? AND owner_key=?",id,owner)==1; }
    public void storeSecret(UUID id,byte[] ciphertext) { db.update("INSERT INTO job_secrets(job_id,ciphertext) VALUES(?,?) ON CONFLICT(job_id) DO UPDATE SET ciphertext=excluded.ciphertext",id,ciphertext); }
    public Optional<byte[]> secret(UUID id) { return db.query("SELECT ciphertext FROM job_secrets WHERE job_id=?",(rs,row)->rs.getBytes(1),id).stream().findFirst(); }
    public void deleteSecret(UUID id) { db.update("DELETE FROM job_secrets WHERE job_id=?",id); }
    public boolean healthy() { return Integer.valueOf(1).equals(db.queryForObject("SELECT 1",Integer.class)); }
    public void heartbeat() { heartbeat("PDF"); }
    public void heartbeat(String workerType) {
        if(!Set.of("PDF","OFFICE").contains(workerType))throw new IllegalArgumentException("Unknown worker type");
        db.update("INSERT INTO worker_heartbeat(worker_type,last_seen) VALUES(?,now()) ON CONFLICT(worker_type) DO UPDATE SET last_seen=now()",workerType);
    }
    public Metrics metrics() {
        return db.queryForObject("""
            SELECT count(*) FILTER(WHERE status='QUEUED') queued,count(*) FILTER(WHERE status='PROCESSING') processing,
              count(*) FILTER(WHERE status='RETRYING') retrying,count(*) FILTER(WHERE status='COMPLETED') completed,
              count(*) FILTER(WHERE status='FAILED') failed,count(*) FILTER(WHERE status='CANCELLED') cancelled,
              coalesce(sum(greatest(attempts-1,0)),0) retries,count(*) FILTER(WHERE status='FAILED' AND attempts>=3) poison,
              coalesce(extract(epoch from now()-min(created_at) FILTER(WHERE status IN('QUEUED','RETRYING'))),0) oldest,
              coalesce((SELECT last_seen>now()-interval '15 seconds' FROM worker_heartbeat WHERE worker_type='PDF'),false) active
            FROM merge_jobs
            """,(rs,row)->new Metrics(rs.getLong("queued"),rs.getLong("processing"),rs.getLong("retrying"),rs.getLong("completed"),
                rs.getLong("failed"),rs.getLong("cancelled"),rs.getLong("retries"),rs.getLong("poison"),rs.getDouble("oldest"),rs.getBoolean("active")));
    }
    public Map<String,OperationMetrics> operationMetrics() {
        var result=new HashMap<String,OperationMetrics>();
        db.query("""
            SELECT operation,count(*) submitted,count(*) FILTER(WHERE status='COMPLETED') completed,
              count(*) FILTER(WHERE status='FAILED') failed,coalesce(sum(greatest(attempts-1,0)),0) retries
            FROM merge_jobs GROUP BY operation
            """,rs->{result.put(rs.getString("operation"),new OperationMetrics(rs.getLong("submitted"),rs.getLong("completed"),rs.getLong("failed"),rs.getLong("retries")));});
        return result;
    }
    public Optional<Job> find(UUID id, String owner) {
        return db.query("SELECT * FROM merge_jobs WHERE id=? AND owner_key=?", this::map, id, owner).stream().findFirst();
    }
    public List<Job> history(String owner) {
        return db.query("SELECT * FROM merge_jobs WHERE owner_key=? ORDER BY created_at DESC LIMIT 30", this::map, owner);
    }
    public List<Job> history(String owner,int limit,Instant before) {
        if(limit<1||limit>100)throw new IllegalArgumentException("History limit must be between 1 and 100");
        return before==null
            ? db.query("SELECT * FROM merge_jobs WHERE owner_key=? ORDER BY created_at DESC,id DESC LIMIT ?",this::map,owner,limit)
            : db.query("SELECT * FROM merge_jobs WHERE owner_key=? AND created_at<? ORDER BY created_at DESC,id DESC LIMIT ?",this::map,owner,Timestamp.from(before),limit);
    }
    public int activeCount(String owner) {
        Long count=db.queryForObject("SELECT count(*) FROM merge_jobs WHERE owner_key=? AND status IN ('CREATED','QUEUED','PROCESSING','RETRYING')",Long.class,owner);
        return count==null?0:Math.toIntExact(count);
    }
    public List<Job> pending() {
        db.update("UPDATE merge_jobs SET status='QUEUED' WHERE status='RETRYING' AND pending AND expires_at>now() AND retry_at<=now()");
        return db.query("SELECT * FROM merge_jobs WHERE pending AND status='QUEUED' AND expires_at>now() ORDER BY created_at LIMIT 50", this::map);
    }
    public void sent(Job job) {
        db.update("UPDATE merge_jobs SET pending=false WHERE id=? AND dispatch_id=?", job.id(), job.dispatch());
    }
    public Optional<Job> claim(UUID id, String owner, UUID dispatch) {
        return db.query("""
            UPDATE merge_jobs SET status='PROCESSING', attempts=attempts+1, lease_token=?,
                lease_until=now()+interval '120 seconds'
            WHERE id=? AND owner_key=? AND dispatch_id=? AND status='QUEUED'
                AND attempts<3 AND expires_at>now() RETURNING *
            """, this::map, UUID.randomUUID(), id, owner, dispatch).stream().findFirst();
    }
    public boolean renew(Job job) {
        return db.update("""
            UPDATE merge_jobs SET lease_until=now()+interval '120 seconds'
            WHERE id=? AND lease_token=? AND status='PROCESSING' AND lease_until>now()
            """, job.id(), job.lease()) == 1;
    }
    public boolean complete(Job job, String key,long size,String checksum) {
        if(size<0||checksum==null||!checksum.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid result metadata");
        return db.update("""
            UPDATE merge_jobs SET status='COMPLETED',output_key=?,output_size=?,output_checksum=?,lease_token=null,lease_until=null,error_code=null
            WHERE id=? AND lease_token=? AND status='PROCESSING' AND lease_until>now()
            """, key,size,checksum,job.id(),job.lease()) == 1;
    }
    public void fail(Job job, boolean retryable, String code) {
        db.update("""
            UPDATE merge_jobs SET status=CASE WHEN ? AND attempts<3 THEN 'RETRYING' ELSE 'FAILED' END,
                pending=(? AND attempts<3),dispatch_id=?,lease_token=null,lease_until=null,error_code=?,
                retry_at=now()+make_interval(secs => 5*attempts)
            WHERE id=? AND lease_token=? AND status='PROCESSING' AND lease_until>now()
            """, retryable, retryable, UUID.randomUUID(), code, job.id(), job.lease());
    }
    public void recover() {
        db.update("""
            UPDATE merge_jobs SET status=CASE WHEN attempts<3 THEN 'RETRYING' ELSE 'FAILED' END,
                pending=(attempts<3),dispatch_id=gen_random_uuid(),lease_token=null,lease_until=null,
                error_code='WORKER_INTERRUPTED',retry_at=now()+interval '5 seconds'
            WHERE status='PROCESSING' AND lease_until<=now()
            """);
    }
    public List<Job> expired() {
        return db.query("SELECT * FROM merge_jobs WHERE expires_at<=now() AND status<>'PROCESSING' AND status<>'EXPIRED' LIMIT 50", this::map);
    }
    public void expire(Job job) {
        db.update("UPDATE merge_jobs SET status='EXPIRED',pending=false,output_key=null,output_size=null,output_checksum=null WHERE id=? AND status<>'PROCESSING' AND expires_at<=now()", job.id());
    }
}
