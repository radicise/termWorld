package termWorld;
import java.io.BufferedWriter;
import java.io.PrintWriter;
public class Text {
	static volatile boolean escapes = true;
	static final char[][] colors = new char[][]{new char[]{'\u001b', '[', '3', '0', 'm'}, new char[]{'\u001b', '[', '3', '1', 'm'}, new char[]{'\u001b', '[', '3', '2', 'm'}, new char[]{'\u001b', '[', '3', '3', 'm'}, new char[]{'\u001b', '[', '3', '4', 'm'}, new char[]{'\u001b', '[', '3', '5', 'm'}, new char[]{'\u001b', '[', '3', '6', 'm'}, new char[]{'\u001b', '[', '3', '7', 'm'}, new char[]{'\u001b', '[', '9', '0', 'm'}, new char[]{'\u001b', '[', '9', '1', 'm'}, new char[]{'\u001b', '[', '9', '2', 'm'}, new char[]{'\u001b', '[', '9', '3', 'm'}, new char[]{'\u001b', '[', '9', '4', 'm'}, new char[]{'\u001b', '[', '9', '5', 'm'}, new char[]{'\u001b', '[', '9', '6', 'm'}, new char[]{'\u001b', '[', '9', '7', 'm'}, new char[]{}};
	static final char[] colorClear = new char[]{'\u001b', '[', '3', '9', 'm'};
	static final char[] tiles = new char[]{' ', '#', '+', '\u00b7', '\u2591', '\u2592', '\u2593', '\u2588'};
	static final int amountTiles = 8;
	static final int amountAccessible = 8;
	static final String delimiter = System.lineSeparator();
	static final BufferedWriter buffered = new BufferedWriter(new PrintWriter(System.out, false));
}
