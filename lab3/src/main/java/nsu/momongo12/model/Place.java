package nsu.momongo12.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * @author momongo12
 * @version 1.0
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class Place {
    private final String xid;
    private final String name;
    private String description;
}
