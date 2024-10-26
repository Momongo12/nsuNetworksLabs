package nsu.momongo12.model;

import lombok.Data;

/**
 * @author momongo12
 * @version 1.0
 */
@Data
public class Weather {
    private final String description;
    private final double temperature;
}
