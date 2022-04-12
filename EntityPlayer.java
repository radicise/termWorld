package termWorld;
class EntityPlayer extends Entity {
	EntityPlayer(int x, int y, long data, short health) {
		face = '\u263a';
		type = 2;
		this.x = x;
		this.y = y;
		xO = x;
		yO = y;
		this.data = data;
		this.health = health;
	}
	synchronized void animate() {
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
			data &= (~0xf);
		}
		data &= (~0xf);
	}
}
