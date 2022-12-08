package TWRoot.Plugins;

public class TileEmpty extends Tile {
    public short ftype = 1;
    public static char dface = ' ';

    public TileEmpty(byte type, int x, int y, SpaceFiller child) {
        super(TileEmpty.dface, type, x, y, true, child);
    }
    public TileEmpty(byte type, int x, int y) {
        super(TileEmpty.dface, type, x, y, true, null);
    }
    public TileEmpty(int type, int x, int y, SpaceFiller child) {
        super(TileEmpty.dface, (byte) type, x, y, true, child);
    }
    public TileEmpty(int type, int x, int y) {
        super(TileEmpty.dface, (byte) type, x, y, true, null);
    }
    public void destroy() throws Exception {
        if (covering == null) {
            return;
        }
        PluginMaster.level.terrain.spaces[PluginMaster.level.terrain.width * y + x] = covering;
    }
}
