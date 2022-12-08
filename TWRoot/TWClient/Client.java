package TWRoot.TWClient;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import TWRoot.Plugins.Entity;
import TWRoot.Plugins.Item;
import TWRoot.Plugins.PluginMaster;
import TWRoot.TWCommon.Globals;
import TWRoot.TWCommon.LevelRefactored;
import TWRoot.TWCommon.Text;

public class Client {
	public static byte[] IPv4Host = new byte[]{127, 0, 0, 1};
	public static byte[] authIPv4Host = new byte[]{127, 0, 0, 1};
	public static final int defaultAuthPort = 15652;
	public static int authPort = defaultAuthPort;
	public static int serverVersion;
	static OutputStream out;//TODO Change to BufferedOutputStream (even in declaration) and add flush() calls when chat or other features which need buffering are added
	static InputStream in;
	static int turnInterval;
	public static volatile boolean up;
	public static volatile boolean down;
	public static volatile boolean left;
	public static volatile boolean right;
	public static volatile boolean placed;
	public static volatile boolean destroyed;
	public static volatile Short cooldown;
	private static boolean disconnect;
	static String username;
	static byte[] unB = new byte[32];
	static long UID;
	public static int EID;
	static DisplayRenderer render;
	public static LevelRefactored level;
	private static StringBuilder comBuild;
	private static boolean capturingLine = false;
	static void lineCapture() throws Exception {
		comBuild = new StringBuilder();
		capturingLine = true;
		while (true) {
			char c = (char) System.in.read();
			if (c == '\n') {
				break;
			}
			comBuild.append(c);
		}
		String com = comBuild.toString();
		if (com.matches("^(dc|exit|leave|quit|disconnect)$")) {
			disconnect = true;
		}
		capturingLine = false;
	}
	static void inputCapture() throws Exception {
		char n = '\u0000';
		while(true) {
			n = (char) System.in.read();
			switch (n) {
				case ('E'):
				case ('e'):
					lineCapture();
					break;
				case ('W'):
				case ('w'):
					if ((!down) && (!up)) {
						up = true;
						System.out.print('\u2191');
						// out.write(130);
					}
					break;
				case ('S'):
				case ('s'):
					if ((!up) && (!down)) {
						down = true;
						System.out.print('\u2193');
						// out.write(131);
					}
					break;
				case ('A'):
				case ('a'):
					if ((!right) && (!left)) {
						left = true;
						System.out.print('\u2190');
						// out.write(128);
					}
					break;
				case ('D'):
				case ('d'):
					if ((!left) && (!right)) {
						right = true;
						System.out.print('\u2192');
						// out.write(129);
					}
					break;
				case ('+'):
					if (!placed) {
						placed = true;
						System.out.print('+');
						// out.write(100);
						synchronized (cooldown) {
							if (cooldown == 0) {
								cooldown = 4;
							}
						}
					}
					break;
				case ('-'):
					if (!placed) {
						placed = true;
						System.out.print('-');
						// out.write(102);
						synchronized (cooldown) {
							if (cooldown == 0) {
								cooldown = 4;
							}
						}
					}
					break;
				case ('O'):
				case ('o'):
					if (!destroyed) {
						destroyed = true;
						System.out.print('*');
						// out.write(101);
						synchronized (cooldown) {
							if (cooldown == 0) {
								cooldown = 7;
							}
						}
					}
					break;
			}
		}
	}
	public static void main(String[] arg) throws Exception {
		PluginMaster.init(1);
		System.out.println(Globals.debugLevel);
		System.out.println("termWorld v" + Globals.versionString);
		String[] ipD = arg[3].split(":");
		int serverPort = Integer.parseInt(ipD[1]);
		ipD = ipD[0].split("\\.");
		for (int q = 0; q < 4; q++) {
			IPv4Host[q] = (byte) (Integer.parseInt(ipD[q]));
		}
		ipD = arg[4].split(":");
		authPort = Integer.parseInt(ipD[1]);
		ipD = ipD[0].split("\\.");
		for (int q = 0; q < 4; q++) {
			authIPv4Host[q] = (byte) (Integer.parseInt(ipD[q]));
		}
		ipD = new String[]{arg[3], arg[4]};
		Socket socket = null;
		try {
			socket = new Socket(InetAddress.getByAddress(IPv4Host), serverPort);
		}
		catch (Exception E) {
			System.out.println("Could not connect to server due to an Exception having occurred: " + E);
			if (Globals.debugLevel > 0) {
				E.printStackTrace();
			}
			System.exit(7);
		}
		in = socket.getInputStream();
		DataInputStream dIn = new DataInputStream(in);
		out = socket.getOutputStream();
		DataOutputStream dOut = new DataOutputStream(out);
		{
			/*arg = new String[3];
			arg[0] = "guest";
			arg[1] = "password";
			arg[2] = "5";
			*/UID = Long.parseLong(arg[2], 16);
			byte[] name = arg[0].getBytes(StandardCharsets.UTF_16BE);
			if (name.length > 32) {
				socket.close();
				throw new Exception("Username is too long");
			}
			out.write(0x63);
			Arrays.fill(unB, (byte) 32);
			System.arraycopy(name, 0, unB, 0, name.length);
			out.write(unB);
			byte[] nonce0 = new byte[32];
			in.read(nonce0);
			long sGUSID = dIn.readLong();
			Socket authSock = null;
			try {
				authSock = new Socket(InetAddress.getByAddress(authIPv4Host), authPort);
			}
			catch (Exception E) {
				System.out.println("Could not connect to authentication server due to an Exception having occurred: " + E);
				if (Globals.debugLevel > 0) {
					E.printStackTrace();
				}
				socket.close();
				System.exit(8);
			}
			byte[] remAuthAddrBytes = authSock.getInetAddress().getAddress();
			InputStream an = authSock.getInputStream();
			OutputStream aut = authSock.getOutputStream();
			DataOutputStream aOut = new DataOutputStream(aut);
			aut.write(0x63);
			aOut.writeLong(UID);
			if (an.read() != 0x63) {
				System.out.println("Authentication failure: userID does not exist");
				authSock.close();
				socket.close();
				System.exit(9);
			}
			byte[] nonce1 = new byte[32];
			an.read(nonce1);
			aOut.writeLong(sGUSID);
			aut.write(nonce0);
			byte[] pw = arg[1].getBytes(StandardCharsets.UTF_16BE);
			byte[] pwB = new byte[32];
			if (pw.length > 32) {
				authSock.close();
				socket.close();
				throw new Exception("Password is too long");
			}
			Arrays.fill(pwB, (byte) 32);
			System.arraycopy(pw, 0, pwB, 0, pw.length);
			byte[] pHpw = new byte[40];
			System.arraycopy(pwB, 0, pHpw, 0, 32);
			for (int i = 0; i < 8; i++) {
				pHpw[39 - i] = (byte) (UID >>> (i * 8));
			}
			/*char[] chras = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
			for (byte n : pHpw) {
				System.out.print(chras[(n >>> 4) & 0xf]);
				System.out.print(chras[n & 0xf]);
				System.out.print(',');
			}
			System.out.println();
			/**/Arrays.fill(pwB, (byte) 0);
			MessageDigest shs = MessageDigest.getInstance("SHA-256");
			pwB = shs.digest(pHpw);
			/*for (byte n : pwB) {
				System.out.print(chras[(n >>> 4) & 0xf]);
				System.out.print(chras[n & 0xf]);
				System.out.print(',');
			}
			System.out.println();
			/**/Arrays.fill(pw, (byte) 0);
			byte[] toh = new byte[72];
			System.arraycopy(pwB, 0, toh, 0, 32);
			System.arraycopy(nonce1, 0, toh, 32, 32);
			for (int i = 0; i < 8; i++) {
				toh[71 - i] = (byte) (UID >>> (i * 8));
			}
			aut.write(shs.digest(toh));
			if (an.read() != 0x63) {
				System.out.println("Authentication failure: Secret key was not verified");
				authSock.close();
				socket.close();
				System.exit(10);
			}
			if (an.read() != 0x63) {
				System.out.println("Authentication failure: Target server not found to be registered with authentication server");
				authSock.close();
				socket.close();
				System.exit(11);
			}
			an.read(nonce0);
			authSock.close();
			out.write(unB);
			dOut.writeLong(UID);
			out.write(nonce0);
			dOut.writeInt(remAuthAddrBytes.length);
			dOut.write(remAuthAddrBytes);
			System.out.println("AFTER SENDING AUTH ADDR");
			if ((byte) in.read() != 0x63) {
				System.out.println("Authentication failure: Target server does not approve");
				socket.close();
				System.exit(12);
			}
			Arrays.fill(pHpw, (byte) 0);
			Arrays.fill(pwB, (byte) 0);
			arg[1] = null;
		}//Extra scope for security and garbage collection purposes
		username = arg[0];
		serverVersion = dIn.readInt();
		System.out.println("Server version: " + serverVersion);
		dOut.writeInt(Globals.version);
		if (in.read() == 0x55) {
			byte[] msg = new byte[dIn.readInt()];
			in.read(msg);
			System.out.println("Disconnected with reason: \u001b[38;5;9m" + (new String(msg, StandardCharsets.UTF_16BE)).replaceAll("\u001b", "\\u001b") + "\u001b[39m");
			socket.close();
			return;
		}
		up = false;
		left = false;
		down = false;
		right = false;
		placed = false;
		destroyed = false;
		cooldown = (short) 0;
		try {
			new Thread() {
				public void run() {
					try {
						inputCapture();
					}
					catch (Exception E) {
						System.out.println("An Exception has occurred: " + E);
						if (Globals.debugLevel > 0) {
							E.printStackTrace();
						}
						System.exit(4);
					}
				}
			}.start();
			// System.out.println(Arrays.toString(dIn.readNBytes(Math.min(dIn.available(), 1000000))));
			// if (true) {
			// throw new Exception("STOP HERE");
			// }
			// System.out.println(Arrays.toString(dIn.readNBytes(dIn.available())));
			// if (true) {
			// throw new BreakPointException("AFTER LEVEL PRINT");
			// }
			// level = Level.deserialize(dIn);
			PluginMaster.loadIdMap(dIn);
			level = LevelRefactored.deserialize(dIn, true);
			System.out.println(Arrays.toString(level.terrain.spaces));
			PluginMaster.level = level;
			byte b;
			int i;
			turnInterval = dIn.readShort();
			System.out.println("Turn interval: " + turnInterval + "ms");
			byte[] mB;
			long id;
			long oID = dIn.readLong();
			boolean notFound = true;
			while (true) {
				while ((b = ((byte) in.read())) != 2) {
					if (b == 3) {
						mB = new byte[dIn.readInt()];
						in.read(mB);
						System.out.println("Disconnected with reason: " + new String(mB, "UTF-8"));
						System.exit(8);
					}
					if (b == (byte) 0x43) {
						level.update(dIn);
					}
				}
				if (disconnect) {
					out.write(0x03);
				}
				synchronized (cooldown) {
					if (cooldown > 0) {
						cooldown--;
					}
				}
				if (Text.escapes) {
					Text.buffered.write("\u001b[2J\u001b[1;1H");
					Thread.sleep(100);
				}
				Text.buffered.write('[');
				Text.buffered.write('*');
				// Text.buffered.write(level.terrain.spaces[EID].covering.face);
				Text.buffered.write(']');
				Text.buffered.write('<');
				Text.buffered.write(Short.toString(cooldown));
				Text.buffered.write('>');
				Text.buffered.write('{');
				// Entity ent = (Entity) level.terrain.spaces[EID];
				// for (int p = 0; p < (ent.inventory.length - 1); p++) {
				// 	if (ent.inventory[p] == null) {
				// 		Text.buffered.write(' ');
				// 	}
				// 	else {
				// 		Text.buffered.write(ent.inventory[p].thing.face);
				// 		Text.buffered.write('x');
				// 		Text.buffered.write(Integer.toString(ent.inventory[p].quantity));
				// 	}
				// 	Text.buffered.write(',');
				// }
				// if (ent.inventory[ent.inventory.length - 1] == null) {
				// 	Text.buffered.write(' ');
				// }
				// else {
				// 	Text.buffered.write(ent.inventory[ent.inventory.length - 1].thing.face);
				// 	Text.buffered.write('x');
				// 	Text.buffered.write(Integer.toString(ent.inventory[ent.inventory.length - 1].quantity));
				// }
				// Text.buffered.write('}');
				// Text.buffered.write('(');
				// Text.buffered.write(Integer.toString(ent.x));
				// Text.buffered.write(',');
				// Text.buffered.write(' ');
				// Text.buffered.write(Integer.toString(ent.y));
				// Text.buffered.write(')');
				level.display();
				Text.buffered.newLine();
				Text.buffered.newLine();
				if (up || down || left || right) {
					out.write(1);
					out.write(0 | (right ? 1 : 0) | (left ? 4 : 0) | (up ? 8 : 0) | (down ? 2 : 0));
				} else if (destroyed || placed) {
					out.write(2);
					if (destroyed) {
						out.write(0);
						out.write(1);
					} else {
						out.write(0);
						out.write(2);
					}
				}
				up = false;
				left = false;
				down = false;
				right = false;
				placed = false;
				destroyed = false;
				if (capturingLine) {
					Text.buffered.write(comBuild.toString());
				}
				Text.buffered.flush();
			}
		}
		catch (Exception E) {
			System.out.println("An Exception has occurred: " + E);
			if (Globals.debugLevel > 0) {
				E.printStackTrace();
			}
			System.exit(13);
		}
	}
}
