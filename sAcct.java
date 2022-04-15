package TWAuth;
class sAcct {
	byte[] ipv4;
	byte[] sName;
	byte[] secret;
	long SID;
	static Long nextSID;
	sAcct(byte[] ipv4, String sName, byte[] secret) throws Exception {
		this.ipv4 = ipv4;
		this.sName = sName.getBytes("UTF-8");
		this.secret = secret;
	}
	sAcct(byte[] ipv4, byte[] sName, byte[] secret, long SID) {
		this.ipv4 = ipv4;
		this.sName = sName;
		this.secret = secret;
		this.SID = SID;
	}
	static sAcct[] fromStream() throws Exception {
		int i = Auth.in.readInt();
		sAcct[] result = new sAcct[i];
		byte[] ip;
		byte[] sn;
		byte[] sc;
		for (int n = 0; n < i; n++) {
			ip = new byte[4];
			Auth.in.read(ip);
			sn = new byte[Auth.in.readInt()];
			Auth.in.read(sn);
			sc = new byte[Auth.in.readInt()];
			Auth.in.read(sc);
			result[n] = new sAcct(ip, sn, sc, Auth.in.readLong());
		}
		return result;
	}
	static void toStream(sAcct[] servers) throws Exception {
		Auth.out.writeInt(servers.length);
		for(int i = 0; i < servers.length; i++) {
			Auth.out.write(servers[i].ipv4);
			Auth.out.writeInt(servers[i].sName.length);
			Auth.out.write(servers[i].sName);
			Auth.out.writeInt(servers[i].secret.length);
			Auth.out.write(servers[i].secret);
			Auth.out.writeLong(servers[i].SID);
		}
	}
}
