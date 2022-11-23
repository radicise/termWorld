package TWRoot.Plugins;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;

import TWRoot.termWorld.InvalidDataException;
public class Item {
	public enum Thing {
		CLOTHPATCH('\u221e'),
		WATERBOTTLE('\u00a1'),
		STONE('o');
		public char face;
		static Thing[] things = new Thing[]{CLOTHPATCH, WATERBOTTLE, STONE};//array with all of the items, their indices in the array being consistent with their ordinal values
		Thing(char face) {
			this.face = face;
		}
	}
	public Thing thing;
	public byte quantity;
	public Item(Thing item, byte quantity) {
		this.thing = item;
		this.quantity = quantity;
	}
	public static Item deserialize(DataInputStream strm) throws Exception {
		int p = strm.read();
		if (p == 0) {
			return null;//null Item instead of Item with null as the thing
		}
		if (p == -1) {
			throw new EOFException();
		}
		if (p == 128) {
			p = 0;
		}
		int itemid = strm.read();
		if (itemid >= Thing.things.length) {
			throw new InvalidDataException("Invalid item id: " + itemid);
		}
		return new Item(Thing.things[itemid], (byte) p);
	}
	public void serialize(DataOutputStream strm) throws Exception {//write 1 0x00 byte for nulls instead of using this method
		if (quantity == 0) {
			strm.write(128);
		}
		else {
			strm.write(quantity);
		}
		strm.write(thing.ordinal());
	}
}
