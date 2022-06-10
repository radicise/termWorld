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
	private static boolean genInit = false;
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
				throw new Exception("Session-unique ID overflow on next player join");
			}
		}
	}
	public boolean equals(Object to) {
		if (to == null) {
			return false;
		}
		return (compareTo((ConnectedPlayer) to) == 0);
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
		try {
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
		}
		catch (Exception E) {
			System.out.println("A connecting user failed to be authenticated due to an Exception having occurred: " + E);
			Server.level.ent[EID] = null;
			return;
		}
		dOut.writeInt(Server.version);
		clientVersion = dIn.readInt();
		if (clientVersion < Server.version) {
			out.write(0x55);
			dOut.writeInt(32);
			out.write("Outdated client!".getBytes("UTF-16BE"));
			alive = false;
			Server.level.ent[EID] = null;
			socket.close();
			return;
		}
		if (Server.level.entities.containsKey((((long) Server.level.spawnX) << 32) | ((long) Server.level.spawnY))) {
			out.write(0x55);
			dOut.writeInt(28);
			out.write("Spawn blocked!".getBytes("UTF-16BE"));
			alive = false;
			Server.level.ent[EID] = null;
			socket.close();
			return;
		}
		out.write(0x63);
		Server.Locker.lock();
		Server.level.ent[EID] = new EntityPlayer(Server.level.spawnX, Server.level.spawnY, (System.currentTimeMillis() << 6) & 0x380, (short) 10);
		Server.level.entities.put((((long) Server.level.ent[EID].x) << 32) | ((long) Server.level.ent[EID].y), EID);
		Server.buf.put((byte) 6).put((byte) 2).putInt(Server.level.ent[EID].x).putInt(Server.level.ent[EID].y).putLong(Server.level.ent[EID].data).putShort(Server.level.ent[EID].health);
		System.out.println("A client has connected: " + socket);
		{
			byte[] initial = Server.level.toBytes();
			dOut.writeInt(initial.length);//TODO Use gzip
			out.write(initial);
		}//Extra scope to allow initial to be cleaned up by the garbage collector earlier
		dOut.writeShort(Server.turnInterval);
		dOut.writeInt(Server.level.ent[EID].x);//TODO Prevent lag due to blocking writes
		dOut.writeInt(Server.level.ent[EID].y);//TODO Prevent lag due to blocking writes
		Server.players.add(this);
		Server.Locker.unlock();
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
				else {
					switch (n) {
						case (100):
							Server.level.ent[EID].data |= 0x20;
							break;
						case (101):
							Server.level.ent[EID].data |= 0x10;
							break;
						case (102):
							Server.level.ent[EID].data |= 0x400;
							break;
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
		Server.level.ent[EID].data = -1;
		synchronized(Server.players) {
			Server.players.remove(this);
		}
		Thread.sleep(500);
		try {
			out.write(3);
			byte[] mB = message.getBytes("UTF-8");
			out.write(new byte[]{(byte) (mB.length >>> 24), (byte) (mB.length >>> 16), (byte) (mB.length >>> 8), (byte) mB.length});
			out.write(mB);
		}
		catch (Exception E) {
			System.out.println("Player on " + socket + " was disconnected with reason: " + message);
			return;
		}
		System.out.println("Player on " + socket + " was kicked with reason: " + message);
	}
	static void initRandom() throws Exception {
		if (genInit) {
			throw new Exception("Reinitialization of the authentication nonce generator is not permitted");
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
