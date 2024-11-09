package nsu.momongo12;

import lombok.extern.slf4j.Slf4j;
import nsu.momongo12.config.Config;
import nsu.momongo12.model.ChannelPair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Queue;

/**
 * @author momongo12
 * @version 1.0
 */
@Slf4j
public class Socks5Server {

    private final Selector selector;

    public Socks5Server() throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(Config.getServerPort()));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        log.info("SOCKS5 server started and listen port: {}", Config.getServerPort());
    }

    public void start() throws IOException {
        while (true) {
            selector.select();

            for (SelectionKey key: selector.selectedKeys()) {
                if (key.isValid()) {
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    } else if (key.isConnectable()) {
                        handleConnect(key);
                    }
                }
            }
            selector.selectedKeys().clear();
        }
    }

    private void handleAccept(SelectionKey selectionKey) throws IOException {
        SocketChannel client = ((ServerSocketChannel) selectionKey.channel()).accept();
        if (client == null) {
            return;
        }
        client.configureBlocking(false);
        client.register(selectionKey.selector(), SelectionKey.OP_READ);
        log.debug("Client connected: {}", getClientAddress(client));
    }

    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel serverChannel = (SocketChannel) key.channel();

        if (serverChannel.finishConnect()) {
            key.interestOps(SelectionKey.OP_READ);
            log.debug("Tunnel to {} active", getClientAddress(serverChannel));
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel sourceChannel = (SocketChannel) key.channel();
        ChannelPair channelPair = (ChannelPair) key.attachment();

        Queue<ByteBuffer> writeQueue;
        SocketChannel targetChannel;

        if (channelPair == null) {
            handleAuthentication(sourceChannel, key);
            return;
        } else if (channelPair.getServerChannel() == null) {
            establishConnection(sourceChannel, key);
            return;
        }

        if (sourceChannel == channelPair.getClientChannel()) {
            writeQueue = channelPair.getClientToServerQueue();
            targetChannel = channelPair.getServerChannel();
        } else {
            writeQueue = channelPair.getServerToClientQueue();
            targetChannel = channelPair.getClientChannel();
        }

        ByteBuffer readBuffer = ByteBuffer.allocate(Config.getBufferSize());
        int bytesRead = sourceChannel.read(readBuffer);
        if (bytesRead == -1) {
            channelPair.close();
            return;
        }

        readBuffer.flip();
        writeQueue.add(readBuffer);
        targetChannel.keyFor(selector).interestOps(SelectionKey.OP_WRITE);
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel targetChannel = (SocketChannel) key.channel();
        ChannelPair channelPair = (ChannelPair) key.attachment();

        Queue<ByteBuffer> writeQueue;
        if (targetChannel == channelPair.getServerChannel()) {
            writeQueue = channelPair.getClientToServerQueue();
        } else {
            writeQueue = channelPair.getServerToClientQueue();
        }

        while (!writeQueue.isEmpty()) {
            ByteBuffer buffer = writeQueue.peek();
            targetChannel.write(buffer);
            if (buffer.hasRemaining()) {
                return;
            }
            writeQueue.poll();
        }

        key.interestOps(SelectionKey.OP_READ);
    }

    private void establishConnection(SocketChannel clientChannel, SelectionKey clientKey) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Config.getBufferSize());
        int bytesRead = clientChannel.read(buffer);
        buffer.flip();

        if (bytesRead < 4) {
            sendErrorResponse(clientChannel, (byte) 0x01);
            return;
        }

        if (buffer.get() != 0x05) {
            sendErrorResponse(clientChannel, (byte) 0x01);
            return;
        }

        byte command = buffer.get();
        if (command != 0x01) {
            sendErrorResponse(clientChannel, (byte) 0x07);
            return;
        }

        buffer.get();

        byte addressType = buffer.get();
        String destinationAddress;
        if (addressType == 0x01) {
            byte[] addressBytes = new byte[4];
            buffer.get(addressBytes);
            destinationAddress = String.format("%d.%d.%d.%d",
                    addressBytes[0] & 0xFF, addressBytes[1] & 0xFF,
                    addressBytes[2] & 0xFF, addressBytes[3] & 0xFF);
        } else if (addressType == 0x03) {
            int domainLength = buffer.get();
            byte[] domainBytes = new byte[domainLength];
            buffer.get(domainBytes);
            destinationAddress = new String(domainBytes);
        } else {
            sendErrorResponse(clientChannel, (byte) 0x08);
            return;
        }

        int destinationPort = buffer.getShort() & 0xFFFF;
        log.info("Connecting to {}:{}", destinationAddress, destinationPort);

        try {
            SocketChannel serverChannel = SocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));

            ChannelPair channelPair = new ChannelPair(clientChannel, serverChannel);
            clientKey.attach(channelPair);
            serverChannel.register(selector, SelectionKey.OP_CONNECT, channelPair);

            sendSuccessResponse(clientChannel);
        } catch (UnresolvedAddressException e) {
            System.err.println("Failed to resolve address: " + destinationAddress);
            sendErrorResponse(clientChannel, (byte) 0x04);
        }
    }

    private void handleAuthentication(SocketChannel clientChannel, SelectionKey key) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        int bytesRead = clientChannel.read(buffer);
        if (bytesRead < 2) {
            return;
        }
        buffer.flip();

        byte version = buffer.get();
        if (version != 0x05) {
            sendErrorResponse(clientChannel, (byte) 0x01); // Неподдерживаемая версия
            return;
        }

        byte numMethods = buffer.get();
        ByteBuffer methodsBuffer = ByteBuffer.allocate(numMethods);
        bytesRead = clientChannel.read(methodsBuffer);
        if (bytesRead < numMethods) {
            return; // Ждем, пока все методы будут переданы
        }
        methodsBuffer.flip();

        boolean noAuthMethodSupported = false;
        for (int i = 0; i < numMethods; i++) {
            if (methodsBuffer.get() == 0x00) {
                noAuthMethodSupported = true;
                break;
            }
        }

        ByteBuffer response = ByteBuffer.allocate(2);
        response.put((byte) 0x05);
        if (noAuthMethodSupported) {
            response.put((byte) 0x00);
        } else {
            response.put((byte) 0xFF);
        }
        response.flip();
        clientChannel.write(response);

        if (!noAuthMethodSupported) {
            clientChannel.close();
        } else {
            key.attach(new ChannelPair(clientChannel, null));
            key.interestOps(SelectionKey.OP_READ);
        }
    }


    private void sendErrorResponse(SocketChannel clientChannel, byte errorCode) throws IOException {
        log.debug("Send error response: {}", errorCode);
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put((byte) 0x05); // SOCKS5 версия
        buffer.put(errorCode);    // Код ошибки
        buffer.put((byte) 0x00);  // Зарезервировано
        buffer.put((byte) 0x01);  // Тип адреса (IPv4)
        buffer.putInt(0);         // Адрес (0.0.0.0, не имеет значения)
        buffer.putShort((short) 0); // Порт (0, не имеет значения)
        buffer.flip();
        clientChannel.write(buffer);
        clientChannel.close();
    }

    private void sendSuccessResponse(SocketChannel clientChannel) throws IOException {
        log.debug("Send success response");
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put((byte) 0x05); // SOCKS5 версия
        buffer.put((byte) 0x00); // Успешное подключение
        buffer.put((byte) 0x00); // Зарезервировано
        buffer.put((byte) 0x01); // Тип адреса (IPv4)
        buffer.putInt(0);        // Адрес (0.0.0.0, не имеет значения)
        buffer.putShort((short) 0); // Порт (0, не имеет значения)
        buffer.flip();
        clientChannel.write(buffer);
    }

    private String getClientAddress(SocketChannel client) {
        try {
            return client.getRemoteAddress().toString();
        } catch (IOException e) {
            return "Unknown";
        }
    }
}
