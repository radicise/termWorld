package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
public class Item {
	enum Thing {
		CLOTHPATCH('\u221e'),
		WATERBOTTLE('\u00a1'),
		STONE('o');
		char face;
		static Thing[] things = new Thing[]{CLOTHPATCH, WATERBOTTLE, STONE};//array with all of the items, their indices in the array being consistent with their ordinal values
		Thing(char face) {
			this.face = face;
		}
	}
	Thing thing;
	byte quantity;
	Item(Thing item, byte quantity) {
		this.thing = item;
		this.quantity = quantity;
	}
	static Item deserialize(DataInputStream strm) throws Exception {
		byte p;
		if ((p = ((byte) strm.read())) == 0) {
			return null;//null Item instead of Item with null as the thing
		}
		if (p == -1) {
			throw new EOFException();
		}
		return new Item(Thing.things[strm.read()], p);
	}
	void serialize(DataOutputStream strm) throws Exception {//write 1 0x00 byte for nulls instead of using this method
		strm.write(quantity);
		strm.write(thing.ordinal());
	}
}
