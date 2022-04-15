package TWAuth;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
public class Auth {
	static List<uAcct> users;
	static List<sAcct> servers;
	static DataInputStream in;
	static DataOutputStream out;
	static final int port = 15652;
	public static void main(String[] args) throws Exception {
		in = new DataInputStream(new FileInputStream(new File("TWAuth")));
		users = Arrays.asList(uAcct.fromStream());
		servers = Arrays.asList(sAcct.fromStream());
		in.close();
		try (ServerSocket serv = new ServerSocket(port)) {
			while (true) {
				
			}
		}
		catch (Exception E) {
			System.out.println("An Exception has occurred: " + E);
		}
	}
}
