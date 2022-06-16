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
		int p;
		if ((p = strm.read()) == 0) {
			return null;//null Item instead of Item with null as the thing
		}
		if (p == -1) {
			throw new EOFException();
		}
		if (p == 128) {
			p = 0;
		}
		return new Item(Thing.things[strm.read()], (byte) p);
	}
	void serialize(DataOutputStream strm) throws Exception {//write 1 0x00 byte for nulls instead of using this method
		if (quantity == 0) {
			strm.write(128);
		}
		else {
			strm.write(quantity);
		}
		strm.write(thing.ordinal());
	}
}
