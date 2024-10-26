package nsu.momongo12;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import nsu.momongo12.model.Location;
import nsu.momongo12.model.Place;
import nsu.momongo12.model.Result;
import nsu.momongo12.model.Weather;
import nsu.momongo12.service.LocationService;
import nsu.momongo12.service.PlaceService;
import nsu.momongo12.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private final LocationService locationService;
    private final WeatherService weatherService;
    private final PlaceService placeService;
    private final CountDownLatch latch = new CountDownLatch(1);

    public Main() {
        locationService = new LocationService();
        weatherService = new WeatherService();
        placeService = new PlaceService();
    }

    public static void main(String[] args) {
        Main app = new Main();
        app.run();
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter a location name: ");
        String query = scanner.nextLine();

        locationService.searchLocations(query).thenAccept(locations -> {
            if (locations.isEmpty()) {
                System.out.println("No locations found.");
                exit();
                return;
            }

            // Display locations
            for (int i = 0; i < locations.size(); i++) {
                System.out.println((i + 1) + ". " + locations.get(i).getName());
            }

            System.out.print("Select a location by number: ");

            int choice = -1;
            while (choice < 1 || choice > locations.size()) {
                try {
                    choice = Integer.parseInt(scanner.nextLine());
                    if (choice < 1 || choice > locations.size()) {
                        System.out.print("Invalid choice. Please select a valid number: ");
                    }
                } catch (NumberFormatException e) {
                    System.out.print("Invalid input. Please enter a number: ");
                }
            }

            Location selectedLocation = locations.get(choice - 1);

            // Fetch weather and places
            displayResults(selectedLocation);

        }).exceptionally(ex -> {
            logger.error("An error occurred while searching for locations", ex);
            System.err.println("An error occurred while searching for locations: " + ex.getMessage());
            exit();
            return null;
        });

        // Wait for completion
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void displayResults(Location location) {
        fetchWeatherAndPlaces(location).thenAccept(result -> {
            // Display weather
            System.out.println("\nWeather in " + location.getName() + ":");
            System.out.println("Description: " + result.getWeather().getDescription());
            System.out.printf("Temperature: %.2f°C%n", result.getWeather().getTemperature() - 273.15);

            // Display places
            System.out.println("\nInteresting Places:");
            for (Place place : result.getPlaces()) {
                System.out.println("Name: " + place.getName());
                System.out.println("Description: " + (place.getDescription() != null ? place.getDescription() : "No description available."));
                System.out.println("----------------------");
            }
            exit();
        }).exceptionally(ex -> {
            logger.error("An error occurred while fetching data", ex);
            System.err.println("An error occurred while fetching data: " + ex.getMessage());
            exit();
            return null;
        });
    }

    public CompletableFuture<Result> fetchWeatherAndPlaces(Location location) {
        CompletableFuture<Weather> weatherFuture = weatherService.fetchWeather(location);
        CompletableFuture<List<Place>> placesFuture = placeService.fetchPlaces(location);

        return CompletableFuture.allOf(weatherFuture, placesFuture)
                .thenCompose(voidResult -> {
                    CompletableFuture<List<Place>> detailedPlacesFuture = placesFuture.thenCompose(places -> {
                        List<CompletableFuture<Place>> placeFutures = new ArrayList<>();
                        for (Place place : places) {
                            placeFutures.add(placeService.fetchPlaceDescription(place));
                        }
                        return CompletableFuture.allOf(placeFutures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> {
                                    List<Place> detailedPlaces = new ArrayList<>();
                                    for (CompletableFuture<Place> placeFuture : placeFutures) {
                                        detailedPlaces.add(placeFuture.join());
                                    }
                                    return detailedPlaces;
                                });
                    });

                    return detailedPlacesFuture.thenCombine(weatherFuture, (detailedPlaces, weather) ->
                            new Result(weather, detailedPlaces));
                });
    }

    private void exit() {
        latch.countDown();
    }
}
