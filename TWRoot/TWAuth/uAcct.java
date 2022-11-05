package TWRoot.TWAuth;
class uAcct {
	byte[] pass;
	byte[] uname;
	long UID;
	static Long nextUID;
	public boolean equals(Object against) {
		if (against == null) {
			return false;
		}
		return this.UID == ((uAcct) against).UID;
	}
	uAcct(byte[] pass, String uname) throws Exception {
		this.pass = pass;
		this.uname = uname.getBytes("UTF-8");
		synchronized (nextUID) {
			UID = nextUID;
			nextUID++;
		}
	}
	uAcct(byte[] pass, byte[] uname, long UID) {
		this.pass = pass;
		this.uname = uname;
		this.UID = UID;
	}
	uAcct(long UID) {
		this.UID = UID;
	}
	static uAcct[] fromStream() throws Exception {
		int i = Auth.in.readInt();
		uAcct[] result = new uAcct[i];
		byte[] pw;
		byte[] un;
		for (int n = 0; n < i; n++) {
			pw = new byte[32];
			Auth.in.read(pw);
			un = new byte[32];
			Auth.in.read(un);
			result[n] = new uAcct(pw, un, Auth.in.readLong());
		}
		return result;
	}
	static void toStream(uAcct[] users) throws Exception {
		Auth.out.writeInt(users.length);
		for(int i = 0; i < users.length; i++) {
			Auth.out.write(users[i].pass);
			Auth.out.write(users[i].uname);
			Auth.out.writeLong(users[i].UID);
		}
	}
}
