package nsu.momongo12.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author momongo12
 * @version 1.0
 */
@Data
@Slf4j
public class ChannelPair {

    private SocketChannel clientChannel;
    private SocketChannel serverChannel;

    private Queue<ByteBuffer> clientToServerQueue = new ArrayDeque<>();
    private Queue<ByteBuffer> serverToClientQueue = new ArrayDeque<>();

    public ChannelPair(SocketChannel clientChannel, SocketChannel serverChannel) {
        this.clientChannel = clientChannel;
        this.serverChannel = serverChannel;
    }

    public void close() throws IOException {
        try {
            if (clientChannel != null && clientChannel.isOpen()) {
                clientChannel.close();
            }
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
        } catch (IOException e) {
            log.warn("Error while closing channels: " + e.getMessage());
        }
    }
}
