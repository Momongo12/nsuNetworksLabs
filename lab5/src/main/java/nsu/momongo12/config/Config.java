package nsu.momongo12.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author momongo12
 * @version 1.0
 */
public class Config {

    private static final String CONFIG_FILE_NAME = "config.properties";

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
            if (input == null) {
                throw new RuntimeException("Файл конфигурации " + CONFIG_FILE_NAME + " не найден в classpath.");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getServerPort() {
        return Integer.parseInt(properties.getProperty("server.port", "8080"));
    }

    public static int getBufferSize() {
        return Integer.parseInt(properties.getProperty("buffer.size", "8192"));
    }
}