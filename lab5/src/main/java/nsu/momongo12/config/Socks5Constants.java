package nsu.momongo12.config;

public class Socks5Constants {

    public static final byte SOCKS_VERSION = 0x05;

    // SOCKS Commands
    public static final byte COMMAND_CONNECT = 0x01;

    // SOCKS Address Types
    public static final byte ADDRESS_TYPE_IPV4 = 0x01;
    public static final byte ADDRESS_TYPE_DOMAIN = 0x03;

    // SOCKS Response Codes
    public static final byte RESPONSE_SUCCESS = 0x00;
    public static final byte RESPONSE_GENERAL_FAILURE = 0x01;
    public static final byte RESPONSE_COMMAND_NOT_SUPPORTED = 0x07;
    public static final byte RESPONSE_ADDRESS_TYPE_NOT_SUPPORTED = 0x08;
    public static final byte RESPONSE_HOST_UNREACHABLE = 0x04;

    // Authentication Methods
    public static final byte AUTH_METHOD_NO_AUTH = 0x00;
    public static final byte AUTH_METHOD_NO_ACCEPTABLE_METHODS = (byte) 0xFF;

    // Sizes
    public static final int IPV4_ADDRESS_LENGTH = 4;
    public static final int RESPONSE_HEADER_SIZE = 10;
}
