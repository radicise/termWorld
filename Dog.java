package termWorld;
class Dog extends Entity {
	Dog(int x, int y, long data, short health) {
		face = 'D';
		type = 1;
		this.x = x;
		this.y = y;
		xO = x;
		yO = y;
		this.data = data;
		this.health = health;
	}
	void animate(int EID) {
		if (checkDeath(EID)) {
			return;
		}
		moveBy((Math.random() < 0.5) ? (Math.random() < 0.5 ? 1 : -1) : 0, (Math.random() < 0.5) ? (Math.random() < 0.5 ? 1 : -1) : 0, 0);
	}
}
