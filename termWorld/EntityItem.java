package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
public class EntityItem extends Entity {
	static final int invSpace = 0;
	Item item;
	EntityItem(int x, int y, Item item, long data) {
		inventory = new Item[invSpace];
		type = 3;
		this.x = x;
		this.y = y;
		xO = x;
		yO = y;
		this.item = item;
		face = item.thing.face;
		this.data = data;
		color = (byte) ((data >>> 6) & 0xf);
		if ((color == 0) || (color == 7) || (color == 8) || (color == 15)) {
			color = 9;
		}
	}
	protected void animate(int EID) {
		health = 0;
		if (data < 0) {
			Server.level.entities.remove((((long) x) << 32) ^ ((long) y));
			Server.level.ent[EID] = null;
			Server.buf.put((byte) 7).putInt(x).putInt(y);
		}
	}
	void serialize(DataOutputStream strm) throws Exception {
		strm.write(type);
		strm.writeInt(x);
		strm.writeInt(y);
		item.serialize(strm);
		strm.writeLong(data);
	}
	static EntityItem fromDataStream(DataInputStream strm) throws Exception {
		int x = strm.readInt();
		int y = strm.readInt();
		System.out.println("entity item: " + x + " " + y);
		return new EntityItem(x, y, Item.deserialize(strm), strm.readLong());
	}
}
