package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InvalidObjectException;
public abstract class Entity {
	public int x;
	public int y;
	public short health;
	public volatile long data;
	public byte type = 0;
	public char face;
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
	void serialize(DataOutputStream dataOut) throws Exception {//TODO Include face value
		dataOut.write(type);
		if (type == 4) {//Supports plugin Entity-subclass objects without making serialization implementation a compulsory and redundant thing for the plugin
			return;
		}
		dataOut.writeInt(x);
		dataOut.writeInt(y);
		dataOut.writeLong(data);
		dataOut.writeShort(health);
	}
	static Entity fromDataStream(DataInputStream readFrom) throws Exception {
		return null;
	}
	static Entity deserialize(DataInputStream strm) throws Exception {
		switch (strm.read()) {
			case (0):
				return Entity.fromDataStream(strm);
			case (1):
				return Dog.fromDataStream(strm);
			case (2):
				return EntityPlayer.fromDataStream(strm);
			case (3):
				return EntityItem.fromDataStream(strm);
			case (4)://reserved for plugin Entity-subclass objects
				return null;//a null return value signifies that the entity should be ignored
			case (5):
				return Explosive.fromDataStream(strm);
			case (6):
				return null;// Placeholder entity
			default:
				throw new InvalidObjectException("Invalid Entity type");
		}
	}
	boolean checkDeath(int EID) {
		if (health > 0) {
			return false;
		}
		Server.level.entities.remove((((long) Server.level.ent[EID].x) << 32) | ((long) Server.level.ent[EID].y));
		Server.level.ent[EID] = null;
		Server.buf.put((byte) 7).putInt(x).putInt(y);
		return true;
	}
	protected void animate(int EID) throws Exception {//,,,inventory,health,teleport,[reserved],face
		if (checkDeath(EID)) {
			return;
		}
		face = (face == '\u203c') ? '\u0021' : '\u203c';
		Server.buf.put((byte) 1).putInt(x).putInt(y).putChar(face);
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
					Server.buf.put((byte) 16).putInt(x).putInt(y).putInt(i);
					inventory[i].serialize(Server.bstr);
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
				Server.buf.put((byte) 16).putInt(x).putInt(y).putInt(i);
				inventory[i].serialize(Server.bstr);
				g += given.quantity;
				given.quantity = 0;
				break;
			}
		}
		return g;
	}
	boolean moveBy(int Dx, int Dy, int d) throws Exception {//Only use when Server.buf is safe to write to, don't move by anything that would move the player out of the bounds of int values if not corrected
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
		else if ((x > 0) && ((x + Dx) >= Server.level.terrain.width)) {
			mX = Server.level.terrain.width - 1;
		}
		else {
			mX = x + Dx;
		}
		if ((Dy < 0) && (y < (-Dy))) {
			mY = 0;
		}
		else if ((Dy > 0) && ((y + Dy) >= Server.level.terrain.height)) {
			mY = Server.level.terrain.width - 1;
		}
		else {
			mY = y + Dy;
		}
		if ((mX == x) && (mY == y)) {
			return false;
		}
		if (Server.level.terrain.tiles[(Server.level.terrain.width * mY) + mX] == 1) {
			return false;
		}
		if (Server.level.entities.containsKey((((long) mX) << 32) ^ ((long) mY))) {
			int n = Server.level.entities.get((((long) mX) << 32) ^ ((long) mY));
			if (Server.level.ent[n].type == 3) {
				give(((EntityItem) Server.level.ent[n]).item);
				if (((EntityItem) Server.level.ent[n]).item.quantity == 0) {
					Server.level.ent[n].data |= ((long) -1);
				}
			}
			if (Server.level.ent[n].moveBy(Dx, Dy, d + 1) ? true : (Server.level.ent[n].moveBy(-Dy, Dx, d + 1) ? true : (Server.level.ent[n].moveBy(Dy, -Dx, d + 1) ? true : Server.level.ent[n].moveBy(-(2 * Dx), -(2 * Dy), d + 1)))) {
				if (Server.level.entities.containsKey((((long) mX) << 32) ^ ((long) mY))) {
					return false;
				}
				int k = Server.level.entities.get((((long) x) << 32) ^ ((long) y));
				Server.level.entities.remove((((long) x) << 32) ^ ((long) y));
				Server.level.entities.put((((long) mX) << 32) ^ ((long) mY), k);
				x = mX;
				y = mY;
				Server.buf.put((byte) 4).putInt(xO).putInt(yO).putInt(x).putInt(y);
				xO = x;
				yO = y;
				return true;
			}
			return false;
		}
		int k = Server.level.entities.get((((long) x) << 32) ^ ((long) y));
		Server.level.entities.remove((((long) x) << 32) ^ ((long) y));
		Server.level.entities.put((((long) mX) << 32) ^ ((long) mY), k);
		x = mX;
		y = mY;
		Server.buf.put((byte) 4).putInt(xO).putInt(yO).putInt(x).putInt(y);
		xO = x;
		yO = y;
		return true;
	}
	void sendFace(char face) {
		Server.buf.put((byte) 1).putInt(x).putInt(y).putChar(face);
		this.face = face;
	}
}
