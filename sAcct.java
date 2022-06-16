package TWAuth;
class sAcct {
	byte[] ipv4;
	byte[] sName;
	byte[] secret;
	byte[] pass;
	long SID;
	static Long nextSID;
	public boolean equals(Object against) {
		if (against == null) {
			return false;
		}
		return this.SID == ((sAcct) against).SID;
	}
	sAcct(byte[] ipv4, byte[] sName, byte[] secret, byte[] pw, long SID) {
		this.ipv4 = ipv4;
		this.sName = sName;
		this.secret = secret;
		this.SID = SID;
		pass = pw;
	}
	static sAcct[] fromStream() throws Exception {
		int i = Auth.in.readInt();
		sAcct[] result = new sAcct[i];
		byte[] ip;
		byte[] sn;
		byte[] sc;
		byte[] pw;
		for (int n = 0; n < i; n++) {
			ip = new byte[4];
			Auth.in.read(ip);
			sn = new byte[32];
			Auth.in.read(sn);
			sc = new byte[32];
			Auth.in.read(sc);
			pw = new byte[32];
			Auth.in.read(pw);
			result[n] = new sAcct(ip, sn, sc, pw, Auth.in.readLong());
		}
		return result;
	}
	static void toStream(sAcct[] servers) throws Exception {
		Auth.out.writeInt(servers.length);
		for(int i = 0; i < servers.length; i++) {
			Auth.out.write(servers[i].ipv4);
			Auth.out.write(servers[i].sName);
			Auth.out.write(servers[i].secret);
			Auth.out.write(servers[i].pass);
			Auth.out.writeLong(servers[i].SID);
		}
	}
}
