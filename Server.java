package termWorld;
/*import java.io.FileOutputStream;
import java.util.TreeMap;
*/import java.net.ServerSocket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
public class Server {
	public static final int version = 1;
	public static final int defaultPort = 15651;
	public static int port = defaultPort;
	public static volatile String levelname = "defaultLevel";
	public static Level level = null;
	public static short turnInterval = 2000;
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
			System.out.println("An Exception has occurred: " + E.getMessage());
			System.exit(1);
		}
		ServerSocket server = new ServerSocket(port);
		while (true) {
			try {
				new Thread(new ConnectedPlayer(server.accept(), level.nextSlot())).run();
			}
			catch (Exception E) {
				System.out.println("An Exception has occurred: " + E.getMessage());
				server.close();
				System.exit(3);
			}
		}
	}
}
