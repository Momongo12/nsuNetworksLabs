package nsu.momongo12;

import java.net.InetAddress;
import java.util.Objects;

/**
 * @author momongo12
 * @version 1.0
 */
public record InstanceInfo(String id, boolean readiness, InetAddress address) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InstanceInfo that = (InstanceInfo) o;

        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "InstanceInfo{id='%s', readiness='%b', address='%s'}".formatted(id, readiness, address);
    }
}

