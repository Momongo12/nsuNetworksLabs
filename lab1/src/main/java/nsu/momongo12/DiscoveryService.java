package nsu.momongo12;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryService.class);
    private static final Gson gson = new Gson();

    private final InetAddress groupAddress;
    private final MulticastSocket socket;
    private final int port;
    private final Set<InstanceInfo> liveInstances = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> instanceTimestamps = new ConcurrentHashMap<>();
    private final Set<InetAddress> localAddresses;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final int heartbeatInterval;
    private final int cleanupInterval;
    private final int instanceTimeout;
    private final int bufferSize;
    private final int multicastTTL;

    private volatile boolean readiness = true;

    public final static String INSTANCE_ID = UUID.randomUUID().toString();

    public DiscoveryService(
        InetAddress groupAddress, int port, Properties properties, String interfaceName
    ) throws IOException {
        this.groupAddress = groupAddress;
        this.port = port;

        heartbeatInterval = Integer.parseInt(properties.getProperty("heartbeat.interval", "1000"));
        cleanupInterval = Integer.parseInt(properties.getProperty("cleanup.interval", "1000"));
        instanceTimeout = Integer.parseInt(properties.getProperty("instance.timeout", "3000"));
        bufferSize = Integer.parseInt(properties.getProperty("buffer.size", "256"));
        multicastTTL = Integer.parseInt(properties.getProperty("multicast.ttl", "1"));

        socket = new MulticastSocket(port);
        socket.setReuseAddress(true);
        socket.setTimeToLive(multicastTTL);

        boolean isIPv6 = groupAddress instanceof Inet6Address;

        NetworkInterface networkInterface = getNetworkInterface(isIPv6, interfaceName);

        socket.setNetworkInterface(networkInterface);

        if (isIPv6) {
            SocketAddress group = new InetSocketAddress(groupAddress, port);
            socket.joinGroup(group, networkInterface);
            logger.info("Joined IPv6 multicast group {} on interface {}", groupAddress, networkInterface.getName());
        } else {
            socket.joinGroup(groupAddress);
            logger.info("Joined IPv4 multicast group {} on interface {}", groupAddress, networkInterface.getName());
        }

        localAddresses = getLocalAddresses(isIPv6, networkInterface);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, heartbeatInterval, TimeUnit.MILLISECONDS);
        scheduler.execute(this::receiveMessages);
        scheduler.scheduleAtFixedRate(this::cleanupInstances, 0, cleanupInterval, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        try {
            String message = String.format("id=%s;ready=%s", INSTANCE_ID, readiness);
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(
                messageBytes,
                messageBytes.length,
                groupAddress,
                port
            );
            socket.send(packet);
            logger.debug("Sent heartbeat: {}", message);
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

                String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                logger.debug("Received message: {}", received);

                InstanceInfo heartbeat;
                try {
                    heartbeat = gson.fromJson(received, InstanceInfo.class);
                } catch (JsonSyntaxException e) {
                    logger.warn("Received invalid heartbeat message: {}", received);
                    continue;
                }

                if (INSTANCE_ID.equals(heartbeat.id())) {
                    logger.debug("Ignored own heartbeat message.");
                    continue;
                }

                InstanceInfo instance = new InstanceInfo(heartbeat.id(), heartbeat.readiness(), senderAddress);

                instanceTimestamps.put(instance.id(), System.currentTimeMillis());

                if (liveInstances.add(instance)) {
                    logger.info("New instance detected: {}", instance);
                    printLiveInstances();
                } else {
                    liveInstances.remove(instance);
                    liveInstances.add(instance);
                    logger.debug("Updated instance: {}", instance);
                }

            } catch (IOException e) {
                logger.error("Error receiving message: {}", e.getMessage(), e);
            }
        }
    }

    private void cleanupInstances() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Map.Entry<String, Long>> iterator = instanceTimestamps.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > instanceTimeout) {
                String id = entry.getKey();
                iterator.remove();

                liveInstances.removeIf(instance -> instance.id().equals(id));
                logger.info("Instance expired: ID={}", id);
                changed = true;
            }
        }
        if (changed) {
            printLiveInstances();
        }
    }

    private synchronized void printLiveInstances() {
        logger.info("Live instances:");
        for (InstanceInfo instance : liveInstances) {
            logger.info("ID: {}, Ready: {}, IP: {}", instance.id(), instance.readiness(), instance.address().getHostAddress());
        }
        logger.info("-----");
    }

    private Set<InetAddress> getLocalAddresses(boolean isIPv6, NetworkInterface networkInterface) {
        Set<InetAddress> addresses = new HashSet<>();
        Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
        while (inetAddresses.hasMoreElements()) {
            InetAddress addr = inetAddresses.nextElement();
            if (isIPv6 && addr instanceof Inet6Address) {
                addresses.add(addr);
            } else if (!isIPv6 && addr instanceof Inet4Address) {
                addresses.add(addr);
            }
        }
        return addresses;
    }

    private NetworkInterface getNetworkInterface(boolean isIPv6, String interfaceName) throws SocketException {
        if (interfaceName != null && !interfaceName.isEmpty()) {
            NetworkInterface ni = NetworkInterface.getByName(interfaceName);
            if (ni != null && ni.isUp() && ni.supportsMulticast()) {
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (isIPv6 && addr instanceof Inet6Address) {
                        return ni;
                    } else if (!isIPv6 && addr instanceof Inet4Address) {
                        return ni;
                    }
                }
            }
            throw new SocketException("Specified network interface " + interfaceName + " is not suitable for multicast.");
        }

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (ni.isUp() && ni.supportsMulticast() && !ni.getName().startsWith("utun")) {
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (isIPv6 && addr instanceof Inet6Address) {
                        return ni;
                    } else if (!isIPv6 && addr instanceof Inet4Address) {
                        return ni;
                    }
                }
            }
        }
        throw new SocketException("No suitable network interface found for multicast.");
    }
}
