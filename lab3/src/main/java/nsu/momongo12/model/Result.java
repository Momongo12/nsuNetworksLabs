package nsu.momongo12.model;

import lombok.Data;

import java.util.List;

/**
 * @author momongo12
 * @version 1.0
 */
@Data
public class Result {
    private final Weather weather;
    private final List<Place> places;
}
