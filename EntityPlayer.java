package termWorld;
class EntityPlayer extends Entity {
	EntityPlayer(int x, int y, long data, short health) {
		face = '\u263a';
		type = 2;
		this.x = x;
		this.y = y;
		this.data = data;
		this.health = health;
	}
	synchronized byte[] animate() {
		System.out.println(data);
		if (((data & 8) == 8) || ((data & 2) == 2)) {
			if ((data & 8) == 8) {
				moveBy((int) ((data & 4) >>> 1) - 1, 0);
			}
			if ((data & 2) == 2) {
				moveBy(0, (int) ((data & 1) << 1) - 1);
			}
			data &= (~0xf);
			return new byte[]{4, (byte) (x >>> 24), (byte) (x >>> 16), (byte) (x >>> 8), (byte) x, (byte) (y >>> 24), (byte) (y >>> 16), (byte) (y >>> 8), (byte) y};
		}
		data &= (~0xf);
		return new byte[0];
	}
	void moveBy(int Dx, int Dy) {//Don't move by anything that would move the player out of the bounds of int values if not corrected
		if ((Dx < 0) && (x < (-Dx))) {
			x = 0;
		}
		else if ((x > 0) && ((x + Dx) > Server.level.terrain.width)) {
			x = Server.level.terrain.width;
		}
		else {
			x += Dx;
		}
		if ((Dy < 0) && (y < (-Dy))) {
			y = 0;
		}
		else if ((Dy > 0) && ((y + Dy) > Server.level.terrain.width)) {
			y = Server.level.terrain.width;
		}
		else {
			y += Dy;
		}
		System.out.println(x + ", " + y);
	}
}
