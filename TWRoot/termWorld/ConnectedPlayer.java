package TWRoot.termWorld;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import TWRoot.Plugins.EntityPlayer;
import TWRoot.Plugins.PluginMaster;
import TWRoot.Plugins.SpaceFiller;
import TWRoot.TWCommon.Globals;
import TWRoot.TWCommon.Sec;

import javax.crypto.Cipher;
class ConnectedPlayer implements Runnable, Comparable<ConnectedPlayer> {
	BufferedOutputStream out;
	InputStream in;
	Socket socket;
	private EntityPlayer playerent;
	public String username;
	public long UID;
	// public int EID;
	public long SUID;
	public volatile boolean alive = true;
	public volatile boolean joined = false;
	public int clientVersion;
	private static boolean genInit = false;
	private int h;
	static private byte[] superSecret = new byte[]{0x58, (byte) 0xe0, (byte) 0xd3, 0x14, 0x41, (byte) 0xd0, (byte) 0xe6, 0x6e, (byte) 0x8b, (byte) 0xa4, (byte) 0xf1, (byte) 0xd3, 0x4b, (byte) 0xc6, 0x46, 0x76, 0x10, (byte) 0xa7, 0x2f, 0x22, (byte) 0xbd, 0x04, 0x53, 0x2b, (byte) 0xf1, (byte) 0x8f, 0x0b, (byte) 0xb3, 0x35, (byte) 0xac, 0x72, (byte) 0xb0};//Arbitrary random value used for seeding of the authentication nonce generator
	static byte[] secret = new byte[]{0x58, (byte) 0xe0, (byte) 0xd3, 0x14, 0x41, (byte) 0xd0, (byte) 0xe6, 0x6e, (byte) 0x8b, (byte) 0xa4, (byte) 0xf1, (byte) 0xd3, 0x4b, (byte) 0xc6, 0x46, 0x76, 0x10, (byte) 0xa7, 0x2f, 0x22, (byte) 0xbd, 0x04, 0x53, 0x2b, (byte) 0xf1, (byte) 0x8f, 0x0b, (byte) 0xb3, 0x35, (byte) 0xac, 0x72, (byte) 0xb0};//Arbitrary random value used for authentication
	private static byte[] password = new byte[32];
	static private SecureRandom nonceGen;
	public static int updateSecret() throws Exception {
		byte[] nsec = new byte[32];
		nonceGen.nextBytes(nsec);
		short good = 0;
		short fail = 0;
		int authcount = Server.authsIPv4.length;
		for (int ai = 0; ai < authcount; ai ++) {
			Socket sock = new Socket(InetAddress.getByAddress(Server.authsIPv4[ai]), Server.authsPorts[ai]);
			DataInputStream dIn = new DataInputStream(sock.getInputStream());
			DataOutputStream dOut = new DataOutputStream(sock.getOutputStream());
			dOut.write(0x33);
			dOut.writeLong(Server.GUSID);
			if (dIn.read() == 0x55) {
				sock.close();
				fail ++;
				continue;
			}
			byte[] nonce0 = new byte[32];
			dIn.read(nonce0);
			MessageDigest hasher = MessageDigest.getInstance("SHA-256");
			hasher.update(password);
			hasher.update(nonce0);
			dOut.write(hasher.digest());
			if (dIn.read() == 0x55) {
				sock.close();
				fail ++;
				continue;
			}
			int keylen = dIn.read();
			byte[] pubkeybytes = new byte[keylen];
			dIn.read(pubkeybytes);
			PublicKey pubkey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubkeybytes));
			Cipher encryptor = Cipher.getInstance("RSA");
			encryptor.init(Cipher.ENCRYPT_MODE, pubkey);
			byte[] enc = encryptor.doFinal(nsec);
			dOut.writeInt(enc.length);
			dOut.write(enc);
			good ++;
		}
		secret = nsec;
		return (((int) good) << 16) | ((int) fail);
	}
	private void manLoop() throws Exception {
		DataInputStream dIn = new DataInputStream(socket.getInputStream());
		DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
		KeyPair kp = Sec.kg.genKeyPair();
		byte[] epubk = kp.getPublic().getEncoded();
		dOut.writeInt(epubk.length);
		dOut.write(epubk);
		byte[] encpass = new byte[dIn.readInt()];
		dIn.read(encpass);
		byte[] decpass = Sec.RSADecrypt(kp.getPrivate(), encpass);
		if (!Arrays.equals(password, decpass)) {
			dOut.writeInt(0x55);
			return;
		}
		dOut.writeInt(0x63);
		byte[] cmkbyt = new byte[dIn.readInt()];
		dIn.read(cmkbyt);
		PublicKey cmkpub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(cmkbyt));
		byte[] symkey = new byte[32];
		Sec.rand.nextBytes(symkey);
		byte[] esym = Sec.RSAEncrypt(cmkpub, symkey);
		dOut.writeInt(esym.length);
		dOut.write(esym);
		Sec cry = new Sec(symkey);
		while (true) {
			int opid = cry.crypt((byte) dIn.readInt());
			switch (opid) {
				case 0x00:
					socket.close();
					return;
				case 0x01:
					dIn.readByte();
					dOut.write(cry.crypt(0x55));
					break;
				case 0x02:
					int success = ConnectedPlayer.updateSecret();
					dOut.writeShort((success & (0xffff << 16)) >> 16);
					dOut.writeShort(success & 0xffff);
					break;
				case 0x04:
					Server.stop();
					break;
				case 0x10:
					String dbg = PluginMaster.level.debugRender();
					dOut.writeInt(dbg.length());
					dOut.writeChars(dbg);
					break;
				default:
					dOut.write(cry.crypt(0x55));
					break;
			}
		}
	}
	ConnectedPlayer(Socket socket) throws Exception {
		this.socket = socket;
		this.out = new BufferedOutputStream(socket.getOutputStream());
		this.in = socket.getInputStream();
		h = socket.getInputStream().read();
		if (!Server.running) {
			kick("SERVER NOT RUNNING", true);
			return;
		}
		if (h == -1) {
			throw new Exception();
		}
		if (h == 0x63) {
			// EID = Server.level.nextSlot();
		}
		if (h == 0x01) {
			return;
		}
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
			if (h == 0x63) {
				serve();
			}
			else if (h == 0x01) {
				manLoop();
			}
			else {
				throw new Exception("Not yet implemented");
			}
		}
		catch (Exception E) {
			System.out.println("An Exception has occurred: " + E);
			E.printStackTrace();
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
		byte[] authaddr;
		try {
			byte[] uname = new byte[32];
			in.read(uname);
			byte[] nonce = new byte[32];
			nonceGen.nextBytes(nonce);
			out.write(nonce);
			dOut.writeLong(Server.GUSID);
			out.flush();
			in.read(uname);
			UID = dIn.readLong();
			byte[] claim = new byte[32];
			in.read(claim);
			// System.out.println("BEFORE AUTH ADDR BUF CREATION");
			authaddr = new byte[dIn.readInt()];
			// System.out.println("BEFORE AUTH ADDR BUF FILL");
			dIn.read(authaddr);
			// System.out.println(Arrays.toString(authaddr));
			byte[] toh = new byte[104];
			System.arraycopy(uname, 0, toh, 0, 32);
			System.arraycopy(nonce, 0, toh, 32, 32);
			synchronized (secret) {
				System.arraycopy(secret, 0, toh, 64, 32);
			}
			for (int i = 0; i < 8; i++) {
				toh[103 - i] = (byte) (UID >>> (i * 8));
			}
			MessageDigest shs = MessageDigest.getInstance("SHA-256");
			nonce = shs.digest(toh);
			/*char[] chras = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
			System.out.println();
			for (byte n : toh) {
				System.out.print(chras[(n >>> 4) & 0xf]);
				System.out.print(chras[n & 0xf]);
				System.out.print(',');
			}
			System.out.println();
			for (byte n : nonce) {
				System.out.print(chras[(n >>> 4) & 0xf]);
				System.out.print(chras[n & 0xf]);
				System.out.print(',');
			}
			System.out.println();
			for (byte n : secret) {
				System.out.print(chras[(n >>> 4) & 0xf]);
				System.out.print(chras[n & 0xf]);
				System.out.print(',');
			}
			System.out.println();
			*/if (!Arrays.equals(nonce, claim)) {
				out.write(0x55);
				out.flush();
				return;
			}
			out.write(0x63);
		}
		catch (Exception E) {
			System.out.println("A connecting user failed to be authenticated due to an Exception having occurred: " + E);
			// Server.level.ent[EID] = null;
			return;
		}
		dOut.writeInt(Globals.version);
		out.flush();
		clientVersion = dIn.readInt();
		if (clientVersion < Globals.version) {
			out.write(0x55);
			dOut.writeInt(32);
			out.write("Outdated client!".getBytes(StandardCharsets.UTF_16BE));
			out.flush();
			alive = false;
			// Server.level.ent[EID] = null;
			socket.close();
			return;
		}
		byte[] playerID = Arrays.copyOf(authaddr, authaddr.length + 8);
		for (int i = 0; i < 8; i ++) {
			playerID[authaddr.length + i] = (byte) ((UID >> i) & 0xff);
		}
		int spawnX = Server.level.spawnX;
		int spawnY = Server.level.spawnY;
		boolean hasID = Server.level.playerMap.containsKey(playerID);
		if (hasID) {
			playerent = Server.level.playerMap.get(playerID);
			spawnX = playerent.x;
			spawnY = playerent.y;
		}
		int spawnloc = spawnY*Server.level.terrain.width+spawnX;
		if (!Server.level.terrain.spaces[spawnloc].canCover) {
			System.out.println(Server.level.terrain.spaces[spawnloc].canCover);
			out.write(0x55);
			dOut.writeInt(28);
			out.write("Spawn blocked!".getBytes(StandardCharsets.UTF_16BE));
			out.flush();
			alive = false;
			socket.close();
			return;
		}
		out.write(0x63);
		Server.Locker.lock();
		SpaceFiller storedspot = Server.level.terrain.spaces[spawnloc];
		Server.level.terrain.spaces[spawnloc] = hasID ? playerent : new EntityPlayer(playerID, Server.level.spawnX, Server.level.spawnY, (System.currentTimeMillis() << 6) & 0x380, (short) 10);
		if (!hasID) {
			playerent = (EntityPlayer) Server.level.terrain.spaces[spawnloc];
		}
		Server.level.terrain.spaces[spawnloc].covering = storedspot;
		if (!hasID) {
			Server.level.playerMap.put(playerID, (EntityPlayer) Server.level.terrain.spaces[spawnloc]);
		}
		System.out.println("A client has connected: " + socket);
		PluginMaster.sendIdMap(dOut);
		Server.level.serialize(dOut);//TODO Use gzip
		dOut.writeShort(Server.turnInterval);
		// dOut.writeInt(Server.level.ent[EID].x);//TODO Prevent lag due to blocking writes
		// dOut.writeInt(Server.level.ent[EID].y);//TODO Prevent lag due to blocking writes
		Server.players.add(this);
		joined = true;
		Server.Locker.unlock();
		int n;
		try {
			while (true) {
				n = in.read();
				if (n == 0x01) { // handle movement
					n = in.read();
					playerent.schedule(1, (short) n);
				} else if (n == 0x02) { // handle terrain changing
					n = in.read();
					playerent.schedule(2, (short) ((n << 8) | in.read()));
				} else if (n == 0x03) { // handle disconnection
					kick("Manual Disconnection");
				} else if (n == 0x04) { // handle command
					//TODO
				}
				// synchronized (Server.level.ent[EID]) {
				// 	if ((n & 128) == 128) {
				// 		n &= 3;
				// 		Server.level.ent[EID].data = ((Server.level.ent[EID].data & (~0xf)) ^ ((((Server.level.ent[EID].data) & (0x33 >>> (n & 2))) ^ ((n | 2) << (~(n | 0xfffffffd)))) & 0xf));
				// 	}
				// 	else {
				// 		switch (n) {
				// 			case (100):
				// 				Server.level.ent[EID].data |= 0x20;
				// 				break;
				// 			case (101):
				// 				Server.level.ent[EID].data |= 0x10;
				// 				break;
				// 			case (102):
				// 				Server.level.ent[EID].data |= 0x400;
				// 				break;
				// 		}
				// 	}
				// }
			}
		}
		catch (Exception E) {
			E.printStackTrace();
			if (alive && !(E instanceof java.net.SocketException)) {
				kick("Serverside Exception: " + E);
			} else {
				alive = false;
				if (joined) {
					remSelf();
				}
			}
		}
		return;
	}
	void remSelf() throws Exception {
		Server.level.addDestruction(playerent.x, playerent.y);
		Server.level.terrain.spaces[playerent.x+Server.level.terrain.width*playerent.y].destroy();
		synchronized(Server.players) {
			Server.players.remove(this);
		}
		Thread.sleep(500);
	}
	void kick(String message) throws Exception {
		kick(message, false);
	}
	void kick(String message, boolean suppress) throws Exception {
		if (!alive) {
			return;
		}
		alive = false;
		if (!suppress && joined) {
			remSelf();
		}
		try {
			out.write(3);
			byte[] mB = message.getBytes("UTF-8");
			out.write(new byte[]{(byte) (mB.length >>> 24), (byte) (mB.length >>> 16), (byte) (mB.length >>> 8), (byte) mB.length});
			out.write(mB);
			out.flush();
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
		Sec.init();
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
