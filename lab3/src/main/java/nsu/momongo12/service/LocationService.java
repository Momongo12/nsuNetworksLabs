package nsu.momongo12.service;

import com.google.gson.*;
import nsu.momongo12.config.Config;
import nsu.momongo12.model.Location;
import nsu.momongo12.service.remote.ApiClient;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author momongo12
 * @version 1.0
 */
public class LocationService {

    private static final Logger logger = LoggerFactory.getLogger(LocationService.class);
    private final OkHttpClient client;
    private final Gson gson;
    private final String apiKey;
    private final int searchLimit;

    public LocationService() {
        this.client = ApiClient.getInstance().getClient();
        this.gson = new Gson();
        Config config = Config.getInstance();
        this.apiKey = config.get("graphhopper.api.key");
        this.searchLimit = config.getInt("search.limit");
    }

    public CompletableFuture<List<Location>> searchLocations(String query) {
        CompletableFuture<List<Location>> future = new CompletableFuture<>();

        HttpUrl url = HttpUrl.parse("https://graphhopper.com/api/1/geocode")
                .newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", String.valueOf(searchLimit))
                .addQueryParameter("locale", "en")
                .addQueryParameter("key", apiKey)
                .build();

        Request request = new Request.Builder().url(url).build();

        logger.info("Sending request to GraphHopper Geocoding API: {}", url);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.error("Failed to get locations", e);
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
                List<Location> locations = parseLocations(responseBody);
                future.complete(locations);
            }
        });

        return future;
    }

    private List<Location> parseLocations(String json) {
        List<Location> locations = new ArrayList<>();
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        JsonArray hits = jsonObject.getAsJsonArray("hits");
        for (JsonElement element : hits) {
            JsonObject hit = element.getAsJsonObject();
            String name = hit.get("name").getAsString();
            double lat = hit.get("point").getAsJsonObject().get("lat").getAsDouble();
            double lng = hit.get("point").getAsJsonObject().get("lng").getAsDouble();
            locations.add(new Location(name, lat, lng));
        }
        return locations;
    }
}
