package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
class ConnectedPlayer implements Runnable {
	OutputStream out;
	InputStream in;
	Socket socket;
	int EID;
	ConnectedPlayer(Socket socket, int EID) throws Exception {
		this.socket = socket;
		this.out = socket.getOutputStream();
		this.in = socket.getInputStream();
		this.EID = EID;
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
	public void serve() throws Exception {
		DataOutputStream dOut = new DataOutputStream(out);
		dOut.writeInt(Server.version);
		dOut.flush();
		DataInputStream dIn = new DataInputStream(in);
		System.out.println("A client has connected");
	}
}
