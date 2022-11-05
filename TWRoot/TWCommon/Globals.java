package TWRoot.TWCommon;

public class Globals {
    public static final String versionString = "0.0.2";
    public static final int version = 2;
    public static final int defaultHostPort = 15651;
    public static final int debugLevel = Integer.valueOf(System.getenv().getOrDefault("DEBUG", "0"));
}
