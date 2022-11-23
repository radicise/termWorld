package TWRoot.Plugins;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InvalidObjectException;
import java.util.Arrays;

public abstract class Entity extends SpaceFiller {
	public final short ftype = 0;
	public short health;
	public volatile long data;
	public byte type = 0;
	public byte color;
	protected int xO;
	protected int yO;
	public Item[] inventory;
	static final int invSpace = 0;
	protected Entity() {
	}
	Entity(int x, int y, long data, short health) {
		inventory = new Item[invSpace];
		face = '\u203c';
		this.x = x;
		this.y = y;
		xO = x;
		yO = y;
		this.data = data;
		this.health = health;
		color = 16;
	}
	public static void getConfigProps() throws Exception {
		throw new Exception("NOT IMPLEMENTED");
	}
	public void serialize(DataOutputStream dataOut, boolean useMap) throws Exception {//TODO Include face value
		// System.out.println("type " + type);
		dataOut.writeInt(ftype);
		dataOut.write(useMap ? PluginMaster.rentmap[type] : type);
		dataOut.writeInt(x);
		dataOut.writeInt(y);
		dataOut.writeLong(data);
		dataOut.writeShort(health);
	}
	public void serialize(DataOutputStream strm) throws Exception {
		serialize(strm, false);
	}
	public static Entity fromDataStream(DataInputStream readFrom) throws Exception {
		return null;
	}
	public static Entity deserialize(DataInputStream strm, boolean useMap) throws Exception {
		int etype = strm.read();
		if (useMap) {
			etype = PluginMaster.entmap[etype];
		}
		// System.out.println("type " + etype);
		switch (etype) {
			case (0):
				return Entity.fromDataStream(strm);
			default:
				if (PluginMaster.contributed.length > etype) {
					return (Entity) PluginMaster.contributed[etype].getMethod("fromDataStream", new Class[]{DataInputStream.class}).invoke(null, strm);
				}
				System.out.println(Arrays.toString(strm.readNBytes(25)));
				throw new InvalidObjectException("Invalid Entity type: " + etype);
		}
	}
	public static Entity deserialize(DataInputStream strm) throws Exception {
		return deserialize(strm, false);
	}
	public boolean checkDeath() throws Exception {
		if (health > 0) {
			return false;
		}
		destroy();
		// PluginMaster.level.entities.remove((((long) PluginMaster.level.ent[EID].x) << 32) | ((long) PluginMaster.level.ent[EID].y));
		// PluginMaster.level.ent[EID] = null;
		// Server.buf.put((byte) 7).putInt(x).putInt(y);
		return true;
	}
	public void animate() throws Exception {//,,,inventory,health,teleport,[reserved],face
		if (checkDeath()) {
			return;
		}
		face = (face == '\u203c') ? '\u0021' : '\u203c';
		// Server.buf.put((byte) 1).putInt(x).putInt(y).putChar(face);
	}
	int give(Item given) throws Exception {//Only use when Server.buf is safe to write to
		int n = 0;
		int g = 0;
		for (int i = 0; i < inventory.length; i++) {
			if (inventory[i] == null) {
				continue;
			}
			if (inventory[i].thing == given.thing) {
				n = Math.min(((int) given.quantity) + ((int) inventory[i].quantity), 127);
				n -= inventory[i].quantity;
				inventory[i].quantity += n;
				g += n;
				if (n != 0) {
					// PluginMaster.level.buf.put((byte) 16).putInt(x).putInt(y).putInt(i);
					// inventory[i].serialize(PluginMaster.level.bstr);
					given.quantity -=  n;
					if (given.quantity == 0) {
						return g;
					}
				}
			}
		}
		for (int i = 0; i < inventory.length; i++) {
			if (inventory[i] == null) {
				inventory[i] = new Item(given.thing, given.quantity);
				// PluginMaster.level.buf.put((byte) 16).putInt(x).putInt(y).putInt(i);
				// inventory[i].serialize(Server.bstr);
				g += given.quantity;
				given.quantity = 0;
				break;
			}
		}
		return g;
	}
	void onMove() throws Exception {}
	public boolean moveBy(int Dx, int Dy, int d) throws Exception {
		if (_moveBy(Dx, Dy, d)) {
			if (d == 0) {
				PluginMaster.level.addMove(PluginMaster.level.terrain.width * y + x, Dx, Dy);
			}
			onMove();
			return true;
		}
		return false;
	}
	private boolean _moveBy(int Dx, int Dy, int d) throws Exception {//Only use when Server.buf is safe to write to, don't move by anything that would move the player out of the bounds of int values if not corrected
		if (d > 15) {
			return false;
		}
		if ((d > 0) && (health > -30000)) {
			health--;
		}
		if ((Dx == 0) && (Dy == 0)) {
			return false;
		}
		int mX;
		int mY;
		if ((Dx < 0) && (x < (-Dx))) {
			mX = 0;
		}
		else if ((x > 0) && ((x + Dx) >= PluginMaster.level.terrain.width)) {
			mX = PluginMaster.level.terrain.width - 1;
		}
		else {
			mX = x + Dx;
		}
		if ((Dy < 0) && (y < (-Dy))) {
			mY = 0;
		}
		else if ((Dy > 0) && ((y + Dy) >= PluginMaster.level.terrain.height)) {
			mY = PluginMaster.level.terrain.width - 1;
		}
		else {
			mY = y + Dy;
		}
		if ((mX == x) && (mY == y)) {
			return false;
		}
		int targetidx = PluginMaster.level.terrain.width * mY + mX;
		int cidx = PluginMaster.level.terrain.width * y + x;
		if (PluginMaster.level.terrain.spaces[targetidx].ftype == 1) {
			if (PluginMaster.tileSolidity[PluginMaster.level.terrain.spaces[targetidx].type]) {
				return false;
			}
			SpaceFiller store = covering;
			covering = PluginMaster.level.terrain.spaces[targetidx];
			PluginMaster.level.terrain.spaces[targetidx] = this;
			PluginMaster.level.terrain.spaces[cidx] = store;
			return true;
		}
		if (PluginMaster.level.terrain.spaces[targetidx].ftype == 0) {
			Entity test = (Entity) PluginMaster.level.terrain.spaces[targetidx];
			if (test.getClass().equals(EntityItem.class)) {
				give(((EntityItem) test).item);
				if (((EntityItem) PluginMaster.level.terrain.spaces[targetidx]).item.quantity == 0) {
					test.destroy();
				}
			}
			if (test.moveBy(Dx, Dy, d + 1) ? true : (test.moveBy(-Dy, Dx, d + 1) ? true : (test.moveBy(Dy, -Dx, d + 1) ? true : test.moveBy(-(2 * Dx), -(2 * Dy), d + 1)))) {
				PluginMaster.level.terrain.spaces[cidx] = covering;
				covering = PluginMaster.level.terrain.spaces[targetidx];
				PluginMaster.level.terrain.spaces[targetidx] = this;
				// return _moveBy(Dx, Dy, d);
				// int k = PluginMaster.level.entities.get((((long) x) << 32) ^ ((long) y));
				// PluginMaster.level.entities.remove((((long) x) << 32) ^ ((long) y));
				// PluginMaster.level.entities.put((((long) mX) << 32) ^ ((long) mY), k);
				// x = mX;
				// y = mY;
				// // Server.buf.put((byte) 4).putInt(xO).putInt(yO).putInt(x).putInt(y);
				// xO = x;
				// yO = y;
				// return true;
			}
			return false;
		}
		return false;
		// int k = PluginMaster.level.entities.get((((long) x) << 32) ^ ((long) y));
		// PluginMaster.level.entities.remove((((long) x) << 32) ^ ((long) y));
		// PluginMaster.level.entities.put((((long) mX) << 32) ^ ((long) mY), k);
		// x = mX;
		// y = mY;
		// // Server.buf.put((byte) 4).putInt(xO).putInt(yO).putInt(x).putInt(y);
		// xO = x;
		// yO = y;
		// return true;
	}
	public void sendFace(char face) {
		// Server.buf.put((byte) 1).putInt(x).putInt(y).putChar(face);
		this.face = face;
	}
}
