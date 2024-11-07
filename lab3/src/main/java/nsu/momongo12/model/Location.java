package nsu.momongo12.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author momongo12
 * @version 1.0
 */
@Data
@AllArgsConstructor
public class Location {
    private String name;
    private double latitude;
    private double longitude;
}
