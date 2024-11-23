package nsu.momongo12.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author momongo12
 * @version 1.0
 */
public class Config {
    private static Config instance;
    private final Properties properties;

    private Config() {
        properties = new Properties();
        try (InputStream input = Config.class.getResourceAsStream("/config.properties")) {
            if (input == null) {
                throw new IOException("config.properties not found");
            }
            properties.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load configuration", ex);
        }
    }

    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public int getInt(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }
}
