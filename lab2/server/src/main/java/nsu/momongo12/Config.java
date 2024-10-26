package nsu.momongo12;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private final Properties properties = new Properties();

    public Config(String configFileName) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFileName)) {
            if (input == null) {
                throw new IOException("Файл конфигурации " + configFileName + " не найден в classpath.");
            }
            properties.load(input);
        }
    }

    public int getServerPort() {
        return Integer.parseInt(properties.getProperty("server.port", "12345"));
    }

    public int getReportIntervalSeconds() {
        return Integer.parseInt(properties.getProperty("report.interval.seconds", "3"));
    }

    public int getBufferSize() {
        return Integer.parseInt(properties.getProperty("buffer.size", "8192"));
    }

    public String getUploadDirectory() {
        return properties.getProperty("upload.directory", "uploads");
    }

    public int getMaxFileNameLength() {
        return Integer.parseInt(properties.getProperty("max.file.name.length", "4096"));
    }

    public long getMaxFileSize() {
        return Long.parseLong(properties.getProperty("max.file.size", "1099511627776"));
    }
}

