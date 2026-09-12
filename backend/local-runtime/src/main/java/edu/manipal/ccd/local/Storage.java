package edu.manipal.ccd.local;

import com.azure.storage.blob.*;
import com.azure.storage.blob.models.*;
import com.azure.storage.queue.*;
import com.azure.storage.queue.models.*;
import com.azure.core.util.Context;
import java.nio.file.*;
import java.io.*;
import java.time.Duration;

public class Storage {
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
    public void upload(String key, Path file) throws IOException {
        try (var stream=Files.newInputStream(file)) { put(key, stream, Files.size(file)); }
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
}
