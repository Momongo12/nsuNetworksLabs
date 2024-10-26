package nsu.momongo12;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class Server {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private final Map<SocketChannel, ClientAttachment> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Config config;

    public Server(Config config) {
        this.config = config;
    }

    public void start() throws IOException {
        Path uploadPath = Path.of(config.getUploadDirectory());
        if (!Files.exists(uploadPath)) {
            Files.createDirectory(uploadPath);
            logger.info("Создана директория для загрузок: {}", config.getUploadDirectory());
        } else {
            logger.info("Директория для загрузок уже существует: {}", config.getUploadDirectory());
        }

        Selector selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(config.getServerPort()));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        logger.info("Сервер NIO запущен и слушает порт {}", config.getServerPort());

        scheduler.scheduleAtFixedRate(this::reportSpeeds, config.getReportIntervalSeconds(), config.getReportIntervalSeconds(), TimeUnit.SECONDS);

        try {
            while (true) {
                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    if (key.isAcceptable()) {
                        register(selector, serverChannel);
                    }

                    if (key.isReadable()) {
                        handleRead(key, uploadPath);
                    }
                }
            }
        } finally {
            shutdown(selector, serverChannel);
        }
    }

    private void register(Selector selector, ServerSocketChannel serverChannel) throws IOException {
        SocketChannel client = serverChannel.accept();
        if (client == null) {
            return;
        }
        client.configureBlocking(false);
        SelectionKey key = client.register(selector, SelectionKey.OP_READ);
        ClientAttachment attachment = new ClientAttachment(client, config);
        key.attach(attachment);
        clients.put(client, attachment);
        logger.info("Подключен клиент: {}", getClientAddress(client));
    }

    private void handleRead(SelectionKey key, Path uploadDir) {
        SocketChannel client = (SocketChannel) key.channel();
        ClientAttachment attachment = (ClientAttachment) key.attachment();

        ByteBuffer buffer = ByteBuffer.allocate(config.getBufferSize());
        int bytesRead;
        try {
            bytesRead = client.read(buffer);
            if (bytesRead == -1) {
                closeConnection(client);
                return;
            }
        } catch (IOException e) {
            logger.error("Ошибка чтения от клиента {}: {}", getClientAddress(client), e.getMessage(), e);
            closeConnection(client);
            return;
        }

        if (bytesRead > 0) {
            buffer.flip();
            try {
                attachment.processData(buffer, uploadDir);
                if (attachment.isFinished()) {
                    boolean success = attachment.verifyFileSize();
                    ByteBuffer confirmation = ByteBuffer.allocate(1);
                    confirmation.put(success ? (byte)1 : (byte)0);
                    confirmation.flip();
                    while (confirmation.hasRemaining()) {
                        client.write(confirmation);
                    }
                    logger.info("Файл {} {} получен от {}", attachment.getFileName(), success ? "успешно" : "не успешно", getClientAddress(client));
                    attachment.calculateAndReportSpeed(System.currentTimeMillis());
                    closeConnection(client);
                }
            } catch (IOException e) {
                logger.error("Ошибка обработки данных от клиента {}: {}", getClientAddress(client), e.getMessage(), e);
                closeConnection(client);
            }
        }
    }

    private void closeConnection(SocketChannel client) {
        try {
            logger.info("Закрытие соединения с клиентом: {}", getClientAddress(client));
            clients.remove(client);
            client.close();
        } catch (IOException e) {
            logger.error("Ошибка при закрытии соединения с клиентом {}: {}", getClientAddress(client), e.getMessage(), e);
        }
    }

    private void reportSpeeds() {
        long currentTime = System.currentTimeMillis();
        for (ClientAttachment attachment : clients.values()) {
            attachment.calculateAndReportSpeed(currentTime);
        }
    }

    private String getClientAddress(SocketChannel client) {
        try {
            return client.getRemoteAddress().toString();
        } catch (IOException e) {
            return "Unknown";
        }
    }

    private void shutdown(Selector selector, ServerSocketChannel serverChannel) {
        try {
            scheduler.shutdown();
            selector.close();
            serverChannel.close();
            logger.info("Сервер остановлен.");
        } catch (IOException e) {
            logger.error("Ошибка при остановке сервера: {}", e.getMessage(), e);
        }
    }
}