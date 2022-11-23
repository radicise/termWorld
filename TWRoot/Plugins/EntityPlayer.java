package TWRoot.Plugins;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;

import TWRoot.termWorld.InvalidDataException;
import TWRoot.termWorld.Server;
import TWRoot.TWCommon.FixedFrame;
import TWRoot.TWCommon.Text;
public class EntityPlayer extends Entity {
	static final int invSpace = 15;
	byte cooldown;
	// int p;
	private int nxtframe = 0;
	private short nxtfparams = 0;
	public byte[] playerID;
	public EntityPlayer(byte[] playerID, int x, int y, long data, short health) {
		this.playerID = playerID;
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
	public EntityPlayer(byte[] playerID, int x, int y, long data, short health, Item[] inv) {
		this.playerID = playerID;
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
		cooldown = (byte) ((data >>> 11) & 0xff);
	}
	public void serialize(DataOutputStream dataOut) throws Exception {//TODO Include face value
		dataOut.write(type);
		dataOut.writeInt(inventory.length);
		for (Item I : inventory) {
			if (I == null) {
				dataOut.write(0);
				continue;
			}
			I.serialize(dataOut);
		}
		dataOut.writeInt(playerID.length);
		dataOut.write(playerID);
		dataOut.writeInt(x);
		dataOut.writeInt(y);
		dataOut.writeLong((((long) cooldown) << 11) ^ (((long) color) << 6));
		dataOut.writeShort(health);
	}
	public static EntityPlayer fromDataStream(DataInputStream readFrom) throws Exception {
		// readFrom.read();
		Item[] inv = new Item[readFrom.readInt()];
		System.out.println(inv.length);
		if (inv.length > 15) {
			System.out.println(Arrays.toString(readFrom.readNBytes(50)));
			throw new InvalidDataException("BAD INVENTORY LENGTH");
		}
		for (int n = 0; n < inv.length; n++) {
			inv[n] = Item.deserialize(readFrom);
		}
		return new EntityPlayer(readFrom.readNBytes(readFrom.readInt()), readFrom.readInt(), readFrom.readInt(), readFrom.readLong(), readFrom.readShort(), inv);
	}
	public boolean checkDeath(int EID) throws Exception {
		if (data < 0 || health <= 0) {
			destroy();
			return true;
		}
		return false;
	}
	void onMove() throws Exception {
		//
	}
	public synchronized void schedule(int opcode, short params) {
		nxtframe = opcode;
		nxtfparams = params;
	}
	public synchronized void animate() throws Exception {
		if (checkDeath()) {
			return;
		}
		// no op
		if (nxtframe == 0) {
			return;
		}
		if (nxtframe == 1) {
			int mX = 1;
			int mY = 1;
			if ((nxtfparams & 0b1) != 0) {
				mX = -1;
			}
			if ((nxtfparams & 0b10) != 0) {
				mY = -1;
			}
			moveBy(mX, mY, 0);
		}
		// if (((data & 8) == 8) || ((data & 2) == 2)) {
		// 	int mX = 0;
		// 	int mY = 0;
		// 	if ((data & 8) == 8) {
		// 		mX = ((int) ((data & 4) >>> 1)) - 1;
		// 	}
		// 	if ((data & 2) == 2) {
		// 		mY = ((int) ((data & 1) << 1)) - 1;
		// 	}
		// 	moveBy(mX, mY, 0);
		// }
		if (cooldown > 0) {
			cooldown--;
		}
		final int width = PluginMaster.level.terrain.width;
		final int height = PluginMaster.level.terrain.height;
		// final int p = y * width + x;
		if (cooldown < 1) {
			if (nxtframe != 2) {
				nxtframe = 0;
				return;
			}
			FixedFrame terrain = PluginMaster.level.terrain;
			if ((nxtfparams & 0b1) != 0) { // handles destruction
				cooldown = 7;
				// int flags = 0;
				if (x > 0) {
					// flags |= 1;
					terrain.spaces[terrain.xyToi(x-1, y)].destroy();
				}
				if (x < width) {
					// flags |= 2;
					terrain.spaces[terrain.xyToi(x+1, y)].destroy();
				}
				if (y > 0) {
					// flags |= 4;
					terrain.spaces[terrain.xyToi(x, y-1)].destroy();
				}
				if (y < height) {
					// flags |= 8;
					terrain.spaces[terrain.xyToi(x, y+1)].destroy();
				}
				// // top left
				// if ((flags ^ 5) == 0) {
				// 	terrain.spaces[terrain.xyToi(x-1, y-1)].destroy();
				// }
				// // top right
				// if ((flags ^ 6) == 0) {
				// 	terrain.spaces[terrain.xyToi(x+1, y-1)].destroy();
				// }
				// // bottom left
				// if ((flags ^ 9) == 0) {
				// 	terrain.spaces[terrain.xyToi(x-1, y+1)].destroy();
				// }
				// // bottom right
				// if ((flags ^ 10) == 0) {
				// 	terrain.spaces[terrain.xyToi(x+1, y+1)].destroy();
				// }
			} else if ((nxtfparams & 0b10) != 0) {
				cooldown = 4;
				if (covering.ftype == 0) {
					covering = new TileEmpty(1, x, y);
				}
				int dif = (nxtfparams & (0b100 << 8)) != 0 ? -1 : 1;
				int id = PluginMaster.getClassId(covering.getClass(), false);
				id += dif;
				id = id % PluginMaster.contiles.length;
				if (id == 0) {
					id += dif;
				}
				covering = PluginMaster.contiles[id].getConstructor(new Class[]{int.class, int.class, int.class}).newInstance(1, x, y);
			}
		}
		nxtframe = 0;
		// if (cooldown < 1) {
		// 	if ((data & 0x10) != 0) {
		// 		cooldown = 7;
		// 		if (x > 0) {
		// 			PluginMaster.level.terrain.tiles[p - 1] = (byte) (0);
		// 			PluginMaster.buf.put((byte) 10).putInt(p - 1).put((byte) (0));
		// 		}
		// 		if (x < (PluginMaster.level.terrain.width - 1)) {
		// 			PluginMaster.level.terrain.tiles[p + 1] = (byte) (0);
		// 			PluginMaster.buf.put((byte) 10).putInt(p + 1).put((byte) (0));
		// 		}
		// 		PluginMaster.level.terrain.tiles[p] = (byte) (0);
		// 		PluginMaster.buf.put((byte) 10).putInt(p).put((byte) (0));
		// 		p -= PluginMaster.level.terrain.width;
		// 		if (p >= 0) {
		// 			PluginMaster.level.terrain.tiles[p] = (byte) (0);
		// 			PluginMaster.buf.put((byte) 10).putInt(p).put((byte) (0));
		// 		}
		// 		p += (PluginMaster.level.terrain.width * 2);
		// 		if (p < PluginMaster.level.terrain.tiles.length) {
		// 			PluginMaster.level.terrain.tiles[p] = (byte) (0);
		// 			PluginMaster.buf.put((byte) 10).putInt(p).put((byte) (0));
		// 		}
		// 	}
		// 	if ((data & 0x20) != 0) {
		// 		cooldown = 4;
		// 		PluginMaster.level.terrain.tiles[p] = (byte) ((PluginMaster.level.terrain.tiles[p] + 1) % Text.amountAccessible);
		// 		PluginMaster.buf.put((byte) 10).putInt(p).put(PluginMaster.level.terrain.tiles[p]);
		// 	}
		// 	if ((data & 0x400) != 0) {
		// 		cooldown = 4;
		// 		PluginMaster.level.terrain.tiles[p] = (byte) ((PluginMaster.level.terrain.tiles[p] + (Text.amountAccessible - 1)) % Text.amountAccessible);
		// 		PluginMaster.buf.put((byte) 10).putInt(p).put(PluginMaster.level.terrain.tiles[p]);
		// 	}
		// }
		// data &= (~0x43f);
	}
}
