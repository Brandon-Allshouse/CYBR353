package com.delivery.services;

import com.delivery.models.Location;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * GeocodingService - Converts addresses to coordinates using Nominatim API
 * Uses simple string parsing (no external JSON library).
 */
public class GeocodingService {
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private final HttpClient httpClient;

    public GeocodingService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Geocodes an address to latitude/longitude
     */
    public Location geocode(String address) throws Exception {
        // Respect Nominatim rate limit (1 request per second)
        Thread.sleep(1100);

        String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
        String url = NOMINATIM_URL + "?q=" + encodedAddress + "&format=json&limit=1";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "DeliveryRouteOptimizer/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Geocoding service returned status: " + response.statusCode());
        }

        String body = response.body().trim();

        // Nominatim returns [] when nothing is found
        if (body.equals("[]") || body.isEmpty()) {
            throw new Exception("Address not found: " + address);
        }

        // Grab the first JSON object in the array
        int firstBrace = body.indexOf('{');
        if (firstBrace == -1) {
            throw new Exception("Unexpected geocoding response for: " + address);
        }
        String firstObj = body.substring(firstBrace);

        double lat = extractDoubleField(firstObj, "\"lat\"");
        double lon = extractDoubleField(firstObj, "\"lon\"");

        return new Location(address, lat, lon);
    }

    /**
     * Very small helper to pull a string-number field out of a JSON-ish string.
     * Example: ..."lat":"40.12345"...
     */
    private double extractDoubleField(String json, String fieldName) throws Exception {
        int idx = json.indexOf(fieldName);
        if (idx == -1) {
            throw new Exception("Missing field " + fieldName + " in geocoding response");
        }

        int colon = json.indexOf(':', idx);
        if (colon == -1) {
            throw new Exception("Invalid JSON near " + fieldName);
        }

        int startQuote = json.indexOf('"', colon + 1);
        int endQuote = json.indexOf('"', startQuote + 1);
        if (startQuote == -1 || endQuote == -1) {
            throw new Exception("Invalid number format for " + fieldName);
        }

        String valueStr = json.substring(startQuote + 1, endQuote);
        return Double.parseDouble(valueStr);
    }
}
