package nsu.momongo12.logic;

import lombok.extern.slf4j.Slf4j;
import nsu.momongo12.config.Config;
import nsu.momongo12.config.Socks5Constants;
import nsu.momongo12.model.ChannelPair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;

/**
 * @author momongo12
 * @version 1.0
 */
@Slf4j
public class Socks5Service implements AutoCloseable {

    private final Selector selector;

    public Socks5Service(Selector selector) {
        this.selector = selector;
    }

    public void establishConnection(SocketChannel clientChannel, SelectionKey clientKey) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Config.getBufferSize());
        int bytesRead = clientChannel.read(buffer);
        buffer.flip();

        if (bytesRead < 4) {
            sendErrorResponse(clientChannel, Socks5Constants.RESPONSE_GENERAL_FAILURE);
            return;
        }

        if (buffer.get() != Socks5Constants.SOCKS_VERSION) {
            sendErrorResponse(clientChannel, Socks5Constants.RESPONSE_GENERAL_FAILURE);
            return;
        }

        byte command = buffer.get();
        if (command != Socks5Constants.COMMAND_CONNECT) {
            sendErrorResponse(clientChannel, Socks5Constants.RESPONSE_COMMAND_NOT_SUPPORTED);
            return;
        }

        buffer.get(); // Пропускаем зарезервированный байт

        byte addressType = buffer.get();
        String destinationAddress;
        if (addressType == Socks5Constants.ADDRESS_TYPE_IPV4) {
            byte[] addressBytes = new byte[Socks5Constants.IPV4_ADDRESS_LENGTH];
            buffer.get(addressBytes);
            destinationAddress = String.format("%d.%d.%d.%d",
                    addressBytes[0] & 0xFF, addressBytes[1] & 0xFF,
                    addressBytes[2] & 0xFF, addressBytes[3] & 0xFF);
        } else if (addressType == Socks5Constants.ADDRESS_TYPE_DOMAIN) {
            int domainLength = buffer.get();
            byte[] domainBytes = new byte[domainLength];
            buffer.get(domainBytes);
            destinationAddress = new String(domainBytes);
        } else {
            sendErrorResponse(clientChannel, Socks5Constants.RESPONSE_ADDRESS_TYPE_NOT_SUPPORTED);
            return;
        }

        int destinationPort = buffer.getShort() & 0xFFFF;
        log.debug("Connecting to {}:{}", destinationAddress, destinationPort);

        try {
            SocketChannel serverChannel = SocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));

            ChannelPair channelPair = new ChannelPair(clientChannel, serverChannel);
            clientKey.attach(channelPair);
            serverChannel.register(selector, SelectionKey.OP_CONNECT, channelPair);

            sendSuccessResponse(clientChannel);
        } catch (UnresolvedAddressException e) {
            log.warn("Failed to resolve address: " + destinationAddress);
            sendErrorResponse(clientChannel, Socks5Constants.RESPONSE_HOST_UNREACHABLE);
        }
    }

    public void handleAuthentication(SocketChannel clientChannel, SelectionKey key) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        int bytesRead = clientChannel.read(buffer);
        if (bytesRead < 2) {
            return;
        }
        buffer.flip();

        byte version = buffer.get();
        if (version != Socks5Constants.SOCKS_VERSION) {
            sendErrorResponse(clientChannel, Socks5Constants.RESPONSE_GENERAL_FAILURE);
            return;
        }

        byte numMethods = buffer.get();
        ByteBuffer methodsBuffer = ByteBuffer.allocate(numMethods);
        bytesRead = clientChannel.read(methodsBuffer);
        if (bytesRead < numMethods) {
            return;
        }
        methodsBuffer.flip();

        boolean noAuthMethodSupported = false;
        for (int i = 0; i < numMethods; i++) {
            if (methodsBuffer.get() == Socks5Constants.AUTH_METHOD_NO_AUTH) {
                noAuthMethodSupported = true;
                break;
            }
        }

        ByteBuffer response = ByteBuffer.allocate(2);
        response.put(Socks5Constants.SOCKS_VERSION);
        if (noAuthMethodSupported) {
            response.put(Socks5Constants.AUTH_METHOD_NO_AUTH);
        } else {
            response.put(Socks5Constants.AUTH_METHOD_NO_ACCEPTABLE_METHODS);
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
        ByteBuffer buffer = ByteBuffer.allocate(Socks5Constants.RESPONSE_HEADER_SIZE);
        buffer.put(Socks5Constants.SOCKS_VERSION); // SOCKS5 версия
        buffer.put(errorCode);    // Код ошибки
        buffer.put((byte) 0x00);  // Зарезервировано
        buffer.put(Socks5Constants.ADDRESS_TYPE_IPV4);  // Тип адреса (IPv4)
        buffer.putInt(0);         // Адрес (0.0.0.0, не имеет значения)
        buffer.putShort((short) 0); // Порт (0, не имеет значения)
        buffer.flip();
        clientChannel.write(buffer);
        clientChannel.close();
    }

    private void sendSuccessResponse(SocketChannel clientChannel) throws IOException {
        log.debug("Send success response");
        ByteBuffer buffer = ByteBuffer.allocate(Socks5Constants.RESPONSE_HEADER_SIZE);
        buffer.put(Socks5Constants.SOCKS_VERSION); // SOCKS5 версия
        buffer.put(Socks5Constants.RESPONSE_SUCCESS); // Успешное подключение
        buffer.put((byte) 0x00); // Зарезервировано
        buffer.put(Socks5Constants.ADDRESS_TYPE_IPV4); // Тип адреса (IPv4)
        buffer.putInt(0);        // Адрес (0.0.0.0, не имеет значения)
        buffer.putShort((short) 0); // Порт (0, не имеет значения)
        buffer.flip();
        clientChannel.write(buffer);
    }

    @Override
    public void close() throws Exception {
        // ignore
    }
}
