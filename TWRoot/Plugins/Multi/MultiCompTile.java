package TWRoot.Plugins.Multi;

import java.lang.reflect.Method;

import TWRoot.Plugins.SpaceFiller;
import TWRoot.Plugins.Tile;

public class MultiCompTile extends Tile {
    public int blocktype;
    public Method[] actions;
    public Method iaction;
    public Method oaction;
    public Object[] aparams;
    public MultiCompTile(int x, int y, boolean canCover, SpaceFiller child) {
        super(' ', (byte) 3, x, y, canCover, child);
    }
    public MultiCompTile(int x, int y, boolean canCover) {
        super(' ', (byte) 3, x, y, canCover, null);
    }
}
