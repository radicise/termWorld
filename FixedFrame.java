package termWorld;
class FixedFrame {
	int width;
	int height;
	byte[] tiles;
	FixedFrame(int width, int height, byte[] tiles) {
		this.width = width;
		this.height = height;
		this.tiles = tiles;
	}
	void displayTiles() throws Exception {
		for (int i = 0; i < height; i++) {
			for (int n = 0; n < width; n++) {
				Text.buffered.write(Text.tiles[tiles[(i * width) + n]]);
			}
			Text.buffered.write(Text.delimiter);
		}
	}
	
}
