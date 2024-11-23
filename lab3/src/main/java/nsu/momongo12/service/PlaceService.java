package nsu.momongo12.service;

import com.google.gson.*;
import nsu.momongo12.config.Config;
import nsu.momongo12.model.Location;
import nsu.momongo12.model.Place;
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
public class PlaceService {

    private static final Logger logger = LoggerFactory.getLogger(PlaceService.class);
    private final OkHttpClient client;
    private final Gson gson;
    private final String apiKey;
    private final int searchLimit;
    private final int radius;

    public PlaceService() {
        this.client = ApiClient.getInstance().getClient();
        this.gson = new Gson();
        Config config = Config.getInstance();
        this.apiKey = config.get("opentripmap.api.key");
        this.searchLimit = config.getInt("search.limit");
        this.radius = config.getInt("radius");
    }

    public CompletableFuture<List<Place>> fetchPlaces(Location location) {
        CompletableFuture<List<Place>> future = new CompletableFuture<>();

        HttpUrl url = HttpUrl.parse("https://api.opentripmap.com/0.1/en/places/radius")
                .newBuilder()
                .addQueryParameter("radius", String.valueOf(radius))
                .addQueryParameter("lon", String.valueOf(location.getLongitude()))
                .addQueryParameter("lat", String.valueOf(location.getLatitude()))
                .addQueryParameter("limit", String.valueOf(searchLimit))
                .addQueryParameter("apikey", apiKey)
                .build();

        Request request = new Request.Builder().url(url).build();

        logger.info("Sending request to OpenTripMap API (radius): {}", url);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.error("Failed to get places", e);
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
                List<Place> places = parsePlaces(responseBody);
                future.complete(places);
            }
        });

        return future;
    }

    private List<Place> parsePlaces(String json) {
        List<Place> places = new ArrayList<>();
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        JsonArray features = jsonObject.getAsJsonArray("features");
        for (JsonElement element : features) {
            JsonObject properties = element.getAsJsonObject().getAsJsonObject("properties");
            String xid = properties.get("xid").getAsString();
            String name = properties.has("name") ? properties.get("name").getAsString() : "Unnamed Place";
            places.add(new Place(xid, name));
        }
        return places;
    }

    public CompletableFuture<Place> fetchPlaceDescription(Place place) {
        CompletableFuture<Place> future = new CompletableFuture<>();

        HttpUrl url = HttpUrl.parse("https://api.opentripmap.com/0.1/en/places/xid/" + place.getXid())
                .newBuilder()
                .addQueryParameter("apikey", apiKey)
                .build();

        Request request = new Request.Builder().url(url).build();

        logger.info("Sending request to OpenTripMap API (xid): {}", url);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.error("Failed to get place description for xid: {}", place.getXid(), e);
                place.setDescription("No description available.");
                future.complete(place);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorMsg = "Unexpected response code: " + response.code();
                    logger.error(errorMsg);
                    place.setDescription("No description available.");
                    future.complete(place);
                    return;
                }
                String responseBody = response.body().string();
                String description = parsePlaceDescription(responseBody);
                place.setDescription(description);
                future.complete(place);
            }
        });

        return future;
    }

    private String parsePlaceDescription(String json) {
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        if (jsonObject.has("wikipedia_extracts") && jsonObject.getAsJsonObject("wikipedia_extracts").has("text")) {
            return jsonObject.getAsJsonObject("wikipedia_extracts").get("text").getAsString();
        } else if (jsonObject.has("info") && jsonObject.getAsJsonObject("info").has("descr")) {
            return jsonObject.getAsJsonObject("info").get("descr").getAsString();
        } else {
            return "No description available.";
        }
    }
}
