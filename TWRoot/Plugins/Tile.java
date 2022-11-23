package TWRoot.Plugins;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public abstract class Tile extends SpaceFiller {
    public final short ftype = 1;

    public Tile(char face, byte type, int x, int y, boolean canCover, SpaceFiller child) {
        this.face = face;
        this.type = type;
        this.x = x;
        this.y = y;
        this.canCover = canCover;
        this.covering = child;
    }

    public void serialize(DataOutputStream strm, boolean useMap) throws Exception {
        strm.writeShort(ftype);
        strm.writeChar(face);
        strm.writeByte(useMap ? PluginMaster.rtilemap[type] : type);
        strm.writeInt(x);
        strm.writeInt(y);
        strm.writeBoolean(canCover);
        serializeChild(strm, useMap);
    }

    public void serialize(DataOutputStream strm) throws Exception {
        serialize(strm, false);
    }

    public static Tile deserialize(DataInputStream strm, boolean useMap) throws Exception {
        char face = strm.readChar();
        int type = strm.readByte();
        if (useMap) {
            type = PluginMaster.tilemap[type];
        }
        int x = strm.readInt();
        int y = strm.readInt();
        boolean canCover = strm.readBoolean();
        SpaceFiller child = deserializeChild(strm);
        Class<? extends SpaceFiller> cls = PluginMaster.contiles[type];
        return (Tile) cls.getConstructor(new Class[]{char.class, byte.class, int.class, int.class, boolean.class, SpaceFiller.class}).newInstance(face, (byte) type, x, y, canCover, child);
    }

    public static Tile deserialize(DataInputStream strm) throws Exception {
        return deserialize(strm, false);
    }
}
