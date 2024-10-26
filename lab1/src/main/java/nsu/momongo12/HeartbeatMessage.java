package nsu.momongo12;

import java.net.InetAddress;

/**
 * @author momongo12
 * @version 1.0
 */
public class HeartbeatMessage {
    private String id;
    private boolean readiness;
    private String address;

    public HeartbeatMessage(String id, boolean readiness, InetAddress address) {
        this.id = id;
        this.readiness = readiness;
        this.address = address.getHostAddress();
    }

    public String getId() {
        return id;
    }

    public boolean isReadiness() {
        return readiness;
    }
}