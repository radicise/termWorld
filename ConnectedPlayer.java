package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
class ConnectedPlayer implements Runnable, Comparable<ConnectedPlayer> {
	OutputStream out;
	InputStream in;
	Socket socket;
	public String username;
	public String UID;
	public int EID;
	public long SUID;
	public volatile boolean alive = true;
	public int clientVersion;
	ConnectedPlayer(Socket socket, int EID) throws Exception {
		this.socket = socket;
		this.out = socket.getOutputStream();
		this.in = socket.getInputStream();
		this.EID = EID;
		synchronized(Server.playerVal) {
			SUID = Server.playerVal;
			Server.playerVal++;
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
			System.out.println("An Exception has occurred: " + E.getMessage());
			try {
				socket.close();
			}
			catch (Exception F) {
				System.out.println("An Exception closing a ConnectedPlayer socket has occurred: " + F.getMessage());
			}
		}
	}
	void serve() throws Exception {
		DataOutputStream dOut = new DataOutputStream(out);
		dOut.writeInt(Server.version);
		DataInputStream dIn = new DataInputStream(in);
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
		}
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
}
