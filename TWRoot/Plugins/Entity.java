package TWRoot.Plugins;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InvalidObjectException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import TWRoot.TWCommon.Globals;

public abstract class Entity extends SpaceFiller {
	public short health;
	public volatile long data;
	public byte type = 0;
	public byte color;
	protected int xO;
	protected int yO;
	private int mX;
	private int mY;
	public Item[] inventory;
	static final int invSpace = 0;
	protected Entity() {
	}
	Entity(int x, int y, long data, short health) {
		ftype = 0;
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
	private void _backup_serialize(DataOutputStream dataOut, boolean useMap) throws Exception {//TODO Include face value
		System.out.println("GENERIC SERIALIZE");
		System.out.println("type " + type);
		System.exit(45);
		dataOut.writeInt(ftype);
		dataOut.write(useMap ? PluginMaster.rentmap[type] : type);
		dataOut.writeInt(x);
		dataOut.writeInt(y);
		dataOut.writeLong(data);
		dataOut.writeShort(health);
	}
	public void serialize(DataOutputStream strm, boolean usemap) throws Exception {
		System.out.println("ATTEMPT SPECIFIC SERIALIZATION");
		// if (type == 2) {
		// 	((EntityPlayer) this).serialize(strm, usemap);
		// 	return;
		// }
		try {
			PluginMaster.contributed[(int) type].getDeclaredMethod("serialize", new Class[]{DataOutputStream.class, boolean.class}).invoke(this, new Object[]{strm, usemap});
		} catch (NoSuchMethodException E) {
			if (Globals.debugLevel > 1) {
				// FileOutputStream fo = new FileOutputStream(new File("LOG.txt"));
				// fo.write(E.getMessage().getBytes(StandardCharsets.UTF_8));
				// fo.close();
				System.out.println(E.getMessage());
				E.printStackTrace();
				System.exit(4500);
				throw new Exception("BREAK POINT");
			}
			_backup_serialize(strm, usemap);
		}
	}
	public void serialize(DataOutputStream strm) throws Exception {
		serialize(strm, false);
	}
	public static Entity fromDataStream(DataInputStream readFrom) throws Exception {
		return null;
	}
	public static Entity deserialize(DataInputStream strm, boolean useMap) throws Exception {
		int etype = strm.read();
		System.out.println("ENT ETYPE: " + etype);
		if (useMap) {
			etype = PluginMaster.entmap[etype];
			System.out.println("ETYPE MAPPED TO: " + etype);
		}
		if (etype == 0) {
			System.out.println(Arrays.toString(strm.readNBytes(strm.available())));
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
			x = mX;
			y = mY;
			if (d == 0) {
				PluginMaster.level.addMove(PluginMaster.level.terrain.width * (y-Dy) + (x-Dx), Dx, Dy);
			}
			onMove();
			return true;
		}
		return false;
	}
	private boolean _moveBy(int Dx, int Dy, int d) throws Exception {//Only use when Server.buf is safe to write to, don't move by anything that would move the player out of the bounds of int values if not corrected
		if (d > 15) {
			// System.out.println("FAILED: RECURSION DEPTH LIMIT EXCEEDED (" + d + ")");
			return false;
		}
		if ((d > 0) && (health > -30000)) {
			health--;
		}
		if ((Dx == 0) && (Dy == 0)) {
			// System.out.println("FAILED: Dx & Dy = 0");
			return false;
		}
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
			// System.out.println("FAILED: mX & mY = x & x");
			return false;
		}
		int targetidx = PluginMaster.level.terrain.width * mY + mX;
		int cidx = PluginMaster.level.terrain.width * y + x;
		SpaceFiller target = PluginMaster.level.terrain.spaces[targetidx];
		if (target.ftype == 1) {
			Tile targetT = (Tile) target;
			if (!targetT.canCover) {
				// System.out.println("FAILED: TARGET SPACE CANNOT BE COVERED (1," + targetT.type + ")");
				return false;
			}
			SpaceFiller store = covering;
			covering = PluginMaster.level.terrain.spaces[targetidx];
			PluginMaster.level.terrain.spaces[targetidx] = this;
			PluginMaster.level.terrain.spaces[cidx] = store;
			return true;
		}
		if (target.ftype == 0) {
			Entity test = (Entity) target;
			if (test.getClass().equals(EntityItem.class)) {
				give(((EntityItem) test).item);
				if (((EntityItem) target).item.quantity == 0) {
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
			// System.out.println("FAILED: OTHER ENTITY OBSTRUCTING");
			return false;
		}
		System.out.println("FAILED: UNHANDLED CASE FOR FTYPE (FTYPE not in X where -1 < X < 2) -> (" + target.ftype + ")");
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
