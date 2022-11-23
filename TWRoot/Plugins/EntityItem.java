package TWRoot.Plugins;
import java.io.DataInputStream;
import java.io.DataOutputStream;
// import termWorld.Server;
public class EntityItem extends Entity {
	static final int invSpace = 0;
	Item item;
	public EntityItem(int x, int y, Item item, long data) {
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
	public void animate(int EID) throws Exception {
		health = 0;
		if (data < 0) {
			destroy();
		}
	}
	public void serialize(DataOutputStream strm) throws Exception {
		strm.write(type);
		strm.writeInt(x);
		strm.writeInt(y);
		item.serialize(strm);
		strm.writeLong(data);
	}
	public static EntityItem fromDataStream(DataInputStream strm) throws Exception {
		int x = strm.readInt();
		int y = strm.readInt();
		System.out.println("entity item: " + x + " " + y);
		return new EntityItem(x, y, Item.deserialize(strm), strm.readLong());
	}
}
