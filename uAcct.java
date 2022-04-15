package TWAuth;
class uAcct {
	byte[] pass;
	byte[] uname;
	byte[] nick;
	long UID;
	static Long nextUID;
	uAcct(byte[] pass, String uname) throws Exception {
		this.pass = pass;
		this.uname = uname.getBytes("UTF-8");
		nick = this.uname;
		synchronized (nextUID) {
			UID = nextUID;
			nextUID++;
		}
	}
	uAcct(byte[] pass, byte[] uname, byte[] nick, long UID) {
		this.pass = pass;
		this.uname = uname;
		this.nick = nick;
		this.UID = UID;
	}
	static uAcct[] fromStream() throws Exception {
		int i = Auth.in.readInt();
		uAcct[] result = new uAcct[i];
		byte[] pw;
		byte[] un;
		byte[] nc;
		for (int n = 0; n < i; n++) {
			pw = new byte[Auth.in.readInt()];
			Auth.in.read(pw);
			un = new byte[Auth.in.readInt()];
			Auth.in.read(un);
			nc = new byte[Auth.in.readInt()];
			Auth.in.read(nc);
			result[n] = new uAcct(pw, un, nc, Auth.in.readLong());
		}
		return result;
	}
	static void toStream(uAcct[] users) throws Exception {
		Auth.out.writeInt(users.length);
		for(int i = 0; i < users.length; i++) {
			Auth.out.writeInt(users[i].pass.length);
			Auth.out.write(users[i].pass);
			Auth.out.writeInt(users[i].uname.length);
			Auth.out.write(users[i].uname);
			Auth.out.writeInt(users[i].nick.length);
			Auth.out.write(users[i].nick);
			Auth.out.writeLong(users[i].UID);
		}
	}
}
