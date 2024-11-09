package nsu.momongo12.logic;

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
public class Socks5Server implements AutoCloseable {

    private final Socks5Service socks5Service;
    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;

    public Socks5Server() throws IOException {
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();

        serverSocketChannel.bind(new InetSocketAddress(Config.getServerPort()));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        socks5Service = new Socks5Service(selector);
        log.info("SOCKS5 server started and listen port: {}", Config.getServerPort());
    }

    public void start() throws IOException {
        while (true) {
            selector.select();

            for (SelectionKey key: selector.selectedKeys()) {
                try {
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
                } catch (IOException e) {
                    log.warn("IOException in selector loop: " + e.getMessage());
                    key.cancel();

                    Channel channel = key.channel();
                    if (channel != null && channel.isOpen()) {
                        try {
                            channel.close();
                        } catch (IOException ex) {
                            log.error("Failed to close channel: " + ex.getMessage());
                        }
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
            socks5Service.handleAuthentication(sourceChannel, key);
            return;
        } else if (channelPair.getServerChannel() == null) {
            socks5Service.establishConnection(sourceChannel, key);
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

    private String getClientAddress(SocketChannel client) {
        try {
            return client.getRemoteAddress().toString();
        } catch (IOException e) {
            return "Unknown";
        }
    }

    @Override
    public void close() throws Exception {
        selector.close();
        serverSocketChannel.close();
        socks5Service.close();

        log.info("Socks5 server shutdown");
    }
}
