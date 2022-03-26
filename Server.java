package termWorld;
/*import java.io.FileOutputStream;
import java.util.TreeMap;
*/import java.net.ServerSocket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
public class Server {
	public static final int version = 1;
	public static final int defaultPort = 15651;
	public static int port = defaultPort;
	public static volatile String levelname = "defaultLevel";
	public static Level level = null;
	public static short turnInterval = 2000;
	static ArrayList<ConnectedPlayer> players = new ArrayList<ConnectedPlayer>();
	public static void main(String[] arg) throws Exception {
		/*Entity[] ent = new Entity[1024];
		ent[0] = new EntityPlayer(2, 1, 0L, (short) 10);
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
				try {
					byte[] bA;
					synchronized (players) {
						for (Integer value : level.entities.values()) {
						    bA = level.ent[value].animate();
						    if (bA.length != 0) {
						    	for (ConnectedPlayer CoPl : players) {
						    		try {
						    			CoPl.out.write(bA);
						    		}
						    		catch (Exception E) {
						    			System.out.println("An Exception has occurred: " + E);//Don't do this
						    			//Remove player
						    		}
						    	}
						    }
						}
						for (ConnectedPlayer CoPl : players) {
				    		try {
				    			CoPl.out.write(2);
				    		}
				    		catch (Exception E) {
				    			System.out.println("An Exception has occurred: " + E);//Don't do this
				    			//Remove player
				    		}
				    	}
					}
				}
				catch (Exception E) {
					System.out.println("An Exception has occurred: " + E);
					System.exit(6);
				}
			}
		}, 0, turnInterval);
		while (true) {
			try {
				new Thread(new ConnectedPlayer(server.accept(), level.nextSlot())).run();
			}
			catch (Exception E) {
				System.out.println("An Exception has occurred: " + E);
				server.close();
				System.exit(3);
			}
		}
	}
}
