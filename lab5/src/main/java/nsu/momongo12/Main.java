package nsu.momongo12;

import lombok.extern.slf4j.Slf4j;
import nsu.momongo12.logic.Socks5Server;

/**
 * @author momongo12
 * @version 1.0
 */
@Slf4j
public class Main {

    public static void main(String[] args) {
        try (var server = new Socks5Server()) {
            server.start();
        } catch (Exception e) {
            log.error("Unexpected server error", e);
        }
    }
}