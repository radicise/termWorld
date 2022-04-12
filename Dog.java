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
}
