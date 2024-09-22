package nsu.momongo12;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MulticastDiscovery {

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: java MulticastDiscovery <multicast-group-address> [port]");
            System.exit(1);
        }

        String groupAddressStr = args[0];
        int port = 5000; // default port

        if (args.length == 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println(STR."Invalid port number: \{args[1]}");
                System.exit(1);
            }
        }

        try {
            InetAddress groupAddress = InetAddress.getByName(groupAddressStr);
            // Create an instance of the discovery service
            DiscoveryService service = new DiscoveryService(groupAddress, port);
            service.start();

        } catch (UnknownHostException e) {
            System.err.println(STR."Invalid multicast group address: \{groupAddressStr}");
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            System.err.println(STR."IOException during setup: \{e.getMessage()}");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
