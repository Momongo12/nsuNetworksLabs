package nsu.momongo12;

import lombok.extern.slf4j.Slf4j;

/**
 * @author momongo12
 * @version 1.0
 */
@Slf4j
public class Main {

    public static void main(String[] args) {
        try {
            new Socks5Server().start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}