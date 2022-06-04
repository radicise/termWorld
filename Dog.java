package termWorld;
class Dog extends Entity {
	boolean healed;
	Dog(int x, int y, long data, short health) {
		face = 'd';
		type = 1;
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
	void animate(int EID) {
		if (checkDeath(EID)) {
			return;
		}
		if (healed) {
			face = 'd';
			Server.buf.put((byte) 1).putInt(x).putInt(y).putChar(face);
			healed = false;
		}
		if (health < 5) {
			moveBy((Math.random() < 0.375) ? (Math.random() < 0.5 ? 2 : -2) : 0, (Math.random() < 0.375) ? (Math.random() < 0.5 ? 2 : -2) : 0, 0);
		}
		else {
			moveBy((Math.random() < 0.375) ? (Math.random() < 0.5 ? 1 : -1) : 0, (Math.random() < 0.375) ? (Math.random() < 0.5 ? 1 : -1) : 0, 0);
		}
		if (((((Server.level.age ^ data) & 0xf) == 0) && (this.health < 10)) && (this.health > -30000)) {
			health++;
			face = 'D';
			Server.buf.put((byte) 1).putInt(x).putInt(y).putChar(face);
			healed = true;
			
		}
	}
}
