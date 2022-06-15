package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InvalidObjectException;
class Entity {
	int x;
	int y;
	short health;
	volatile long data;
	final byte type = 0;
	char face;
	byte color;
	int xO;
	int yO;
	Item[] inventory;
	static final int invSpace = 0;
	Entity() {
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
	void toDataStream(DataOutputStream dataOut) throws Exception {//TODO Include face value
		dataOut.write(type);
		dataOut.writeInt(x);
		dataOut.writeInt(y);
		dataOut.writeLong(data);
		dataOut.writeShort(health);
	}
	void serialize(DataOutputStream strm) throws Exception {
		switch (type) { 
			case (0):
				toDataStream(strm);
				return;
			case (1):
				((Dog) this).toDataStream(strm);
				return;
			case (2):
				((EntityPlayer) this).toDataStream(strm);
				return;
			default:
				throw new InvalidObjectException("Invalid Entity type");
		}
	}
	static Entity fromDataStream(DataInputStream readFrom) throws Exception {
		return new Entity(readFrom.readInt(), readFrom.readInt(), readFrom.readLong(), readFrom.readShort());
	}
	static Entity deserialize(DataInputStream strm) throws Exception {
		switch (strm.read()) {
			case (0):
				return Entity.fromDataStream(strm);
			case (1):
				return Dog.fromDataStream(strm);
			case (2):
				return EntityPlayer.fromDataStream(strm);
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
	void animate(int EID) {//TODO add client-side health changing as follows:    ,,,,health,teleport,[reserved],face
		if (checkDeath(EID)) {
			return;
		}
		face = (face == '\u203c') ? '\u0021' : '\u203c';
		Server.buf.put((byte) 1).putInt(x).putInt(y).putChar(face);
	}
	boolean give(Item given) {
		for (int i = 0; i < inventory.length; i++) {
			if (inventory[i] == null) {
				continue;
			}
			if (inventory[i].thing == given.thing) {
				inventory[i].quantity += given.quantity;//TODO prevent overflow
				return true;
			}
		}
		return false;
	}
	boolean moveBy(int Dx, int Dy, int d) {//Don't move by anything that would move the player out of the bounds of int values if not corrected
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
			if (Server.level.ent[n].moveBy(Dx, Dy, d + 1) ? true : (Server.level.ent[n].moveBy(-Dy, Dx, d + 1) ? true : (Server.level.ent[n].moveBy(Dy, -Dx, d + 1) ? true : Server.level.ent[n].moveBy(-(2 * Dx), -(2 * Dy), d + 1)))) {
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
}
