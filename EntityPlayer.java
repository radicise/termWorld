package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
class EntityPlayer extends Entity {
	static final int invSpace = 15;
	int p;
	EntityPlayer(int x, int y, long data, short health) {
		type = 2;
		inventory = new Item[invSpace];
		face = '\u263a';
		this.x = x;
		this.y = y;
		xO = x;
		yO = y;
		this.data = data;
		this.health = health;
		color = (byte) ((data >>> 6) & 0xf);
		if ((color == 0) || (color == 7) || (color == 8) || (color == 15)) {
			color = 9;
		}
	}
	EntityPlayer(int x, int y, long data, short health, Item[] inv) {
		inventory = new Item[invSpace];
		System.arraycopy(inv, 0, inventory, 0, Math.min(invSpace, inv.length));
		face = '\u263a';
		this.x = x;
		this.y = y;
		xO = x;
		yO = y;
		this.data = data;
		this.health = health;
		color = (byte) ((data >>> 6) & 0xf);
		if ((color == 0) || (color == 7) || (color == 8) || (color == 15)) {
			color = 9;
		}
	}
	void serialize(DataOutputStream dataOut) throws Exception {//TODO Include face value
		dataOut.write(type);
		dataOut.writeInt(inventory.length);
		for (Item I : inventory) {
			if (I == null) {
				dataOut.write(0);
				continue;
			}
			I.serialize(dataOut);
		}
		dataOut.writeInt(x);
		dataOut.writeInt(y);
		dataOut.writeLong(data);
		dataOut.writeShort(health);
	}
	static EntityPlayer fromDataStream(DataInputStream readFrom) throws Exception {
		Item[] inv = new Item[readFrom.readInt()];
		for (int n = 0; n < inv.length; n++) {
			inv[n] = Item.deserialize(readFrom);
		}
		return new EntityPlayer(readFrom.readInt(), readFrom.readInt(), readFrom.readLong(), readFrom.readShort(), inv);
	}
	boolean checkDeath(int EID) {
		if (data < 0) {
			Server.level.entities.remove((((long) Server.level.ent[EID].x) << 32) | ((long) Server.level.ent[EID].y));
			Server.level.ent[EID] = null;
			Server.buf.put((byte) 7).putInt(x).putInt(y);
			return true;
		}
		return false;
	}
	protected synchronized void animate(int EID) throws Exception {
		if (checkDeath(EID)) {
			return;
		}
		if (((data & 8) == 8) || ((data & 2) == 2)) {
			int mX = 0;
			int mY = 0;
			if ((data & 8) == 8) {
				mX = ((int) ((data & 4) >>> 1)) - 1;
			}
			if ((data & 2) == 2) {
				mY = ((int) ((data & 1) << 1)) - 1;
			}
			moveBy(mX, mY, 0);
		}
		if ((data & 0x10) != 0) {
			p = (y * Server.level.terrain.width) + x;
			if (x > 0) {
				Server.level.terrain.tiles[p - 1] = (byte) (0);
				Server.buf.put((byte) 10).putInt(p - 1).put((byte) (0));
			}
			if (x < (Server.level.terrain.width - 1)) {
				Server.level.terrain.tiles[p + 1] = (byte) (0);
				Server.buf.put((byte) 10).putInt(p + 1).put((byte) (0));
			}
			Server.level.terrain.tiles[p] = (byte) (0);
			Server.buf.put((byte) 10).putInt(p).put((byte) (0));
			p -= Server.level.terrain.width;
			if (p >= 0) {
				Server.level.terrain.tiles[p] = (byte) (0);
				Server.buf.put((byte) 10).putInt(p).put((byte) (0));
			}
			p += (Server.level.terrain.width * 2);
			if (p < Server.level.terrain.tiles.length) {
				Server.level.terrain.tiles[p] = (byte) (0);
				Server.buf.put((byte) 10).putInt(p).put((byte) (0));
			}
		}
		if ((data & 0x20) != 0) {
			p = (y * Server.level.terrain.width) + x;
			Server.level.terrain.tiles[p] = (byte) ((Server.level.terrain.tiles[p] + 1) % Text.amountAccessible);
			Server.buf.put((byte) 10).putInt(p).put(Server.level.terrain.tiles[p]);
		}
		if ((data & 0x400) != 0) {
			p = (y * Server.level.terrain.width) + x;
			Server.level.terrain.tiles[p] = (byte) ((Server.level.terrain.tiles[p] + (Text.amountAccessible - 1)) % Text.amountAccessible);
			Server.buf.put((byte) 10).putInt(p).put(Server.level.terrain.tiles[p]);
		}
		data &= (~0x43f);
	}
}
