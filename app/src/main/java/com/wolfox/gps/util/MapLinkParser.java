package com.wolfox.gps.util;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses coordinates, addresses and shared map links without opening another app. */
public final class MapLinkParser {
    private static final int MAX_INPUT_LENGTH = 8192;
    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(?i)(https?://[^\\s]+|geo:[^\\s]+|google\\.navigation:[^\\s]+|"
                    + "comgooglemaps(?:url)?://[^\\s]+|maps://[^\\s]+)");
    private static final Pattern EXACT_COORDINATES = Pattern.compile(
            "^\\s*\\(?\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*[°]?\\s*[,;\\s]+\\s*"
                    + "(-?\\d{1,3}(?:\\.\\d+)?)\\s*[°]?\\s*\\)?\\s*$");
    private static final Pattern ANY_COORDINATES = Pattern.compile(
            "(-?\\d{1,3}(?:\\.\\d+)?)[\\s,;]+(-?\\d{1,3}(?:\\.\\d+)?)");
    private static final Pattern GOOGLE_AT = Pattern.compile(
            "(?i)/@(-?\\d{1,3}(?:\\.\\d+)?),(-?\\d{1,3}(?:\\.\\d+)?)");
    private static final Pattern GOOGLE_DATA = Pattern.compile(
            "(?i)!3d(-?\\d{1,3}(?:\\.\\d+)?)!4d(-?\\d{1,3}(?:\\.\\d+)?)");

    private MapLinkParser() {}

    public static final class Parsed {
        private final Double latitude;
        private final Double longitude;
        private final String query;
        private final String label;
        private final String provider;
        private final boolean mapLink;

        private Parsed(Double latitude, Double longitude, String query, String label,
                       String provider, boolean mapLink) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.query = clean(query);
            this.label = clean(label);
            this.provider = clean(provider);
            this.mapLink = mapLink;
        }

