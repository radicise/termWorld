package termWorld;
/*import java.io.FileOutputStream;
import java.util.TreeMap;
*/import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;
public class Server {
	static ReentrantLock Locker = new ReentrantLock();
	public static final int version = 1;
	public static final int defaultPort = 15651;
	public static int port = defaultPort;
	public static volatile String levelname = "defaultLevel";
	public static Level level = null;
	public static short turnInterval = 500;
	static ArrayList<ConnectedPlayer> players = new ArrayList<ConnectedPlayer>();
	static Long playerVal = new Long(0L);
	static ByteBuffer buf = ByteBuffer.allocate(4096).order(ByteOrder.BIG_ENDIAN);
	static byte[] bufBytes = buf.array();
	public static void main(String[] arg) throws Exception {
		/*Entity[] ent = new Entity[1024];
		ent[0] = new Dog(2, 1, 0L, (short) 10);
		TreeMap<Long, Integer> entities = new TreeMap<Long, Integer>();
		entities.put((((long) ent[0].x) << 32) | ((long) ent[0].y), 0);
		Level testing = new Level(new FixedFrame(5, 5, new byte[]{3, 2, 1, 2, 3, 2, 1, 0, 1, 2, 1, 0, 0, 0, 1, 2, 1, 0, 1, 2, 3, 2, 1, 2, 3}), entities, ent, 50L, 1, 2, 2);
		FileOutputStream fileOut = new FileOutputStream("defaultLevel");
		fileOut.write(testing.toBytes());
		fileOut.close();
		System.exit(0);
		*/try {
			level = Level.fromBytes(Files.readAllBytes(FileSystems.getDefault().getPath(levelname)));
		}
		catch (Exception E) {
			System.out.println("An Exception has occurred: " + E);
			System.exit(1);
		}
		Timer intervallic = new Timer();
		ServerSocket server = new ServerSocket(port);
		intervallic.schedule(new TimerTask() {
			public void run() {
				if (Locker.tryLock()) {
					try {
						int n = 0;
						ConnectedPlayer CoPl;
						synchronized (players) {
							for (Integer value : (level.entities.values().toArray(new Integer[0]))) {
							    level.ent[value].animate();
							}
							buf.put((byte) 2);
							int pos = buf.position();
							int i = players.size();
							while (n < i) {
					    		CoPl = players.get(n);
					    		try {
					    			CoPl.out.write(bufBytes, 0, pos);
					    		}
					    		catch (Exception E) {
					    			CoPl.kick("Serverside Exception: " + E);
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
						System.exit(6);
						Locker.unlock();
					}
					Locker.unlock();
				}
				else {
					System.out.println("Animation interval lost!");
				}
			}
		}, 0, turnInterval);
		while (true) {
			try {
				(new Thread(new ConnectedPlayer(server.accept(), level.nextSlot()))).start();
			}
			catch (Exception E) {
				System.out.println("An Exception has occurred: " + E);
				server.close();
				System.exit(3);
			}
		}
	}
}
