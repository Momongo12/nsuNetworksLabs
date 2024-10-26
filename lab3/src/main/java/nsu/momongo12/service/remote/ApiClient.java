package nsu.momongo12.service.remote;

import okhttp3.OkHttpClient;

/**
 * @author momongo12
 * @version 1.0
 */
public class ApiClient {

    private static ApiClient instance;
    private final OkHttpClient client;

    private ApiClient() {
        client = new OkHttpClient.Builder()
                .build();
    }

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    public OkHttpClient getClient() {
        return client;
    }
}
