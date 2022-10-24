package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
class Dog extends Entity {
	static final int invSpace = 2;
	boolean healed;
	Dog(int x, int y, long data, short health) {
		type = 1;
		inventory = new Item[invSpace];
		face = 'd';
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
	Dog(int x, int y, long data, short health, Item[] inv) {
		type = 1;
		inventory = new Item[invSpace];
		System.arraycopy(inv, 0, inventory, 0, Math.min(invSpace, inv.length));
		face = 'd';
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
	void serialize(DataOutputStream dataOut) throws Exception {
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
		dataOut.writeChar(face);
	}
	static Dog fromDataStream(DataInputStream readFrom) throws Exception {
		Item[] inv = new Item[readFrom.readInt()];
		for (int n = 0; n < inv.length; n++) {
			inv[n] = Item.deserialize(readFrom);
		}
		Dog d = new Dog(readFrom.readInt(), readFrom.readInt(), readFrom.readLong(), readFrom.readShort(), inv);
		d.face = readFrom.readChar();
		return d;
	}
	protected void animate(int EID) throws Exception {
		if (checkDeath(EID)) {
			return;
		}
		if (healed) {
			face = 'd';
			Server.buf.put((byte) 1).putInt(x).putInt(y).putChar(face);
			healed = false;
		}
		if (health < 5) {
			moveBy((Math.random() < 0.375) ? (Math.random() < 0.5 ? 2 : -2) : 0, (Math.random() < 0.375) ? (Math.random() < 0.5 ? 2 : -2) : 0, 0);
		}
		else {
			moveBy((Math.random() < 0.375) ? (Math.random() < 0.5 ? 1 : -1) : 0, (Math.random() < 0.375) ? (Math.random() < 0.5 ? 1 : -1) : 0, 0);
		}
		if (((((Server.level.age ^ data) & 0xf) == 0) && (this.health < 10)) && (this.health > -30000)) {
			health++;
			face = 'D';
			sendFace(face);
			healed = true;
			
		}
	}
}
