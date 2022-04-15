package termWorld;
class Entity {
	int x;
	int y;
	short health;
	long data;
	byte type;
	char face;
	int xO;
	int yO;
	Entity() {
	}
	Entity(int x, int y, long data, short health) {
		type = 0;
		face = '\u203c';
		this.x = x;
		this.y = y;
		xO = x;
		yO = y;
		this.data = data;
		this.health = health;
	}
	void animate() {//removal,,,,health,tp,[res],face
		face = (face == '\u203c') ? '\u0021' : '\u203c';
		Server.buf.put((byte) 1).putInt(x).putInt(y).putChar(face);
	}
	boolean moveBy(int Dx, int Dy, int d) {//Don't move by anything that would move the player out of the bounds of int values if not corrected
		if (d > 15) {
			return false;
		}
		int mX;
		int mY;
		if ((Dx < 0) && (x < (-Dx))) {
			mX = 0;
		}
		else if ((x > 0) && ((x + Dx) >= Server.level.terrain.width)) {
			mX = Server.level.terrain.width - 1;
		}
		else {
			mX = x + Dx;
		}
		if ((Dy < 0) && (y < (-Dy))) {
			mY = 0;
		}
		else if ((Dy > 0) && ((y + Dy) >= Server.level.terrain.height)) {
			mY = Server.level.terrain.width - 1;
		}
		else {
			mY = y + Dy;
		}
		if ((mX == x) && (mY == y)) {
			return false;
		}
		if (Server.level.terrain.tiles[(Server.level.terrain.width * mY) + mX] == 3) {
			return false;
		}
		if (Server.level.entities.containsKey((((long) mX) << 32) ^ ((long) mY))) {
			int n = Server.level.entities.get((((long) mX) << 32) ^ ((long) mY));
			if (Server.level.ent[n].moveBy(Dx, Dy, d + 1) ? true : (Server.level.ent[n].moveBy(-Dy, Dx, d + 1) ? true : (Server.level.ent[n].moveBy(Dy, -Dx, d + 1) ? true : Server.level.ent[n].moveBy(-(2 * Dx), -(2 * Dy), d + 1)))) {
				int k = Server.level.entities.get((((long) x) << 32) ^ ((long) y));
				Server.level.entities.remove((((long) x) << 32) ^ ((long) y));
				Server.level.entities.put((((long) mX) << 32) ^ ((long) mY), k);
				x = mX;
				y = mY;
				Server.buf.put((byte) 4).putInt(xO).putInt(yO).putInt(x).putInt(y);
				xO = x;
				yO = y;
				return true;
			}
			return false;
		}
		int k = Server.level.entities.get((((long) x) << 32) ^ ((long) y));
		Server.level.entities.remove((((long) x) << 32) ^ ((long) y));
		Server.level.entities.put((((long) mX) << 32) ^ ((long) mY), k);
		x = mX;
		y = mY;
		Server.buf.put((byte) 4).putInt(xO).putInt(yO).putInt(x).putInt(y);
		xO = x;
		yO = y;
		return true;
	}
}
