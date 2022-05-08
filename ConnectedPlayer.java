package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
class ConnectedPlayer implements Runnable, Comparable<ConnectedPlayer> {
	OutputStream out;
	InputStream in;
	Socket socket;
	public String username;
	public long UID;
	public int EID;
	public long SUID;
	public volatile boolean alive = true;
	public int clientVersion;
	static boolean genInit = false;
	static private byte[] superSecret = new byte[]{0x58, (byte) 0xe0, (byte) 0xd3, 0x14, 0x41, (byte) 0xd0, (byte) 0xe6, 0x6e, (byte) 0x8b, (byte) 0xa4, (byte) 0xf1, (byte) 0xd3, 0x4b, (byte) 0xc6, 0x46, 0x76, 0x10, (byte) 0xa7, 0x2f, 0x22, (byte) 0xbd, 0x04, 0x53, 0x2b, (byte) 0xf1, (byte) 0x8f, 0x0b, (byte) 0xb3, 0x35, (byte) 0xac, 0x72, (byte) 0xb0};//Arbitrary random value used for seeding of the authentication nonce generator
	static private byte[] secret = new byte[]{0x58, (byte) 0xe0, (byte) 0xd3, 0x14, 0x41, (byte) 0xd0, (byte) 0xe6, 0x6e, (byte) 0x8b, (byte) 0xa4, (byte) 0xf1, (byte) 0xd3, 0x4b, (byte) 0xc6, 0x46, 0x76, 0x10, (byte) 0xa7, 0x2f, 0x22, (byte) 0xbd, 0x04, 0x53, 0x2b, (byte) 0xf1, (byte) 0x8f, 0x0b, (byte) 0xb3, 0x35, (byte) 0xac, 0x72, (byte) 0xb0};//Arbitrary random value used for authentication
	static private SecureRandom nonceGen;
	ConnectedPlayer(Socket socket, int EID) throws Exception {
		this.socket = socket;
		this.out = socket.getOutputStream();
		this.in = socket.getInputStream();
		this.EID = EID;
		synchronized(Server.playerVal) {
			SUID = Server.playerVal;
			Server.playerVal++;
			if (Server.playerVal == 0) {
				throw new Exception("Session-unique ID overflow imminent");
			}
		}
	}
	public boolean equals(ConnectedPlayer to) {
		if (to == null) {
			return false;
		}
		return (compareTo(to) == 0);
	}
	public int compareTo(ConnectedPlayer to) {
		if (to.SUID == SUID) {
			return 0;
		}
		if (to.SUID < SUID) {
			return 1;
		}
		return -1;
	}
	public void run() {
		try {
			serve();
		}
		catch (Exception E) {
			System.out.println("An Exception has occurred: " + E);
			try {
				socket.close();
			}
			catch (Exception F) {
				System.out.println("An Exception closing a ConnectedPlayer socket has occurred: " + F);
			}
		}
	}
	void serve() throws Exception {
		DataOutputStream dOut = new DataOutputStream(out);
		DataInputStream dIn = new DataInputStream(in);
		byte[] uname = new byte[32];
		in.read(uname);
		byte[] nonce = new byte[32];
		nonceGen.nextBytes(nonce);
		out.write(nonce);
		dOut.writeLong(Server.GUSID);
		in.read(uname);
		UID = dIn.readLong();
		byte[] claim = new byte[32];
		in.read(claim);
		byte[] toh = new byte[104];
		System.arraycopy(uname, 0, toh, 0, 32);
		System.arraycopy(nonce, 0, toh, 32, 32);
		System.arraycopy(secret, 0, toh, 64, 32);
		for (int i = 0; i < 8; i++) {
			toh[103 - i] = (byte) (UID >>> (i * 8));
		}
		MessageDigest shs = MessageDigest.getInstance("SHA-256");
		nonce = shs.digest(toh);
		if (!Arrays.equals(nonce, claim)) {
			out.write(0x55);
			return;
		}
		out.write(0x63);
		dOut.writeInt(Server.version);
		clientVersion = dIn.readInt();
		if (Server.level.entities.containsKey((((long) Server.level.spawnX) << 32) | ((long) Server.level.spawnY))) {
			dOut.writeInt(37);
			out.write(Level.blankAndInterval);
			Thread.sleep(500);
			kick("Spawn blocked!");
			return;
		}
		Server.level.ent[EID] = new EntityPlayer(Server.level.spawnX, Server.level.spawnY, 0, (short) 10);
		Server.level.entities.put((((long) Server.level.ent[EID].x) << 32) | ((long) Server.level.ent[EID].y), EID);
		if (clientVersion < Server.version) {
			System.out.println("A client was kicked for being outdated: " + socket);
			socket.close();
			return;
		}
		System.out.println("A client has connected: " + socket);
		{
			byte[] initial = Server.level.toBytes();
			dOut.writeInt(initial.length);
			out.write(initial);
		}//Extra scope to allow initial to be cleaned up by the garbage collector earlier
		dOut.writeShort(Server.turnInterval);
		synchronized (Server.players) {
			Server.players.add(this);
		}
		int n;
		try {
			while (true) {
				n = in.read();
				if ((n & 128) == 128) {
					n &= 3;
					synchronized (Server.level.ent[EID]) {
						Server.level.ent[EID].data = ((Server.level.ent[EID].data & (~0xf)) ^ ((((Server.level.ent[EID].data) & (0x33 >>> (n & 2))) ^ ((n | 2) << (~(n | 0xfffffffd)))) & 0xf));
					}
				}
			}
		}
		catch (Exception E) {
			if (alive) {
				kick("Serverside Exception: " + E);
			}
		}
		return;
	}
	void kick(String message) throws Exception {
		alive = false;
		Server.players.remove(this);
		Server.level.entities.remove((((long) Server.level.ent[EID].x) << 32) | ((long) Server.level.ent[EID].y));
		Server.level.ent[EID] = null;
		Thread.sleep(500);
		try {
			out.write(3);
			byte[] mB = message.getBytes("UTF-8");
			out.write(new byte[]{(byte) (mB.length >>> 24), (byte) (mB.length >>> 16), (byte) (mB.length >>> 8), (byte) mB.length});
			out.write(mB);
		}
		catch (Exception E) {
			System.out.println("Player at " + socket + " was disconnected with reason: " + message);
			return;
		}
		System.out.println("Player at " + socket + " was kicked with reason: " + message);
	}
	static void initRandom() throws Exception {
		if (genInit) {
			throw new Exception("Reinitialization of the authentication nonce generator is now allowed");
		}
		byte[] seed = new byte[superSecret.length + 8];
		System.arraycopy(superSecret, 0, seed, 0, superSecret.length);
		long thenTime = System.currentTimeMillis();
		for (int i = 0; i < 8; i++) {
			seed[seed.length - 1 - i] = (byte) (thenTime >> (i * 8));
		}
		nonceGen = new SecureRandom(seed);
		genInit = true;
	}
}
