package nsu.momongo12;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

public class Server {
    private static final int BUFFER_SIZE = 8192;
    private static final String UPLOAD_DIR = "uploads";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Использование: java NioFileServer <порт>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectory(uploadPath);
        }

        Selector selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new java.net.InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Сервер NIO запущен и слушает порт " + port);

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
    }

    private static void register(Selector selector, ServerSocketChannel serverChannel) throws IOException {
        SocketChannel client = serverChannel.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, new ClientAttachment());
        System.out.println("Подключен клиент: " + client.getRemoteAddress());
    }

    private static void handleRead(SelectionKey key, Path uploadDir) {
        SocketChannel client = (SocketChannel) key.channel();
        ClientAttachment attachment = (ClientAttachment) key.attachment();

        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        int bytesRead;
        try {
            bytesRead = client.read(buffer);
        } catch (IOException e) {
            System.err.println("Ошибка чтения от клиента: " + e.getMessage());
            closeChannel(key);
            return;
        }

        if (bytesRead == -1) {
            closeChannel(key);
            return;
        }

        buffer.flip();
        try {
            while (buffer.hasRemaining()) {
                attachment.processData(buffer, uploadDir, client);
            }
        } catch (IOException e) {
            System.err.println("Ошибка обработки данных: " + e.getMessage());
            closeChannel(key);
        }
    }

    private static void closeChannel(SelectionKey key) {
        try {
            SocketChannel client = (SocketChannel) key.channel();
            System.out.println("Закрытие соединения с клиентом: " + client.getRemoteAddress());
            client.close();
        } catch (IOException e) {
            // Игнорируем
        }
        key.cancel();
    }
}

class ClientAttachment {
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
    private ByteBuffer confirmationBuffer = ByteBuffer.allocate(1);

    public void processData(ByteBuffer buffer, Path uploadDir, SocketChannel client) throws IOException {
        while (buffer.hasRemaining()) {
            switch (state) {
                case READ_NAME_LENGTH:
                    if (readBytes(buffer, intBuffer)) {
                        intBuffer.flip();
                        int nameLength = intBuffer.getInt();
                        if (nameLength <= 0 || nameLength > 4096) {
                            throw new IOException("Неверная длина имени файла: " + nameLength);
                        }
                        nameBuffer = ByteBuffer.allocate(nameLength);
                        state = State.READ_NAME;
                    }
                    break;
                case READ_NAME:
                    if (readBytes(buffer, nameBuffer)) {
                        nameBuffer.flip();
                        byte[] nameBytes = new byte[nameBuffer.remaining()];
                        nameBuffer.get(nameBytes);
                        fileName = new String(nameBytes, "UTF-8");
                        // Безопасность пути
                        Path tempPath = uploadDir.resolve(fileName).normalize();
                        if (!tempPath.startsWith(uploadDir)) {
                            throw new IOException("Попытка записи за пределы директории uploads");
                        }
                        filePath = tempPath;
                        fileSize = 0;
                        state = State.READ_SIZE;
                    }
                    break;
                case READ_SIZE:
                    if (readBytes(buffer, longBuffer)) {
                        longBuffer.flip();
                        fileSize = longBuffer.getLong();
                        if (fileSize < 0 || fileSize > (1L << 40)) { // 1 ТБ
                            throw new IOException("Неверный размер файла: " + fileSize);
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

                    if (bytesReceived == fileSize) {
                        fileChannel.close();
                        // Проверка размера файла
                        long actualSize = Files.size(filePath);
                        byte status = (actualSize == fileSize) ? (byte)1 : (byte)0;
                        confirmationBuffer.put(status);
                        confirmationBuffer.flip();
                        // Отправка подтверждения
                        client.write(confirmationBuffer);
                        state = State.SEND_CONFIRMATION;
                        confirmationBuffer.clear();
                        // Закрытие соединения после подтверждения
                        client.close();
                    }
                    break;
                case SEND_CONFIRMATION:
                    // Ничего не делаем, ожидание закрытия
                    break;
            }
        }
    }

    private boolean readBytes(ByteBuffer source, ByteBuffer target) {
        int remaining = target.remaining();
        int toRead = Math.min(source.remaining(), remaining);
        for (int i = 0; i < toRead; i++) {
            target.put(source.get());
        }
        return !target.hasRemaining();
    }
}
