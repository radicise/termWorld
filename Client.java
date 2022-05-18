package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
public class Client {
	public static byte[] IPv4Host = new byte[]{127, 0, 0, 1};
	public static byte[] authIPv4Host = new byte[]{127, 0, 0, 1};
	public static int authPort = 15652;
	public static int serverVersion;
	static OutputStream out;
	static InputStream in;
	static int turnInterval;
	public static volatile boolean up;
	public static volatile boolean down;
	public static volatile boolean left;
	public static volatile boolean right;
	static String username;
	static byte[] unB = new byte[32];
	static long UID;
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
		System.out.println("termWorld v" + Server.versionString);
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
		out = socket.getOutputStream();
		DataOutputStream dOut = new DataOutputStream(out);
		{
			/*arg = new String[3];
			arg[0] = "guest";
			arg[1] = "password";
			arg[2] = "5";
			*/UID = Long.valueOf(arg[2]);
			byte[] name = arg[0].getBytes(StandardCharsets.UTF_16BE);
			if (name.length > 32) {
				socket.close();
				throw new Exception("Username is too long");
			}
			Arrays.fill(unB, (byte) 32);
			System.arraycopy(name, 0, unB, 0, name.length);
			out.write(unB);
			byte[] nonce0 = new byte[32];
			in.read(nonce0);
			Server.GUSID = dIn.readLong();
			Socket authSock = null;
			try {
				authSock = new Socket(InetAddress.getByAddress(authIPv4Host), authPort);
			}
			catch (Exception E) {
				System.out.println("Could not connect to authentication server due to an Exception having occurred: " + E);
				authSock.close();
				socket.close();
				System.exit(8);
			}
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
			aOut.writeLong(Server.GUSID);
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
			Arrays.fill(pw, (byte) 0);
			byte[] toh = new byte[72];
			System.arraycopy(pwB, 0, toh, 0, 32);
			System.arraycopy(nonce1, 0, toh, 32, 32);
			for (int i = 0; i < 8; i++) {
				toh[71 - i] = (byte) (UID >>> (i * 8));
			}
			MessageDigest shs = MessageDigest.getInstance("SHA-256");
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
			if ((byte) in.read() != 0x63) {
				System.out.println("Authentication failure: Target server does not approve");
				socket.close();
				System.exit(12);
			}
			Arrays.fill(pwB, (byte) 0);
			arg[1] = null;
		}//Extra scope for security and garbage collection purposes
		username = arg[0];
		serverVersion = dIn.readInt();
		dOut.writeInt(Server.version);
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
		try {
			while (true) {
				while ((b = ((byte) in.read())) != 2) {
					if ((b & 2) == 0) {
						id = dIn.readLong();
						i = Server.level.entities.get(id);
						if ((b & 1) == 1) {
							Server.level.ent[i].face = dIn.readChar();
							Server.level.dispFaces.replace((id >>> 32) ^ (id << 32), Server.level.ent[i].face);
						}
						if ((b & 4) == 4) {
							Server.level.entities.remove(id);
							Server.level.dispFaces.remove((id >>> 32) ^ (id << 32));
							Server.level.ent[i].x = dIn.readInt();
							Server.level.ent[i].y = dIn.readInt();
							Server.level.dispFaces.put((((long) Server.level.ent[i].y) << 32) ^ ((long) Server.level.ent[i].x), Server.level.ent[i].face);
							Server.level.entities.put((((long) Server.level.ent[i].x) << 32) ^ ((long) Server.level.ent[i].y), i);
						}
					}
					else if (b == 3) {
						mB = new byte[dIn.readInt()];
						in.read(mB);
						System.out.println("Disconnected with reason: " + new String(mB, "UTF-8"));
						System.exit(8);
					}
					else if (b == 6) {
						i = Server.level.nextSlot();
						switch (in.read()) {//UIL
							case(0):
								Server.level.ent[i] = new Entity(dIn.readInt(), dIn.readInt(), dIn.readLong(), dIn.readShort());
								break;
							case(1):
								Server.level.ent[i] = new Dog(dIn.readInt(), dIn.readInt(), dIn.readLong(), dIn.readShort());
								break;
							case(2):
								Server.level.ent[i] = new EntityPlayer(dIn.readInt(), dIn.readInt(), dIn.readLong(), dIn.readShort());
								break;
							default:
								Server.level.ent[i] = new Entity(dIn.readInt(), dIn.readInt(), dIn.readLong(), dIn.readShort());
						}
						Server.level.dispFaces.put((((long) Server.level.ent[i].y) << 32) | ((long) Server.level.ent[i].x), Server.level.ent[i].face);
						Server.level.entities.put((((long) Server.level.ent[i].x) << 32) | ((long) Server.level.ent[i].y), i);
					}
					else if (b == 7) {
						id = dIn.readLong();
						Server.level.dispFaces.remove((id << 32) ^ (id >>> 32));
						Server.level.entities.remove(id);
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
		catch (Exception E) {
			System.out.println("An Exception has occurred: " + E);
			System.exit(13);
		}
	}
}
