package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
public class Client {
	public static final byte[] IPv4Host = new byte[]{127, 0, 0, 1};
	public static int serverVersion;
	static OutputStream out;
	static InputStream in;
	static int turnInterval;
	public static volatile boolean up;
	public static volatile boolean down;
	public static volatile boolean left;
	public static volatile boolean right;
	static void movementCapture() throws Exception {
		int n = 0;
		while(true) {
			n = System.in.read();
			switch (n) {
				case (119):
				case (87):
					if ((!down) && (!up)) {
						up = true;
						System.out.print('\u2191');
						out.write(130);
					}
					break;
				case (115):
				case (83):
					if ((!up) && (!down)) {
						down = true;
						System.out.print('\u2193');
						out.write(131);
					}
					break;
				case (97):
				case (65):
					if ((!right) && (!left)) {
						left = true;
						System.out.print('\u2190');
						out.write(128);
					}
					break;
				case (100):
				case (68):
					if ((!left) && (!right)) {
						right = true;
						System.out.print('\u2192');
						out.write(129);
					}
					break;
			}
		}
	}
	public static void main(String[] arg) throws Exception {
		Socket socket = null;
		try {
			socket = new Socket(InetAddress.getByAddress(IPv4Host), Server.port);
		}
		catch (Exception E) {
			System.out.println("Could not connect to server due to an Exception having occurred: " + E);
			System.exit(7);
		}
		in = socket.getInputStream();
		DataInputStream dIn = new DataInputStream(in);
		serverVersion = dIn.readInt();
		out = socket.getOutputStream();
		DataOutputStream dOut = new DataOutputStream(out);
		dOut.write(new byte[]{Server.version >>> 24, Server.version >>> 16, Server.version >>> 8, Server.version});
		new Thread() {
			public void run() {
				try {
					movementCapture();
				}
				catch (Exception E) {
					System.out.println("An Exception has occurred: " + E);
					System.exit(4);
				}
			}
		}.start();
		byte[] levelBytes = new byte[dIn.readInt()];
		in.read(levelBytes);
		Server.level = Level.fromBytes(levelBytes);
		Server.level.display();
		Text.buffered.flush();
		byte b;
		int i;
		turnInterval = dIn.readShort();
		byte[] mB;
		long id;
		//while (turnInterval > -1111) {System.out.println(0xff & in.read());}
		while (true) {
			while ((b = ((byte) in.read())) != 2) {
				if ((b & 2) == 0) {
					id = dIn.readLong();
					i = Server.level.entities.get(id);
					if ((b & 1) == 1) {
						Server.level.ent[i].face = dIn.readChar();
					}
					if ((b & 4) == 4) {
						Server.level.entities.remove(id);
						Server.level.ent[i].x = dIn.readInt();
						Server.level.ent[i].y = dIn.readInt();
						Server.level.entities.put((((long) Server.level.ent[i].x) << 32) ^ ((long) Server.level.ent[i].y), i);
					}
				}
				else if (b == 3) {
					mB = new byte[dIn.readInt()];
					in.read(mB);
					System.out.println("Disconnected with reason: " + new String(mB, "UTF-8"));
					System.exit(8);
				}
			}
			Server.level.display();
			up = false;
			left = false;
			down = false;
			right = false;
			Text.buffered.flush();
		}
	}
}
