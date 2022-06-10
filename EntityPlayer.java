package termWorld;
class EntityPlayer extends Entity {
	int p;
	EntityPlayer(int x, int y, long data, short health) {
		face = '\u263a';
		type = 2;
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
	boolean checkDeath(int EID) {
		if (data < 0) {
			Server.level.entities.remove((((long) Server.level.ent[EID].x) << 32) | ((long) Server.level.ent[EID].y));
			Server.level.ent[EID] = null;
			Server.buf.put((byte) 7).putInt(x).putInt(y);
			return true;
		}
		return false;
	}
	synchronized void animate(int EID) {
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
