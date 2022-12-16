package TWRoot.Plugins.Std;

import TWRoot.Plugins.SpaceFiller;
import TWRoot.Plugins.Tile;

public class TileWall extends Tile {
    public short ftype = 1;
    public static char dface = '#';

    public TileWall(byte type, int x, int y, SpaceFiller child) {
        super(TileWall.dface, type, x, y, false, child);
    }
    public TileWall(byte type, int x, int y) {
        super(TileWall.dface, type, x, y, false, null);
    }
    public TileWall(int type, int x, int y, SpaceFiller child) {
        super(TileWall.dface, (byte) type, x, y, false, child);
    }
    public TileWall(int type, int x, int y) {
        super(TileWall.dface, (byte) type, x, y, false, null);
    }
}
