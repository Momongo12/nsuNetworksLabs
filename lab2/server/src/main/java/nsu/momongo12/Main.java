package nsu.momongo12;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Использование: java -jar nio-file-server.jar <config.properties>");
            return;
        }

        String configFileName = args[0];
        try {
            Config config = new Config(configFileName);
            Server server = new Server(config);
            server.start();
        } catch (Exception e) {
            logger.error("Ошибка при запуске сервера: {}", e.getMessage(), e);
        }
    }
}