        public boolean hasCoordinates() { return latitude != null && longitude != null; }
        public double getLatitude() { return latitude == null ? 0d : latitude; }
        public double getLongitude() { return longitude == null ? 0d : longitude; }
        public String getQuery() { return query; }
        public String getLabel() { return label; }
        public String getProvider() { return provider; }
        public boolean isMapLink() { return mapLink; }
    }

    public static Parsed parse(String input) {
        String normalized = normalizeDigits(clean(input));
        if (normalized.isEmpty() || normalized.length() > MAX_INPUT_LENGTH) return null;
        String extracted = extractLink(normalized);
        String value = extracted.isEmpty() ? normalized : extracted;
        String lower = value.toLowerCase(Locale.US);

        if (lower.startsWith("geo:")) return parseGeo(value);
        if (lower.startsWith("google.navigation:")) return parseGoogleNavigation(value);
        if (lower.startsWith("maps://")) return parseCustomScheme(value, "Apple Maps");
        if (lower.startsWith("comgooglemaps://") || lower.startsWith("comgooglemapsurl://")) {
            return parseCustomScheme(value, "Google Maps");
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return parseHttp(value);
        }

        double[] pair = coordinates(value, true);
        if (pair != null) return point(pair, "إحداثيات", "إحداثيات", false);
        return new Parsed(null, null, value, value, "بحث العنوان", false);
    }

    public static boolean needsExpansion(String input) {
        String link = extractLink(normalizeDigits(clean(input)));
        if (link.isEmpty()) return false;
        try {
            String host = lower(safeUri(link).getHost());
            return "maps.app.goo.gl".equals(host) || "goo.gl".equals(host)
                    || "maps.apple".equals(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Follows only known Google/Apple hosts and rejects cross-domain redirects. */
    public static String expandKnownShortLink(String input) throws Exception {
        String link = extractLink(normalizeDigits(clean(input)));
        if (link.isEmpty()) return input;
        if (!isAllowedRedirectHost(safeUri(link).getHost())) return link;

        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(link).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(4500);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "WolFox-GPS/3.1.0 Android");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            try {
                int code = connection.getResponseCode();
                if (code < 300 || code > 399) return link;
                String location = connection.getHeaderField("Location");
                if (clean(location).isEmpty()) return link;
                URL next = new URL(new URL(link), location);
                if (!isAllowedRedirectHost(next.getHost())) return link;
                String expanded = next.toString();
                if (expanded.equals(link)) return link;
                link = expanded;
            } finally {
                connection.disconnect();
            }
        }
        return link;
    }

    private static Parsed parseGeo(String value) {
        String body = value.substring(value.indexOf(':') + 1);
        int question = body.indexOf('?');
        String center = question >= 0 ? body.substring(0, question) : body;
        Map<String, String> query = question >= 0
                ? queryMap(body.substring(question + 1)) : new LinkedHashMap<String, String>();
        String target = first(query, "q", "query", "destination", "daddr");
        double[] targetPoint = coordinates(target, false);
        if (targetPoint != null) {
            return point(targetPoint, stripCoordinateLabel(target), "Android Maps", true);
        }
        double[] centerPoint = coordinates(center, false);
        if (centerPoint != null && (!nearZero(centerPoint[0]) || !nearZero(centerPoint[1]))) {
            return point(centerPoint, target, "Android Maps", true);
        }
        if (!clean(target).isEmpty()) {
            return new Parsed(null, null, target, target, "Android Maps", true);
        }
        return new Parsed(null, null, "", "", "Android Maps", true);
    }

    private static Parsed parseGoogleNavigation(String value) {
        String body = value.substring(value.indexOf(':') + 1);
        String target = first(queryMap(body), "q", "place", "destination", "daddr");
        double[] pair = coordinates(target, false);
        if (pair != null) return point(pair, stripCoordinateLabel(target), "Android Navigation", true);
        return new Parsed(null, null, target, target, "Android Navigation", true);
    }

    private static Parsed parseCustomScheme(String value, String provider) {
        try {
            URI uri = safeUri(value);
            Map<String, String> values = queryMap(uri.getRawQuery());
            String coordinate = first(values, "coordinate", "ll", "sll", "center");
            double[] pair = coordinates(coordinate, false);
            String target = first(values, "q", "query", "address", "destination", "daddr");
            if (pair == null) pair = coordinates(target, false);
            if (pair != null) return point(pair, stripCoordinateLabel(target), provider, true);
            return new Parsed(null, null, target, target, provider, true);
        } catch (Exception ignored) {
            return new Parsed(null, null, "", "", provider, true);
        }
    }

    private static Parsed parseHttp(String value) {
        try {
            URI uri = safeUri(value);
            String host = lower(uri.getHost());
            String path = decode(uri.getRawPath());
            Map<String, String> query = queryMap(uri.getRawQuery());

            if (isAppleHost(host)) {
                String label = first(query, "name", "q", "address");
                double[] pair = coordinates(first(query,
                        "coordinate", "ll", "sll", "near", "center"), false);
                if (pair != null) return point(pair, label, "Apple Maps", true);
                String target = first(query, "q", "address", "daddr", "destination");
                pair = coordinates(target, false);
                if (pair != null) return point(pair, stripCoordinateLabel(target), "Apple Maps", true);
                if (target.isEmpty()) target = pathLabel(path);
                return new Parsed(null, null, target, label.isEmpty() ? target : label,
                        "Apple Maps", true);
            }

            if (isGoogleHost(host)) {
                Matcher data = GOOGLE_DATA.matcher(value);
                if (data.find()) {
                    return point(pair(data.group(1), data.group(2)), pathLabel(path),
                            "Google Maps", true);
                }
                Matcher at = GOOGLE_AT.matcher(value);
                if (at.find()) {
                    return point(pair(at.group(1), at.group(2)), pathLabel(path),
                            "Google Maps", true);
                }
                String target = first(query,
                        "destination", "query", "q", "center", "ll", "daddr", "saddr");
                double[] coordinates = coordinates(target, false);
                if (coordinates != null) {
                    return point(coordinates, stripCoordinateLabel(target), "Google Maps", true);
                }
                coordinates = coordinates(path, false);
                if (coordinates != null) {
                    return point(coordinates, pathLabel(path), "Google Maps", true);
                }
                String label = target.isEmpty() ? pathLabel(path) : target;
                return new Parsed(null, null, label, label, "Google Maps", true);
            }

            String target = first(query,
                    "destination", "query", "q", "center", "ll", "daddr", "where");
            double[] coordinates = coordinates(target, false);
            if (coordinates == null) coordinates = coordinates(value, false);
            if (coordinates != null) return point(coordinates, target, "رابط خريطة", true);
            return new Parsed(null, null, target, target, "رابط خريطة", true);
        } catch (Exception ignored) {
            return new Parsed(null, null, "", "", "رابط خريطة", true);
        }
    }

    private static Parsed point(double[] pair, String label, String provider, boolean mapLink) {
        if (pair == null || !valid(pair[0], pair[1])) {
            return new Parsed(null, null, "", label, provider, mapLink);
        }
        String cleanLabel = clean(label);
        return new Parsed(pair[0], pair[1], "",
                cleanLabel.isEmpty() ? provider : cleanLabel, provider, mapLink);
    }

    private static double[] pair(String latitude, String longitude) {
        try {
            double lat = Double.parseDouble(normalizeDigits(latitude));
            double lng = Double.parseDouble(normalizeDigits(longitude));
            return valid(lat, lng) ? new double[]{lat, lng} : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double[] coordinates(String value, boolean exact) {
        String normalized = normalizeDigits(decode(clean(value)))
                .replace('\u060c', ',').replace('\u066b', '.');
        Matcher matcher = (exact ? EXACT_COORDINATES : ANY_COORDINATES).matcher(normalized);
        return matcher.find() ? pair(matcher.group(1), matcher.group(2)) : null;
    }

    private static boolean valid(double lat, double lng) {
        return !Double.isNaN(lat) && !Double.isInfinite(lat)
                && !Double.isNaN(lng) && !Double.isInfinite(lng)
                && lat >= -90d && lat <= 90d && lng >= -180d && lng <= 180d;
    }

    private static boolean nearZero(double value) { return Math.abs(value) < 0.0000001d; }

    private static Map<String, String> queryMap(String raw) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (String part : raw.split("&")) {
            int equals = part.indexOf('=');
            String key = decode(equals >= 0 ? part.substring(0, equals) : part)
                    .toLowerCase(Locale.US);
            String value = decode(equals >= 0 ? part.substring(equals + 1) : "");
            if (!key.isEmpty() && !out.containsKey(key)) out.put(key, value);
        }
        return out;
    }

    private static String first(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = clean(values.get(key));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String pathLabel(String path) {
        Matcher matcher = Pattern.compile("(?i)/(?:place|search)/([^/@]+)").matcher(clean(path));
        return matcher.find() ? decode(matcher.group(1)).replace('+', ' ').trim() : "";
    }

    private static String stripCoordinateLabel(String value) {
        String clean = clean(value);
        int start = clean.indexOf('(');
        int end = clean.lastIndexOf(')');
        if (start >= 0 && end > start) return clean(clean.substring(start + 1, end));
        return "";
    }

    private static String extractLink(String input) {
        Matcher matcher = LINK_PATTERN.matcher(clean(input));
        return matcher.find()
                ? matcher.group(1).replaceAll("[\\)\\]\\}>،؛.!]+$", "") : "";
    }

    private static URI safeUri(String value) throws Exception {
        return new URI(clean(value).replace(" ", "%20"));
    }

    private static boolean isGoogleHost(String host) {
        String value = lower(host);
        return "maps.app.goo.gl".equals(value) || "goo.gl".equals(value)
                || value.matches("(^|.*\\.)google\\.(?:[a-z]{2,3}|com\\.[a-z]{2}|co\\.[a-z]{2})$")
                || value.matches("(^|.*\\.)googleusercontent\\.com$");
    }

    private static boolean isAppleHost(String host) {
        String value = lower(host);
        return "maps.apple.com".equals(value) || "maps.apple".equals(value);
    }

    private static boolean isAllowedRedirectHost(String host) {
        return isGoogleHost(host) || isAppleHost(host);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(clean(value), "UTF-8");
        } catch (Exception ignored) {
            return clean(value);
        }
    }

    static String normalizeDigits(String value) {
        String source = clean(value);
        StringBuilder out = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c >= '\u0660' && c <= '\u0669') c = (char) ('0' + c - '\u0660');
            else if (c >= '\u06f0' && c <= '\u06f9') c = (char) ('0' + c - '\u06f0');
            else if (c == '\u066b') c = '.';
            else if (c == '\u060c') c = ',';
            out.append(c);
        }
        return out.toString();
    }

    private static String lower(String value) { return clean(value).toLowerCase(Locale.US); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
