package TWRoot.Plugins;

/**
 * provides default implementations of standard plugin methods
 */
@SuppressWarnings("unchecked")
public abstract class Plugin {
    /** {@link SpaceFiller} contributions */
    public static Class<? extends SpaceFiller>[] contributes = new Class[]{};
    public static Class<? extends SpaceFiller>[] contiles = new Class[]{};
    /**
     * post level gen
     * @param args
     * @throws Exception
     */
    public static void OnPostGen(Object[] args) throws Exception {}
    /**
     * called every animation frame
     * @param args
     * @throws Exception
     */
    public static void OnTick(Object[] args) throws Exception {}
    /**
     * called after player validation but before the join process is finished
     * @param args
     * @throws Exception
     */
    public static void OnBeforeJoin(Object[] args) throws Exception {}
    /**
     * called after the join process is finished
     * @param args
     * @throws Exception
     */
    public static void OnAfterJoin(Object[] args) throws Exception {}
    /**
     * called at some point after plugins have finished loading but before the server has started executing animation frames, exact point not specified
     * @param args
     * @throws Exception
     */
    public static void OnBoot(Object[] args) throws Exception {}
    /**
     * called when auth login is completed, allows plugins to deny requests to connect based on player data
     * @param args
     * @return {@code true} if player connection should be allowed and {@code false} otherwise
     * @throws Exception
     */
    public static boolean ValidatePlayerLogin(Object[] args) throws Exception {
        return true;
    }
}
