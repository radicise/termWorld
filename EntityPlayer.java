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
}
