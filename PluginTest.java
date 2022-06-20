public class PluginTest extends termWorld.Entity {
	static int closenessToLaunch = 1;//increment for earlier-called chained plugins
	public static void main(String[] arg) throws Exception {
		termWorld.Server.plugs.add((new PluginTest(0)).getClass().getMethod("postgen"));
		termWorld.Server.main(arg);
	}
	PluginTest(int x) {
		type = 4;
		this.x = x;
		y = 0;
	}
	protected void animate(int EID) {
		System.out.println("This prints to System.out once per frame!");
	}
	public static termWorld.Level postgen() throws Exception {
		if (termWorld.Server.level.ent[termWorld.Server.level.ent.length - closenessToLaunch] != null) {
			throw new Exception("Not enough Entity space for plugin to load");
		}
		int h = (-1);
		while (termWorld.Server.level.entities.containsKey(((long) h) << 32)) {
			h--;
		}
		termWorld.Server.level.ent[termWorld.Server.level.ent.length - 1] = new PluginTest(h);
		termWorld.Server.level.entities.put((((long) termWorld.Server.level.ent[termWorld.Server.level.ent.length - 1].x) << 32) ^ ((long) termWorld.Server.level.ent[termWorld.Server.level.ent.length - 1].y), termWorld.Server.level.ent.length - 1);
		return termWorld.Server.level;
	}
}
