package TWRoot.Plugins.Std;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;

import TWRoot.Plugins.Entity;
import TWRoot.Plugins.Item;
import TWRoot.Plugins.PluginMaster;
import TWRoot.Plugins.SpaceFiller;
// import termWorld.Server;
class Explosive extends Entity {
	// entity and tile resistances, -1 is unbreakable
	private static int[] entresist = new int[]{-1, 0, -1, -1};
	private static int[] tileresist = new int[]{-1, -1, 0};
	static final int invSpace = 0;
	int size;
	boolean healed;
	public static void parseConfig(String data) {
		String[] conf = data.split("\\|");
		ArrayList<Integer> er = new ArrayList<>(4);
		er.add(-1);
		er.add(0);
		er.add(-1);
		er.add(-1);
		ArrayList<Integer> tr = new ArrayList<>(2);
		er.add(-1);
		er.add(-1);
		for (String s : conf[0].split("[\\s]+")) {
			er.add(Integer.parseInt(s));
		}
		for (String s : conf[1].split("[\\s]+")) {
			tr.add(Integer.parseInt(s));
		}
	}
	Explosive(int x, int y, long data, short fuse, int size) {
		type = 5;
		inventory = new Item[invSpace];
		face = '\u2022';
		this.x = x;
		this.y = y;
		xO = x;
		yO = y;
		this.data = data;
		this.health = fuse;
		color = 14;
		this.size = size;
	}
	public void serialize(DataOutputStream dataOut) throws Exception {
		super.serialize(dataOut);
		dataOut.write(size);
	}
	public static Explosive fromDataStream(DataInputStream readFrom) throws Exception {
		Explosive e = new Explosive(readFrom.readInt(), readFrom.readInt(), readFrom.readLong(), readFrom.readShort(), readFrom.readUnsignedByte());
		e.face = readFrom.readChar();
		return e;
	}
	void explosion() throws Exception {
		int right = Math.min(x+size, PluginMaster.level.terrain.width);
		int top = Math.max(y-size, 0);
		int bot = Math.min(y+size, PluginMaster.level.terrain.height);
		for (int i = Math.max(x-size, 0); i < right; i ++) {
			for (int j = top; j < bot; j ++) {
				int spaceid = j * PluginMaster.level.terrain.width + i;
				SpaceFiller spot = PluginMaster.level.terrain.spaces[spaceid];
				boolean isEnt = spot.ftype == 0;
				int[] resistlst = isEnt ? entresist : tileresist;
				int clsid = PluginMaster.getClassId(spot.getClass(), isEnt);
				// catch cases where the class was not found
				if (clsid == -1) {
					continue;
				}
				if (clsid >= resistlst.length || resistlst[clsid] == -1) {
					continue;
				}
				spot.destroy();
			}
		}
	}
	public void animate() throws Exception {
		if (checkDeath()) {
			explosion();
			return;
		}
		health--;
		if ((--color) == 8) {
			color = 14;
		}
		// TODO implement color update
		switch (face) {
			case ('\u2022'):
				face = '\u25d8';
				break;
			case ('\u25d8'):
				face = '\u25cb';
				break;
			case ('\u25cb'):
				face = '\u25d9';
				break;
			case ('\u25d9'):
			default:
				face = '\u2022';
		}
		sendFace(face);
	}
}
