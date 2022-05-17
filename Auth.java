package TWAuth;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
/*import java.util.ArrayList;
import java.io.FileOutputStream;
*/public class Auth {
	static List<uAcct> users/* = new ArrayList<uAcct>()*/;
	static List<sAcct> servers/* = new ArrayList<sAcct>()*/;
	static DataInputStream in;
	static DataOutputStream out;
	static final int port = 15652;
	static private SecureRandom rand;
	static private byte[] verySecret = new byte[]{0x58, (byte) 0xe0, (byte) 0xd3, 0x14, 0x41, (byte) 0xd0, (byte) 0xe6, 0x6e, (byte) 0x8b, (byte) 0xa4, (byte) 0xf1, (byte) 0xd3, 0x4b, (byte) 0xc6, 0x46, 0x76, 0x10, (byte) 0xa7, 0x2f, 0x22, (byte) 0xbd, 0x04, 0x53, 0x2b, (byte) 0xf1, (byte) 0x8f, 0x0b, (byte) 0xb3, 0x35, (byte) 0xac, 0x72, (byte) 0xb0};//Arbitrary random value used for seeding of the nonce generator
	public static void main(String[] args) throws Exception {
		/*users.add(new uAcct("password\u2020\u2020\u2020\u2020\u2020\u2020\u2020\u2020".getBytes("UTF-16BE"), "guest\u2020\u2020\u2020\u2020\u2020\u2020\u2020\u2020\u2020\u2020\u2020".getBytes("UTF-16BE"), 5));
		servers.add(new sAcct(new byte[]{127, 0, 0, 1}, "testServer\u2020\u2020\u2020\u2020\u2020\u2020".getBytes("UTF-16BE"), new byte[]{0x58, (byte) 0xe0, (byte) 0xd3, 0x14, 0x41, (byte) 0xd0, (byte) 0xe6, 0x6e, (byte) 0x8b, (byte) 0xa4, (byte) 0xf1, (byte) 0xd3, 0x4b, (byte) 0xc6, 0x46, 0x76, 0x10, (byte) 0xa7, 0x2f, 0x22, (byte) 0xbd, 0x04, 0x53, 0x2b, (byte) 0xf1, (byte) 0x8f, 0x0b, (byte) 0xb3, 0x35, (byte) 0xac, 0x72, (byte) 0xb0}, 1L));
		out = new DataOutputStream(new FileOutputStream(new File("TWAuth")));
		out.writeLong(6);
		out.writeLong(2);
		uAcct.toStream(users.toArray(new uAcct[0]));
		sAcct.toStream(servers.toArray(new sAcct[0]));
		out.flush();
		out.close();
		System.exit(0);
		*/in = new DataInputStream(new FileInputStream(new File("TWAuth")));
		uAcct.nextUID = in.readLong();
		sAcct.nextSID = in.readLong();
		users = Arrays.asList(uAcct.fromStream());
		servers = Arrays.asList(sAcct.fromStream());
		in.close();
		byte[] seed = new byte[verySecret.length + 8];
		System.arraycopy(verySecret, 0, seed, 0, verySecret.length);
		long thenTime = System.currentTimeMillis();
		for (int i = 0; i < 8; i++) {
			seed[seed.length - 1 - i] = (byte) (thenTime >> (i * 8));
		}
		rand = new SecureRandom(seed);
		try (ServerSocket serv = new ServerSocket(port)) {
			while (true) {
				serve(serv.accept());
			}
		}
		catch (Exception E) {
			System.out.println("An Exception has occurred: " + E);
		}
	}
	static boolean authenticateClient(Socket sock) throws Exception {
		InputStream uIn = sock.getInputStream();
		DataInputStream dIn = new DataInputStream(uIn);
		long UID = dIn.readLong();
		int ind = 0;
		OutputStream uOut = sock.getOutputStream();
		uAcct cmr;
		byte[] upass = new byte[32];
		byte[] unm = new byte[32];
		synchronized (users) {
			ind = users.indexOf(new uAcct(new byte[0], new byte[0], UID));
			if (ind == -1) {
				uOut.write(0x55);
				return false;
			}
			uOut.write(0x63);
			cmr = users.get(ind);
			System.arraycopy(cmr.pass, 0, upass, 0, 32);
			System.arraycopy(cmr.uname, 0, unm, 0, 32);
		}
		byte[] nonce = new byte[32];
		rand.nextBytes(nonce);
		uOut.write(nonce);
		long serverID = dIn.readLong();
		byte[] sno = new byte[32];
		uIn.read(sno);
		byte[] uResp = new byte[32];
		uIn.read(uResp);
		byte[] toh = new byte[72];
		System.arraycopy(upass, 0, toh, 0, 32);
		System.arraycopy(nonce, 0, toh, 32, 32);
		for (int i = 0; i < 8; i++) {
			toh[71 - i] = (byte) (UID >>> (i * 8));
		}
		MessageDigest shs = MessageDigest.getInstance("SHA-256");
		for (byte b : toh) {
			System.out.print(", " + b);
		}
		nonce = shs.digest(toh);
		if (!Arrays.equals(uResp, nonce)) {
			uOut.write(0x55);
			return false;
		}
		uOut.write(0x63);
		toh = new byte[104];
		System.arraycopy(unm, 0, toh, 0, 32);
		System.arraycopy(sno, 0, toh, 32, 32);
		sAcct smr;
		synchronized (servers) {
			ind = servers.indexOf(new sAcct(new byte[0], new byte[0], new byte[0], serverID));
			if (ind == -1) {
				uOut.write(0x55);
				return true;
			}
			uOut.write(0x63);
			smr = servers.get(ind);
			System.arraycopy(smr.secret, 0, upass, 0, 32);
		}
		System.arraycopy(upass, 0, toh, 64, 32);
		for (int i = 0; i < 8; i++) {
			toh[103 - i] = (byte) (UID >>> (i * 8));
		}
		uOut.write(shs.digest(toh));//Hope that time machines don't and won't exist
		return true;
	}
	static void serve(Socket sock) {
		new Thread(new Runnable() {
        	public void run() {
        		try {
        			InputStream in = sock.getInputStream();
        			switch (in.read()) {
        				case (0x63):
        					authenticateClient(sock);
        					break;
        				default:
        					sock.close();
        					throw new Exception("Invalid request type");
        			}
        			Thread.sleep(250);
        			sock.close();
        		}
        		catch (Exception E) {
        			System.out.println("An Exception has occurred in handling the remote request: " + E);
        		}
        	}
        }).start();
	}
}