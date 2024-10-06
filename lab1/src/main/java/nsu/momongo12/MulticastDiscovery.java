package nsu.momongo12;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(MulticastDiscovery.class);

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            logger.error("Usage: java -jar multicast-discovery.jar <multicast-group-address> [port] [interface]");
            System.exit(1);
        }

        String groupAddressStr = args[0];
        int port;
        String interfaceName;

        Properties properties = new Properties();
        try (InputStream input = MulticastDiscovery.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                logger.error("Unable to find config.properties");
                System.exit(1);
            }
            properties.load(input);
        } catch (IOException ex) {
            logger.error("Error loading properties: {}", ex.getMessage(), ex);
            System.exit(1);
        }

        if (args.length == 2) {
            port = Integer.parseInt(args[1]);
        } else {
            port = Integer.parseInt(properties.getProperty("default.port", "5000"));
        }

        if (args.length == 3) {
            interfaceName = args[2];
        } else {
            interfaceName = System.getenv("MULTICAST_INTERFACE");
        }

        try {
            InetAddress groupAddress = InetAddress.getByName(groupAddressStr);
            DiscoveryService service = new DiscoveryService(groupAddress, port, properties, interfaceName);
            service.start();
            logger.info("Multicast Discovery Service started. Instance ID: {}", DiscoveryService.INSTANCE_ID);

        } catch (UnknownHostException e) {
            logger.error("Invalid multicast group address: {}", groupAddressStr, e);
            System.exit(1);
        } catch (IOException e) {
            logger.error("IOException during setup: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
