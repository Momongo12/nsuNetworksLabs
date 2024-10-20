package nsu.momongo12;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;

public class ClientAttachment {
    private static final Logger logger = LoggerFactory.getLogger(ClientAttachment.class);

    private enum State { READ_NAME_LENGTH, READ_NAME, READ_SIZE, READ_CONTENT, SEND_CONFIRMATION }

    private State state = State.READ_NAME_LENGTH;
    private ByteBuffer intBuffer = ByteBuffer.allocate(4);
    private ByteBuffer longBuffer = ByteBuffer.allocate(8);
    private ByteBuffer nameBuffer;
    private String fileName;
    private long fileSize;
    private long bytesReceived = 0;
    private FileChannel fileChannel;
    private Path filePath;

    private final AtomicLong totalBytes = new AtomicLong(0);
    private final AtomicLong bytesSinceLastReport = new AtomicLong(0);
    private final long startTime;
    private long lastReportTime;

    private final SocketAddress clientAddress;

    private final int maxFileNameLength;
    private final long maxFileSize;
    private final int reportIntervalSeconds;
    private boolean fileSizeVerified;

    public ClientAttachment(SocketChannel client, Config config) throws IOException {
        this.startTime = System.currentTimeMillis();
        this.lastReportTime = this.startTime;
        this.clientAddress = client.getRemoteAddress();
        this.maxFileNameLength = config.getMaxFileNameLength();
        this.maxFileSize = config.getMaxFileSize();
        this.reportIntervalSeconds = config.getReportIntervalSeconds();
        this.fileSizeVerified = false;
    }

    public void processData(ByteBuffer buffer, Path uploadDir) throws IOException {
        while (buffer.hasRemaining()) {
            switch (state) {
                case READ_NAME_LENGTH:
                    readToBuffer(buffer, intBuffer);
                    if (!intBuffer.hasRemaining()) {
                        intBuffer.flip();
                        int nameLength = intBuffer.getInt();
                        intBuffer.clear();
                        if (nameLength <= 0 || nameLength > maxFileNameLength) {
                            throw new IOException("Invalid file name length: " + nameLength);
                        }
                        nameBuffer = ByteBuffer.allocate(nameLength);
                        state = State.READ_NAME;
                    }
                    break;
                case READ_NAME:
                    readToBuffer(buffer, nameBuffer);
                    if (!nameBuffer.hasRemaining()) {
                        nameBuffer.flip();
                        byte[] nameBytes = new byte[nameBuffer.remaining()];
                        nameBuffer.get(nameBytes);
                        fileName = new String(nameBytes, "UTF-8");
                        Path tempPath = uploadDir.resolve(fileName).normalize();
                        if (!tempPath.startsWith(uploadDir)) {
                            throw new IOException("Attempt to write outside uploads directory");
                        }
                        filePath = tempPath;
                        state = State.READ_SIZE;
                    }
                    break;
                case READ_SIZE:
                    readToBuffer(buffer, longBuffer);
                    if (!longBuffer.hasRemaining()) {
                        longBuffer.flip();
                        fileSize = longBuffer.getLong();
                        longBuffer.clear();
                        if (fileSize < 0 || fileSize > maxFileSize) {
                            throw new IOException("Invalid file size: " + fileSize);
                        }
                        fileChannel = FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                        state = State.READ_CONTENT;
                    }
                    break;
                case READ_CONTENT:
                    long bytesToRead = fileSize - bytesReceived;
                    int bytesAvailable = buffer.remaining();
                    int bytesThisRound = (int) Math.min(bytesAvailable, bytesToRead);
                    ByteBuffer slice = buffer.slice();
                    slice.limit(bytesThisRound);
                    fileChannel.write(slice);
                    buffer.position(buffer.position() + bytesThisRound);
                    bytesReceived += bytesThisRound;
                    totalBytes.addAndGet(bytesThisRound);
                    bytesSinceLastReport.addAndGet(bytesThisRound);
                    if (bytesReceived == fileSize) {
                        fileSizeVerified = true;
                        fileChannel.close();
                        state = State.SEND_CONFIRMATION;
                    }
                    break;
                case SEND_CONFIRMATION:
                    break;
            }
        }
    }

    private void readToBuffer(ByteBuffer source, ByteBuffer target) {
        int remaining = target.remaining();
        int toRead = Math.min(source.remaining(), remaining);
        for (int i = 0; i < toRead; i++) {
            target.put(source.get());
        }
    }

    public boolean isFinished() {
        return state == State.SEND_CONFIRMATION;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean verifyFileSize() throws IOException {
        return fileSizeVerified;
    }

    public void calculateAndReportSpeed(long currentTime) {
        long elapsedSinceLastReport = (currentTime - lastReportTime) / 1000;
        boolean shouldReport = elapsedSinceLastReport >= reportIntervalSeconds || isFinished();

        if (shouldReport) {
            long instantBytes = bytesSinceLastReport.getAndSet(0);
            double instantSpeed = reportIntervalSeconds > 0 ? instantBytes / (double) reportIntervalSeconds : instantBytes;

            long totalElapsed = (currentTime - startTime) / 1000;
            double averageSpeed = totalElapsed > 0 ? totalBytes.get() / (double) totalElapsed : 0;

            String message = String.format("Клиент %s - Скорость за последние %d сек: %.2f байт/сек, Средняя скорость: %.2f байт/сек",
                clientAddress.toString(),
                reportIntervalSeconds,
                instantSpeed,
                averageSpeed);

            logger.info(message);

            lastReportTime = currentTime;
        }
    }
}