package TWRoot.Plugins;

public enum PluginHook {
    PostGen,
    Tick,
    BeforeJoin,
    AfterJoin,
    Boot;
    public static String getUsableName(PluginHook hook) {
        switch (hook) {
            case PostGen:
                return "OnPostGen";
            case Tick:
                return "OnTick";
            case BeforeJoin:
                return "OnBeforeJoin";
            case AfterJoin:
                return "OnAfterJoin";
            case Boot:
                return "OnBoot";
        }
        return null;
    }
}
