package nsu.momongo12;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryService.class);

    private final InetAddress groupAddress;
    private final MulticastSocket socket;
    private final int port;

    private final Set<InetAddress> liveInstances = ConcurrentHashMap.newKeySet();
    private final Map<InetAddress, Long> instanceTimestamps = new ConcurrentHashMap<>();

    private final Set<InetAddress> localAddresses;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    private final int heartbeatInterval;
    private final int cleanupInterval;
    private final int instanceTimeout;
    private final String heartbeatMessage;
    private final int bufferSize;

    public DiscoveryService(InetAddress groupAddress, int port, Properties properties) throws IOException {
        this.groupAddress = groupAddress;
        this.port = port;

        heartbeatInterval = Integer.parseInt(properties.getProperty("heartbeat.interval", "1000"));
        cleanupInterval = Integer.parseInt(properties.getProperty("cleanup.interval", "1000"));
        instanceTimeout = Integer.parseInt(properties.getProperty("instance.timeout", "3000"));
        heartbeatMessage = properties.getProperty("heartbeat.message", "HELLO");
        bufferSize = Integer.parseInt(properties.getProperty("buffer.size", "256"));

        int multicastTTL = Integer.parseInt(properties.getProperty("multicast.ttl", "1"));

        socket = new MulticastSocket(port);
        socket.setReuseAddress(true);
        socket.setTimeToLive(multicastTTL);

        NetworkInterface networkInterface = getNetworkInterface();

        SocketAddress group = new InetSocketAddress(groupAddress, port);
        socket.joinGroup(group, networkInterface);

        localAddresses = getLocalAddresses();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, heartbeatInterval, TimeUnit.MILLISECONDS);
        scheduler.execute(this::receiveMessages);
        scheduler.scheduleAtFixedRate(this::cleanupInstances, 0, cleanupInterval, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        try {
            byte[] message = heartbeatMessage.getBytes();
            DatagramPacket packet = new DatagramPacket(
                message,
                message.length,
                groupAddress,
                port
            );
            socket.send(packet);
        } catch (IOException e) {
            logger.error("Error sending heartbeat: {}", e.getMessage(), e);
        }
    }

    private void receiveMessages() {
        byte[] buf = new byte[bufferSize];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                InetAddress senderAddress = packet.getAddress();

                if (localAddresses.contains(senderAddress)) {
                    continue;
                }

                instanceTimestamps.put(senderAddress, System.currentTimeMillis());

                if (liveInstances.add(senderAddress)) {
                    printLiveInstances();
                }
            } catch (IOException e) {
                logger.error("Error receiving message: {}", e.getMessage(), e);
            }
        }
    }

    private void cleanupInstances() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Map.Entry<InetAddress, Long>> iterator = instanceTimestamps.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<InetAddress, Long> entry = iterator.next();
            if (now - entry.getValue() > instanceTimeout) {
                InetAddress address = entry.getKey();
                iterator.remove();
                liveInstances.remove(address);
                changed = true;
            }
        }
        if (changed) {
            printLiveInstances();
        }
    }

    private synchronized void printLiveInstances() {
        logger.info("Live instances:");
        for (InetAddress address : liveInstances) {
            logger.info(address.getHostAddress());
        }
        logger.info("-----");
    }

    private Set<InetAddress> getLocalAddresses() throws SocketException {
        Set<InetAddress> addresses = new HashSet<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (!ni.isUp() || ni.isLoopback()) {
                continue;
            }
            Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress addr = inetAddresses.nextElement();
                addresses.add(addr);
            }
        }
        return addresses;
    }

    private NetworkInterface getNetworkInterface() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (ni.isUp() && ni.supportsMulticast()) {
                return ni;
            }
        }
        throw new SocketException("No suitable network interface found for multicast");
    }
}
