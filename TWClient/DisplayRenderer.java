package TWClient;

import java.io.DataInputStream;

import TWCommon.Text;

public class DisplayRenderer {
    public class SpotDisplay {
        char face;
        byte[] color;
        SpotDisplay(char face, byte[] color) {
            this.face = face;
            this.color = color;
        }
    }
    int width;
    int height;
    SpotDisplay[] spaces;
    DisplayRenderer(DataInputStream strm) throws Exception {
        width = strm.readInt();
        height = strm.readInt();
    }
    void redisplay() {
        for (int i = 0; i < height; i ++) {
            for (int j = 0; j < width; j ++) {
                SpotDisplay spot = spaces[i*height+j];
                System.out.print(Text.formatColorString("$1" + spot.face, new String[]{Text.rgbToAnsi(spot.color[0], spot.color[1], spot.color[2])}));
            }
            System.out.println();
        }
    }
}
