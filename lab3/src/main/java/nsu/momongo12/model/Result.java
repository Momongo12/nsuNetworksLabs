package nsu.momongo12.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * @author momongo12
 * @version 1.0
 */
@Data
@AllArgsConstructor
public class Result {
    private Weather weather;
    private List<Place> places;
}
