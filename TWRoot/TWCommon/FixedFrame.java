package TWRoot.TWCommon;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;

import TWRoot.Plugins.SpaceFiller;
import TWRoot.Plugins.TileEmpty;

public class FixedFrame {
	public int width;
	public int height;
	public SpaceFiller[] spaces;
	public FixedFrame(int width, int height) {
		this.width = width;
		this.height = height;
		spaces = new SpaceFiller[width*height];
	}
	public void display() throws Exception {
		for (int y = 0; y < height; y ++) {
			Text.buffered.newLine();
			for (int x = 0; x < width; x ++) {
				int i = x + y * width;
				if (spaces[i] == null) {
					spaces[i] = new TileEmpty(1, x, y);
				}
				Text.buffered.append(spaces[i].face);
			}
		}
	}
	public int xyToi(int x, int y) {
		return y * width + x;
	}
	public void serialize(DataOutputStream strm, boolean useMap) throws Exception {
		System.out.println(Arrays.toString(spaces));
		strm.writeInt(width);
		strm.writeInt(height);
		for (int i = 0; i < spaces.length; i ++) {
			spaces[i].serialize(strm, useMap);
		}
	}
	public void serialize(DataOutputStream strm) throws Exception {
		serialize(strm, false);
	}
	public static FixedFrame deserialize(DataInputStream strm, boolean useMap) throws Exception {
		// byte[] data = strm.readNBytes(strm.available());
		// System.out.println(Arrays.toString(data));
		// if (true) {
		// 	throw new Exception("STOP");
		// }
		FixedFrame frame = new FixedFrame(strm.readInt(), strm.readInt());
		for (int i = 0; i < frame.height*frame.width; i ++) {
			frame.spaces[i] = SpaceFiller.deserialize(strm, useMap);
		}
		System.out.println(Arrays.toString(frame.spaces));
		return frame;
	}
	public static FixedFrame deserialize(DataInputStream strm) throws Exception {
		return deserialize(strm, false);
	}
	/**animates all space fillers */
	public void animate() throws Exception {
		for (SpaceFiller sf : spaces) {
			sf.animate();
		}
	}
	// void displayTiles() throws Exception {
	// 	for (int i = 0; i < height; i++) {
	// 		for (int n = 0; n < width; n++) {
	// 			Text.buffered.write(Text.tiles[spaces[(i * width) + n]]);
	// 		}
	// 		Text.buffered.write(Text.delimiter);
	// 	}
	// }
	
}
