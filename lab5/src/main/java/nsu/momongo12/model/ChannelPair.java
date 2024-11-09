package nsu.momongo12.model;

import lombok.Data;

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
public class ChannelPair {

    private SocketChannel clientChannel;
    private SocketChannel serverChannel;

    private Queue<ByteBuffer> clientToServerQueue = new ArrayDeque<>();
    private Queue<ByteBuffer> serverToClientQueue = new ArrayDeque<>();

    public ChannelPair(SocketChannel clientChannel, SocketChannel serverChannel) {
        this.clientChannel = clientChannel;
        this.serverChannel = serverChannel;
    }

    public SocketChannel getActiveChannel(SocketChannel socketChannel) {
        return socketChannel == clientChannel ? clientChannel : serverChannel;
    }

    public void close() throws IOException {
        clientChannel.close();
        serverChannel.close();
    }
}
