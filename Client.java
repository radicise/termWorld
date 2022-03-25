package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
public class Client {
	public static final byte[] IPv4Host = new byte[]{127, 0, 0, 1};
	public static int serverVersion;
	static volatile boolean up;
	static volatile boolean down;
	static volatile boolean left;
	static volatile boolean right;
	static void movementCapture() throws Exception {
		int n = 0;
		while(true) {
			n = System.in.read();
			switch (n) {
				case (119):
				case (87):
					up = true;
					System.out.print('\u2191');
					break;
				case (115):
				case (83):
					down = true;
					System.out.print('\u2193');
					break;
				case (97):
				case (65):
					left = true;
					System.out.print('\u2190');
					break;
				case (100):
				case (68):
					right = true;
					System.out.print('\u2192');
					break;
			}
		}
	}
	static void recieve() throws Exception {
		while (true) {
			
		}
	}
	public static void main(String[] arg) throws Exception {
		Socket socket = new Socket(InetAddress.getByAddress(IPv4Host), Server.port);
		OutputStream out = socket.getOutputStream();
		InputStream in = socket.getInputStream();
		DataOutputStream dOut = new DataOutputStream(out);
		DataInputStream dIn = new DataInputStream(in);
		serverVersion = dIn.readInt();
		dOut.writeInt(Server.version);
		byte[] levelBytes = new byte[dIn.readInt()];
		in.read(levelBytes);
		Level level = Level.fromBytes(levelBytes);
		level.display();
		Text.buffered.flush();
		System.exit(0);
		new Thread(new Runnable() {
			public void run() {
				try {
					movementCapture();
				} catch (Exception E) {
					System.out.println("An Exception has occurred: " + E);
					System.exit(4);
				}
			}
		}).run();
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			public void run() {
				try {
					recieve();
				}
				catch (Exception E) {
					System.out.println("An Exception has occurred: " + E);
					System.exit(6);
				}
			}
		}, 0, dIn.readShort());
		timer.cancel();
	}
}
