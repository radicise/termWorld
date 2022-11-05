package TWRoot.termWorld;
import java.io.DataInputStream;
/*import java.io.FileOutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.TreeMap;
*/
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.reflect.Method;
import TWRoot.TWCommon.Globals;
import TWRoot.TWEntities.*;
public class Server {
	static ReentrantLock Locker = new ReentrantLock();
	public static int port = Globals.defaultHostPort;
	static byte[][] authsIPv4;
	static int[] authsPorts;
	public static volatile String levelname = "defaultLevel";
	public static Level level = null;
	public static ArrayList<Method> plugs = new ArrayList<Method>();
	public static short turnInterval = 189;
	static ArrayList<ConnectedPlayer> players = new ArrayList<ConnectedPlayer>();
	static Long playerVal = 0L;
	public static ByteBuffer buf = ByteBuffer.allocate(4096).order(ByteOrder.BIG_ENDIAN);
	public static DataOutputStream bstr;
	static byte[] bufBytes = buf.array();
	static long GUSID = 1;//Server ID
	private static Timer intervallic;
	public static boolean running = true;
	public static void stop() {
		running = false;
		intervallic.cancel();
		try {
			FileOutputStream fOut = new FileOutputStream(new File("TWLevelDat"), false);
			DataOutputStream dOut = new DataOutputStream(fOut);
			level.zip(dOut);
			fOut.close();
		} catch (Exception E) {
			System.out.println("ERROR SAVING LEVEL DATA: " + E);
			E.printStackTrace();
		}
		synchronized (players) {
			for (int i = 0; i < players.size(); i ++) {
				ConnectedPlayer player = players.get(i);
				try {
					player.kick("SERVER SHUTDOWN", true);
				} catch (Exception E) {
					System.out.println(String.format("ERROR LOGGING OUT PLAYER %s: %o", player.username, E));
					E.printStackTrace();
				}
			}
		}
		System.out.println("CLEAN SHUTDOWN");
		System.exit(0);
	}
	public static void main(String[] arg) throws Exception {
		{
			String[] aut;
			String[] byt;
			String[] cshr = arg[0].split(",");
			String[] tps;
			authsPorts = new int[cshr.length];
			aut = new String[cshr.length];
			for (int i = 0; i < cshr.length; i++) {
				tps = cshr[i].split(":");
				aut[i] = tps[0];
				authsPorts[i] = Integer.parseInt(tps[1]);
			}
			authsIPv4 = new byte[cshr.length][];
			for (int i = 0; i < cshr.length; i++) {
				byt = aut[i].split("\\.");
				authsIPv4[i] = new byte[]{(byte) Integer.parseInt(byt[0]),(byte) Integer.parseInt(byt[1]),(byte) Integer.parseInt(byt[2]),(byte) Integer.parseInt(byt[3])};
			}
		}//GC
		/*Entity[] ent = new Entity[1024];
		ent[0] = new Dog(2, 1, 0L, (short) 10);
		TreeMap<Long, Integer> entities = new TreeMap<Long, Integer>();
		entities.put((((long) ent[0].x) << 32) | ((long) ent[0].y), 0);
		Level testing = new Level(new FixedFrame(5, 5, new byte[]{3, 2, 1, 2, 3, 2, 1, 0, 1, 2, 1, 0, 0, 0, 1, 2, 1, 0, 1, 2, 3, 2, 1, 2, 3}), entities, ent, 50L, 1, 2, 2);
		testing = Level.generate(8, 8, 3127);
		FileOutputStream fileOut = new FileOutputStream("defaultLevel");
		fileOut.write(testing.toBytes());
		fileOut.close();
		System.exit(0);
		/**/try {
			boolean saved = true;
			/*level = Level.fromBytes(Files.readAllBytes(FileSystems.getDefault().getPath(levelname)));/**/
			try {
				File f = new File("TWLevelDat");
				FileInputStream fis = new FileInputStream(f);
				try {
					level = Level.unzip(new DataInputStream(fis));
				} catch (Exception E) {
					System.out.println("COULD NOT UNZIP LEVEL");
					E.printStackTrace();
				}
				saved = false;
				fis.close();
			} catch (Exception E) {
				System.out.println("LEVEL NOT STORED");
			}
			if (saved) {
				level = Level.generate(40, 40, 3827L);
			}
		}
		catch (Exception E) {
			System.out.println("An Exception has occurred: " + E);
			System.exit(1);
		}
		int n = 0;
		for (Method M : plugs) {
			System.out.println("Loading plugin: " + M.toGenericString() + " in " + M.getDeclaringClass().getCanonicalName());
			level = ((Level) M.invoke(null));
			n++;
			System.out.println("Plugin loaded");
		}
		System.out.println("Plugins loaded: " + n);
		bstr = new DataOutputStream(new ByteBufferOutputStream(buf));
		ConnectedPlayer.initRandom();
		ConnectedPlayer.updateSecret();
		ServerSocket server = new ServerSocket(port);
		intervallic = new Timer();
		intervallic.schedule(new TimerTask() {
			public void run() {
				if (!running) {return;}
				if (Locker.tryLock()) {
					try {
						int n = 0;
						for (Integer value : (level.entities.values().toArray(new Integer[0]))) {
						    level.ent[value].animate(value);
						}
						ConnectedPlayer CoPl;
						synchronized (players) {
							buf.put((byte) 2);
							int pos = buf.position();
							int i = players.size();
							while (n < i) {
					    		CoPl = players.get(n);
					    		try {
					    			CoPl.out.write(bufBytes, 0, pos);//TODO Prevent lag due to blocking writes
					    			CoPl.out.flush();
					    		}
					    		catch (Exception E) {
					    			CoPl.kick("Exception in Socket communication to client: " + E);
					    			i--;
					    			continue;
					    		}
					    		n++;
					    	}
							buf.rewind();
						}
					}
					catch (Exception E) {
						System.out.println("An Exception has occurred: " + E);
						E.printStackTrace();
						System.exit(6);
					}
					level.age++;
					Locker.unlock();
				}
				else {
					// System.out.println("Animation interval lost!");
				}
			}
		}, 0, turnInterval);
		while (running) {
			try {
				(new Thread(new ConnectedPlayer(server.accept()))).start();
			}
			catch (Exception E) {
				System.out.println("An Exception has occurred: " + E);
				server.close();
				System.exit(3);
			}
		}
	}
	void updateSalt(byte[] newSalt) throws Exception {
		synchronized (ConnectedPlayer.secret) {
			System.arraycopy(newSalt, 0, ConnectedPlayer.secret, 0, 32);
		}
		for (int i = 0; i < authsPorts.length;) {
			throw new Exception("Not yet implemented");
		}
	}
}
