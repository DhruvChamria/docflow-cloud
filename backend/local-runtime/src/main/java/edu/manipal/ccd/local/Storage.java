package edu.manipal.ccd.local;

import com.azure.storage.blob.*;
import com.azure.storage.blob.models.*;
import com.azure.storage.queue.*;
import com.azure.storage.queue.models.*;
import com.azure.core.util.Context;
import java.nio.file.*;
import java.io.*;
import java.time.Duration;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class Storage {
    public record StoredObject(long size,String sha256) {}
    public final BlobContainerClient blobs;
    public final QueueClient queue;
    public final QueueClient officeQueue;
    public Storage(String connection) {
        blobs = new BlobServiceClientBuilder().connectionString(connection)
            .serviceVersion(BlobServiceVersion.V2023_11_03).buildClient().getBlobContainerClient("documents");
        blobs.createIfNotExists();
        queue = new QueueClientBuilder().connectionString(connection).queueName("pdf-jobs")
            .serviceVersion(QueueServiceVersion.V2023_11_03).buildClient();
        queue.createIfNotExists();
        officeQueue = new QueueClientBuilder().connectionString(connection).queueName("office-jobs")
            .serviceVersion(QueueServiceVersion.V2023_11_03).buildClient();
        officeQueue.createIfNotExists();
    }
    public void put(String key, InputStream data, long length) {
        blobs.getBlobClient(key).upload(data, length, false);
    }
    public StoredObject upload(String key, Path file) throws IOException {
        long size=Files.size(file);
        try (var stream=new DigestInputStream(Files.newInputStream(file),sha256())) {
            put(key, stream, size);
            return new StoredObject(size,HexFormat.of().formatHex(stream.getMessageDigest().digest()));
        }
    }
    public InputStream open(String key) { return blobs.getBlobClient(key).openInputStream(); }
    public void download(String key, Path file) { blobs.getBlobClient(key).downloadToFile(file.toString()); }
    public void delete(String key) { blobs.getBlobClient(key).deleteIfExists(); }
    public void deletePrefix(String prefix) {
        for (var blob : blobs.listBlobs(new ListBlobsOptions().setPrefix(prefix), Duration.ofSeconds(30)))
            delete(blob.getName());
    }
    public QueueMessageItem receive() {
        return receive(queue);
    }
    public QueueMessageItem receiveOffice() { return receive(officeQueue); }
    private QueueMessageItem receive(QueueClient target) {
        return target.receiveMessages(1, Duration.ofSeconds(120), Duration.ofSeconds(10), Context.NONE)
            .stream().findFirst().orElse(null);
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
