package TWRoot.Plugins;

/**
 * {@link Enum} signifying validation rule names
 */
public enum PluginValidator {
    PlayerLogin;
    public static String getUsableName(PluginValidator hook) {
        switch (hook) {
            case PlayerLogin:
                return "ValidatePlayerLogin";
        }
        return null;
    }
}
