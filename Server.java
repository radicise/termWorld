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
	public static void main(String[] arg) throws Exception {
		/*Level testing = new Level(new FixedFrame(5, 5, new byte[]{3, 2, 1, 2, 3, 2, 1, 0, 1, 2, 1, 0, 0, 0, 1, 2, 1, 0, 1, 2, 3, 2, 1, 2, 3}), new TreeMap<Long, Integer>(), new Entity[1024], 50L, 1, 2, 2);
		FileOutputStream fileOut = new FileOutputStream("defaultLevel");
		fileOut.write(testing.toBytes());
		fileOut.close();
		System.exit(0);
		*/Level level = null;
		try {
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
			}
		}
	}
}
