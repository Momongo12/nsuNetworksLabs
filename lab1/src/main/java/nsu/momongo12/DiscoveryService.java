package nsu.momongo12;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class DiscoveryService {

    private final InetAddress groupAddress;
    private final MulticastSocket socket;
    private final int port;
    private final Set<InetAddress> liveInstances = ConcurrentHashMap.newKeySet();
    private final Map<InetAddress, Long> instanceTimestamps = new ConcurrentHashMap<>();

    private final Set<InetAddress> localAddresses;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public DiscoveryService(InetAddress groupAddress, int port) throws IOException {
        this.groupAddress = groupAddress;
        this.port = port;
        // Create a MulticastSocket bound to the appropriate port
        socket = new MulticastSocket(port);
        socket.setReuseAddress(true);
        socket.setTimeToLive(1); // stay within local network
        socket.joinGroup(groupAddress);

        localAddresses = getLocalAddresses();
    }

    public void start() {
        // Start the sender task
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, 1, TimeUnit.SECONDS);

        // Start the receiver task
        scheduler.execute(this::receiveMessages);

        // Start the cleanup task
        scheduler.scheduleAtFixedRate(this::cleanupInstances, 0, 1, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        try {
            byte[] message = "HELLO".getBytes();
            DatagramPacket packet = new DatagramPacket(
                message,
                message.length,
                groupAddress,
                port
            );
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void receiveMessages() {
        byte[] buf = new byte[256];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                InetAddress senderAddress = packet.getAddress();

                // Ignore messages from ourselves
                if (localAddresses.contains(senderAddress)) {
                    continue;
                }

                // Update the timestamp for this instance
                instanceTimestamps.put(senderAddress, System.currentTimeMillis());

                // If this is a new instance, add it and print the live instances
                if (liveInstances.add(senderAddress)) {
                    printLiveInstances();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void cleanupInstances() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Map.Entry<InetAddress, Long>> iterator = instanceTimestamps.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<InetAddress, Long> entry = iterator.next();
            if (now - entry.getValue() > 3000) { // 3 seconds timeout
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
        System.out.println("Live instances:");
        for (InetAddress address : liveInstances) {
            System.out.println(address.getHostAddress());
        }
        System.out.println("-----");
    }

    private Set<InetAddress> getLocalAddresses() throws SocketException {
        Set<InetAddress> addresses = new HashSet<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress addr = inetAddresses.nextElement();
                addresses.add(addr);
            }
        }
        return addresses;
    }
}