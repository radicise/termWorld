package termWorld;
class Entity {
	int x;
	int y;
	short health;
	long data;
	byte type;
	char face;
	Entity() {
	}
	Entity(int x, int y, long data, short health) {
		type = 0;
		face = '\u203c';
		this.x = x;
		this.y = y;
		this.data = data;
		this.health = health;
	}
	synchronized byte[] animate() {//removal,,,,health,tp,,face
		face = (face == '\u203c') ? '\u0021' : '\u203c';
		return new byte[]{1, (byte) (x >>> 24), (byte) (x >>> 16), (byte) (x >>> 8), (byte) x, (byte) (y >>> 24), (byte) (y >>> 16), (byte) (y >>> 8), (byte) y, (byte) (face >>> 8), (byte) face};
	}
	
}
