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
	public byte animate() {//removal,,,,health,x,y,face
		face = (face == '\u203c') ? '\u0021' : '\u203c';
		return 1;
	}
}
