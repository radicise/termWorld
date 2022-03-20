package termWorld;
import java.io.BufferedWriter;
import java.io.PrintWriter;
public class Text {
	static final char[] tiles = new char[]{' ', '\u00b7', '+', '#', '\u2591', '\u2592', '\u2593', '\u2588', '!', '!'};
	static final String delimiter = System.lineSeparator();
	static final BufferedWriter buffered = new BufferedWriter(new PrintWriter(System.out, false));
}
