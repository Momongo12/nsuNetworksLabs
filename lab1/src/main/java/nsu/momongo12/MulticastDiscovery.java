package nsu.momongo12;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author momongo12
 * @version 1.0
 */
public class MulticastDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(MulticastDiscovery.class);

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            logger.error("Usage: java -jar multicast-discovery.jar <multicast-group-address> [port]");
            System.exit(1);
        }

        String groupAddressStr = args[0];
        int port;

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
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                logger.error("Invalid port number: {}", args[1]);
                System.exit(1);
                return;
            }
        } else {
            port = Integer.parseInt(properties.getProperty("default.port", "5000"));
        }

        try {
            InetAddress groupAddress = InetAddress.getByName(groupAddressStr);
            String interfaceName = properties.getProperty("network.interface", "");
            DiscoveryService service = new DiscoveryService(groupAddress, port, properties, interfaceName);
            service.start();
            logger.info("Multicast Discovery started with ID: {}", service.getOwnId());
        } catch (UnknownHostException e) {
            logger.error("Invalid multicast group address: {}", groupAddressStr, e);
            System.exit(1);
        } catch (IOException e) {
            logger.error("IOException during setup: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
