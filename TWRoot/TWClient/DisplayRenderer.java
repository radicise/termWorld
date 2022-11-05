package TWRoot.TWClient;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;

import TWRoot.TWCommon.Text;

public class DisplayRenderer {
    public class SpotDisplay {
        char face;
        byte[] color;
        SpotDisplay(char face, byte[] color) {
            this.face = face;
            this.color = color;
        }
    }
    public BufferedOutputStream bOut = new BufferedOutputStream(System.out);
    public SpotDisplay[] spaces;
    int width;
    int height;
    int headerlines = 0;
    public DisplayRenderer(DataInputStream strm) throws Exception {
        width = strm.readInt();
        height = strm.readInt();
        spaces = new SpotDisplay[width*height];
        setHeaderLines();
        clear();
    }
    public DisplayRenderer(int width, int height) throws Exception {
        this.width = width;
        this.height = height;
        spaces = new SpotDisplay[width*height];
        setHeaderLines();
        clear();
    }
    public void clear() throws Exception {
        System.out.print("\u001b["+headerlines+"H");
    }
    public void setHeaderLines() throws Exception {
        System.out.print("\u001b[6n");
        StringBuilder sb = new StringBuilder(5);
        while (true) {
            char c = (char) System.in.read();
            if (c == 'R') {
                break;
            }
            sb.append(c);
        }
        headerlines = Integer.valueOf(sb.toString().split("\\[")[1].split(";")[0]);
    }
    public void redisplay() throws Exception {
        clear();
        for (int i = 0; i < height; i ++) {
            for (int j = 0; j < width; j ++) {
                SpotDisplay spot = spaces[i*height+j];
                bOut.write(Text.formatColorString("$1" + spot.face, new String[]{Text.rgbToAnsi(spot.color[0], spot.color[1], spot.color[2])}).getBytes());
            }
            bOut.write((byte) '\n');
        }
        bOut.flush();
    }
    public static void main(String[] args) throws Exception {
        System.out.println("TESTING");
        DisplayRenderer dr = new DisplayRenderer(10, 10);
        System.out.println("HI");
        System.in.read();
        System.in.read();
        dr.clear();
    }
}
