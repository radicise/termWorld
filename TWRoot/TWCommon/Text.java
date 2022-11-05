package TWRoot.TWCommon;
import java.io.BufferedWriter;
import java.io.PrintWriter;
public class Text {
	public static volatile boolean escapes = true;
	public static final char[][] colors = new char[][]{new char[]{'\u001b', '[', '3', '0', 'm'}, new char[]{'\u001b', '[', '3', '1', 'm'}, new char[]{'\u001b', '[', '3', '2', 'm'}, new char[]{'\u001b', '[', '3', '3', 'm'}, new char[]{'\u001b', '[', '3', '4', 'm'}, new char[]{'\u001b', '[', '3', '5', 'm'}, new char[]{'\u001b', '[', '3', '6', 'm'}, new char[]{'\u001b', '[', '3', '7', 'm'}, new char[]{'\u001b', '[', '9', '0', 'm'}, new char[]{'\u001b', '[', '9', '1', 'm'}, new char[]{'\u001b', '[', '9', '2', 'm'}, new char[]{'\u001b', '[', '9', '3', 'm'}, new char[]{'\u001b', '[', '9', '4', 'm'}, new char[]{'\u001b', '[', '9', '5', 'm'}, new char[]{'\u001b', '[', '9', '6', 'm'}, new char[]{'\u001b', '[', '9', '7', 'm'}, new char[]{}};
	public static final char[] colorClear = new char[]{'\u001b', '[', '3', '9', 'm'};
	public static final char[] tiles = new char[]{' ', '#', '+', '\u00b7', '\u2591', '\u2592', '\u2593', '\u2588'};
	public static final int amountTiles = 8;
	public static final int amountAccessible = 8;
	public static final String delimiter = System.lineSeparator();
	public static final BufferedWriter buffered = new BufferedWriter(new PrintWriter(System.out, false));
	public static void clearScreen() {
		System.out.print("\u001bc");
	}
	public static String rgbToAnsi(int r, int g, int b) {
		return "\u001b[38;2;" + Integer.valueOf(r).toString() + ';' + Integer.valueOf(g).toString() + ';' + Integer.valueOf(b).toString() + 'm';
	}
	public static String formatColorString(String fmtstr, String[] colorlst) {
		String[] parts = fmtstr.split("(?<!\\\\)\\$\\(");
		StringBuilder sb = new StringBuilder(fmtstr.length());
		sb.append(parts[0]);
		// i starts at one because the first item will always have no formatting colors ex: "$1start$0" = ["", "1start", "0"]
		for (int i = 1; i < parts.length; i ++) {
			int end = parts[i].indexOf(")");
			int colorid = Integer.valueOf(parts[i].substring(0, end));
			sb.append(colorid == 0 ? "\u001b[39m" : colorlst[colorid-1]);
			sb.append(parts[i].subSequence(end+1, parts[i].length()));
		}
		sb.append(colorClear);
		return sb.toString().replaceAll("\\\\\\$", "\\$");
	}
	public static void main(String[] args) { // test anything you need here
		clearScreen();
		System.out.println(String.format("%sred%s", rgbToAnsi(255, 0, 0), new String(colorClear)));
		System.out.println(String.format("%sgreen%s", rgbToAnsi(0, 255, 0), new String(colorClear)));
		System.out.println(String.format("%sblue%s", rgbToAnsi(0, 0, 255), new String(colorClear)));
		System.out.println(String.format("%syellow%s", rgbToAnsi(255, 255, 0), new String(colorClear)));
		System.out.println(String.format("%scyan%s", rgbToAnsi(0, 255, 255), new String(colorClear)));
		System.out.println(String.format("%sviolet%s", rgbToAnsi(255, 0, 255), new String(colorClear)));
		System.out.println(String.format("%sdark green%s", rgbToAnsi(0, 125, 0), new String(colorClear)));
		System.out.println(formatColorString("$(1)red $(2)green $(3)blue $(0)\\$(4)escaped", new String[]{"\u001b[38;5;9m","\u001b[38;2;0;255;0m","\u001b[38;2;0;0;255m"}));
	}
}
