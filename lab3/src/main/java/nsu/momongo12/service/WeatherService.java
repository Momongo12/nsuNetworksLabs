package nsu.momongo12.service;

import com.google.gson.*;
import nsu.momongo12.config.Config;
import nsu.momongo12.model.Location;
import nsu.momongo12.model.Weather;
import nsu.momongo12.service.remote.ApiClient;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author momongo12
 * @version 1.0
 */
public class WeatherService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);
    private final OkHttpClient client;
    private final Gson gson;
    private final String apiKey;

    public WeatherService() {
        this.client = ApiClient.getInstance().getClient();
        this.gson = new Gson();
        Config config = Config.getInstance();
        this.apiKey = config.get("openweathermap.api.key");
    }

    public CompletableFuture<Weather> fetchWeather(Location location) {
        CompletableFuture<Weather> future = new CompletableFuture<>();

        HttpUrl url = HttpUrl.parse("https://api.openweathermap.org/data/2.5/weather")
                .newBuilder()
                .addQueryParameter("lat", String.valueOf(location.getLatitude()))
                .addQueryParameter("lon", String.valueOf(location.getLongitude()))
                .addQueryParameter("appid", apiKey)
                .build();

        Request request = new Request.Builder().url(url).build();

        logger.info("Sending request to OpenWeatherMap API: {}", url);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.error("Failed to get weather", e);
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorMsg = "Unexpected response code: " + response.code();
                    logger.error(errorMsg);
                    future.completeExceptionally(new IOException(errorMsg));
                    return;
                }
                String responseBody = response.body().string();
                Weather weather = parseWeather(responseBody);
                future.complete(weather);
            }
        });

        return future;
    }

    private Weather parseWeather(String json) {
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        String description = jsonObject.getAsJsonArray("weather")
                .get(0).getAsJsonObject().get("description").getAsString();
        double temperature = jsonObject.getAsJsonObject("main").get("temp").getAsDouble();
        return new Weather(description, temperature);
    }
}
