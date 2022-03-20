package termWorld;
import java.net.InetAddress;
import java.net.Socket;
public class Client {
	public static final byte[] IPv4Host = new byte[]{127, 0, 0, 1};
	public static void main(String[] arg) throws Exception {
		Socket socket = new Socket(InetAddress.getByAddress(IPv4Host), Server.port);
		Thread.sleep(1000);
	}
}
