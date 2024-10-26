package nsu.momongo12.model;

import lombok.Data;

/**
 * @author momongo12
 * @version 1.0
 */
@Data
public class Location {
    private final String name;
    private final double latitude;
    private final double longitude;
}
