package TWRoot.Plugins.Std.TWEntities;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import TWRoot.Plugins.Entity;
import TWRoot.Plugins.Item;
// import termWorld.Server;
class Explosive extends Entity {
	static final int invSpace = 0;
	int size;
	boolean healed;
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
		dataOut.writeShort(health);
	}
	public static Explosive fromDataStream(DataInputStream readFrom) throws Exception {
		Explosive e = new Explosive(readFrom.readInt(), readFrom.readInt(), readFrom.readLong(), readFrom.readShort(), readFrom.readUnsignedByte());
		e.face = readFrom.readChar();
		return e;
	}
	public void animate(int EID) throws Exception {
		if (checkDeath(EID)) {
			Entity.level.explosion(x, y, size);// TODO implement explosions
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
