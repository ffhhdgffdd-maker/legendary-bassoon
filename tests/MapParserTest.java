import com.wolfox.gps.util.MapLinkParser;

public final class MapParserTest {
    private static void point(String input, double lat, double lng) {
        MapLinkParser.Parsed parsed = MapLinkParser.parse(input);
        if (parsed == null || !parsed.hasCoordinates()
                || Math.abs(parsed.getLatitude() - lat) > 0.000001
                || Math.abs(parsed.getLongitude() - lng) > 0.000001) {
            throw new AssertionError("not parsed: " + input);
        }
    }

    private static void query(String input, String expected) {
        MapLinkParser.Parsed parsed = MapLinkParser.parse(input);
        if (parsed == null || !parsed.getQuery().contains(expected)) {
            throw new AssertionError("query not parsed: " + input + " -> "
                    + (parsed == null ? "null" : parsed.getQuery()));
        }
    }

    public static void main(String[] args) {
        point("٢٤٫٧١٣٦، ٤٦٫٦٧٥٣", 24.7136, 46.6753);
        point("https://www.google.com/maps/place/Riyadh/@24.7136,46.6753,14z", 24.7136, 46.6753);
        point("https://www.google.com/maps/search/?api=1&query=24.7136%2C46.6753", 24.7136, 46.6753);
        point("geo:0,0?q=24.7136,46.6753(Riyadh)", 24.7136, 46.6753);
        point("google.navigation:q=24.7136,46.6753", 24.7136, 46.6753);
        point("https://maps.apple.com/?ll=24.7136%2C46.6753&q=Riyadh", 24.7136, 46.6753);
        point("maps://?q=24.7136%2C46.6753", 24.7136, 46.6753);
        query("google.navigation:q=Kingdom+Centre,+Riyadh", "Kingdom Centre");
        query("https://maps.apple.com/?q=Riyadh", "Riyadh");
        System.out.println("MapLinkParser: 9/9 passed");
    }
}
