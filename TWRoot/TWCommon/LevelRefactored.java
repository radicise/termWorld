package TWRoot.TWCommon;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;

import TWRoot.Plugins.Entity;
import TWRoot.Plugins.EntityPlayer;
import TWRoot.Plugins.TileEmpty;

public class LevelRefactored {
    public static final int roomWidth = 5;
	public static final int roomHeight = 4;
    /**disable sending updates to remote connection */
    public static boolean noUpdates = true;
    public FixedFrame terrain;
	public long age;
	public int VID;
	public int spawnX;
	public int spawnY;
    public HashMap<byte[], EntityPlayer> playerMap;
    public DataOutputStream globalOut;
	public LevelRefactored(FixedFrame terrain, long age, int VID, int spawnX, int spawnY) {
		this.terrain = terrain;
        this.age = age;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
		this.VID = VID;
		playerMap = new HashMap<byte[], EntityPlayer>();
	}
    public void update(DataInputStream strm) throws Exception {
        int controlcode;
        while ((controlcode = strm.readByte()) != 0x54) {
            switch (controlcode) {
                case (0):
                    terrain.spaces[strm.readInt()].destroy();
                    break;
                case (1):
                    ((Entity) terrain.spaces[strm.readInt()]).moveBy(strm.readInt(), strm.readInt(), 0);
                    break;
                case (2):
                    terrain.spaces[strm.readInt()].face = strm.readChar();
                    break;
            }
        }
    }
    public void animate() throws Exception {
        globalOut.writeByte(0x43);
        terrain.animate();
        globalOut.writeByte(0x54);
    }
    public void display() throws Exception {
        terrain.display();
    }
    public void addDestruction(int x, int y) throws Exception {
        if (LevelRefactored.noUpdates) {
            return;
        }
        globalOut.writeByte(0x00);
        globalOut.writeInt(y*terrain.width+x);
    }
    public void addMove(int sfid, int Dx, int Dy) throws Exception {
        if (LevelRefactored.noUpdates) {
            return;
        }
        globalOut.writeByte(0x01);
        globalOut.writeInt(sfid);
        globalOut.writeInt(Dx);
        globalOut.writeInt(Dy);
    }
    public void addFaceChange(int sfid, char face) throws Exception {
        if (LevelRefactored.noUpdates) {
            return;
        }
        globalOut.writeByte(0x02);
        globalOut.writeInt(sfid);
        globalOut.writeChar(face);
    }
    public void zip(DataOutputStream strm) throws Exception {
		serialize(strm);
		strm.writeInt(playerMap.size());
		for (byte[] pid : playerMap.keySet()) {
			playerMap.get(pid).serialize(strm);
		}
	}
	public static LevelRefactored unzip(DataInputStream strm) throws Exception {
		LevelRefactored lvl = deserialize(strm);
		int pmSize = strm.readInt();
		lvl.playerMap = new HashMap<>(pmSize);
		for (int i = 0; i < pmSize; i ++) {
			strm.read();
			EntityPlayer ep = EntityPlayer.fromDataStream(strm);
			lvl.playerMap.put(ep.playerID, ep);
		}
		return lvl;
	}
    public synchronized void serialize(DataOutputStream strm, boolean useMap) throws Exception {
		strm.writeInt(VID);
		strm.writeLong(age);
		strm.writeInt(spawnX);
		strm.writeInt(spawnY);
		terrain.serialize(strm, useMap);
	}
    public synchronized void serialize(DataOutputStream strm) throws Exception {
        serialize(strm, false);
    }
	public static LevelRefactored deserialize(DataInputStream strm, boolean useMap) throws Exception {
		int VID = strm.readInt();
		long age = strm.readLong();
		int spawnX = strm.readInt();
		int spawnY = strm.readInt();
		return new LevelRefactored(FixedFrame.deserialize(strm, useMap), age, VID, spawnX, spawnY);
	}
    public static LevelRefactored deserialize(DataInputStream strm) throws Exception {
        return deserialize(strm, false);
    }
    public static LevelRefactored generate(int width, int height, long seed) throws Exception {
        FixedFrame terrain = new FixedFrame(width, height);
        for (int i = 0; i < terrain.height; i ++) {
            for (int j = 0; j < terrain.width; j ++) {
                terrain.spaces[i*terrain.width+j] = new TileEmpty(1, j, i, null);
            }
        }
        return new LevelRefactored(terrain, 0L, 0, 1, 1);
    }
    public String debugRender() {
        StringBuilder sb = new StringBuilder(terrain.spaces.length+terrain.height);
        for (int y = 0; y < terrain.height; y ++) {
            for (int x = 0; x < terrain.width; x ++) {
                char f = terrain.spaces[y*terrain.width+x].face;
                sb.append(f == ' ' ? '\uFFFD' : f);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
