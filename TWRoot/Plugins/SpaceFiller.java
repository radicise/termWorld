package TWRoot.Plugins;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public abstract class SpaceFiller {
    public int x;
	public int y;
	public char face;
    public final short ftype = -1;
    public byte type = 0;
    public boolean canCover = false;
    public SpaceFiller covering = null;

    public void animate() throws Exception {
        //
    }

    public void schedule(int opcode, short params) {}

    public void destroy() throws Exception {
        if (covering == null) {
            covering = new TileEmpty(1, x, y, null);
        }
        PluginMaster.level.terrain.spaces[PluginMaster.level.terrain.width * y + x] = covering;
    }

    public void serializeChild(DataOutputStream strm, boolean useMap) throws Exception {
        if (covering == null) {
            strm.writeByte(0);
        } else {
            strm.writeByte(1);
            covering.serialize(strm, useMap);
        }
    }

    public void serializeChild(DataOutputStream strm) throws Exception {
        serializeChild(strm, false);
    }

    public void serialize(DataOutputStream strm, boolean useMap) throws Exception {
        strm.writeShort(-1);
        serializeChild(strm, useMap);
    }

    public void serialize(DataOutputStream strm) throws Exception {
        serialize(strm, false);
    }

    public static SpaceFiller deserializeChild(DataInputStream strm, boolean useMap) throws Exception {
        boolean hasChild = strm.readBoolean();
        if (hasChild) {
            return SpaceFiller.deserialize(strm, useMap);
        } else {
            return null;
        }
    }

    public static SpaceFiller deserializeChild(DataInputStream strm) throws Exception {
        return deserializeChild(strm, false);
    }

    public static SpaceFiller deserialize(DataInputStream strm, boolean useMap) throws Exception {
        int tftype = strm.readShort();
        switch (tftype) {
            case 0:
                return Entity.deserialize(strm, useMap);
            case 1:
                return Tile.deserialize(strm, useMap);
            default:
                throw new Exception("BAD TYPE");
        }
    }

    public static SpaceFiller deserialize(DataInputStream strm) throws Exception {
        return deserialize(strm, false);
    }
}
