package TWRoot.Plugins.Multi;

import TWRoot.Plugins.SpaceFiller;

public class MultiFrame {
    public static int[] watchids;
    public SpaceFiller[] tiles;

    public static boolean validate(SpaceFiller[] blocks) {
        return false;
    }
    public static void init() {}
    public void disassemble() {}
    public void destroy() {}
    public MultiFrame(SpaceFiller[] blocks) {}
}
