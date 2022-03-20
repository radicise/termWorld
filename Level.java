package termWorld;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.TreeMap;
class Level {
	static final int extension = 32;
	Entity[] ent;
	FixedFrame terrain;
	TreeMap<Long, Integer> entities;
	long age;
	int VID;
	int spawnX;
	int spawnY;
	Integer entPlace = 0;
	Level(FixedFrame terrain, TreeMap<Long, Integer> entities, Entity[] ent, long age, int VID, int spawnX, int spawnY) {
		this.terrain = terrain;
		this.entities = entities;
		this.ent = ent;
		this.VID = VID;
	}
	byte[] toBytes() {
		ByteBuffer data = ByteBuffer.allocate(terrain.width * terrain.height + extension).order(ByteOrder.LITTLE_ENDIAN);
		data.putInt(VID);
		data.putInt(terrain.width);
		data.putInt(terrain.height);
		data.putInt(ent.length);
		data.put(terrain.tiles);
		data.putLong(age);
		data.putInt(spawnX);
		data.putInt(spawnY);
		byte[] result = new byte[terrain.width * terrain.height + extension];
		data.rewind();
		data.get(result);
		return result;
	}
	static Level fromBytes(byte[] data) {
		int VID = (((((data[0] & 0xff )<< 8) | (data[1] & 0xff) << 8) | (data[2] & 0xff)) << 8) | (data[3] & 0xff);
		int width = (((((data[4] & 0xff )<< 8) | (data[5] & 0xff) << 8) | (data[6] & 0xff)) << 8) | (data[7] & 0xff);
		int height = (((((data[8] & 0xff )<< 8) | (data[9] & 0xff) << 8) | (data[10] & 0xff)) << 8) | (data[11] & 0xff);
		int marker = width * height;
		Entity[] ent = new Entity[(((((data[12] & 0xff )<< 8) | (data[13] & 0xff) << 8) | (data[14] & 0xff)) << 8) | (data[15] & 0xff)];
		byte[] tiles = Arrays.copyOfRange(data, 16, marker + 16);
		ByteBuffer readFrom = ByteBuffer.wrap(Arrays.copyOfRange(data, marker + 16, marker + extension)).order(ByteOrder.LITTLE_ENDIAN);
		long age = readFrom.getLong();
		int spawnX = readFrom.getInt();
		int spawnY = readFrom.getInt();
		TreeMap<Long, Integer> entities = new TreeMap<Long, Integer>();
		//fill ent and fill entities
		return new Level(new FixedFrame(width, height, tiles), entities, ent, age, VID, spawnX, spawnY);
	}
	void display() throws Exception {
		Long nextEntity = entities.firstKey();
		boolean moreEntities = nextEntity != null;
		Long lastEntity = entities.lastKey();
		int o = 0;
		for (int i = 0; i < terrain.height; i++) {
			for (int n = 0; n < terrain.width; n++) {
				if (moreEntities && (nextEntity == o)) {
					Text.buffered.write(ent[entities.get(nextEntity)].face);
					nextEntity = entities.higherKey(nextEntity);
					if (o == lastEntity) {
						moreEntities = false;
					}
				}
				else {
					Text.buffered.write(Text.tiles[terrain.tiles[i * terrain.width + n]]);
				}
				o++;
			}
			Text.buffered.write(Text.delimiter);
		}
	}
	int nextSlot() throws Exception {
		synchronized(entPlace) {
			int start = entPlace;
			while (ent[entPlace] != null) {
				entPlace++;
				if (entPlace == ent.length) {
					entPlace = 0;
				}
				if (entPlace == start) {
					System.out.println("Warning: Could not allocate Entity slot. (Too many Entity objects loaded!)");
					throw new Exception("could not allocate Entity slot");
				}
			}
			ent[entPlace] = new Entity();
		}
		return entPlace;
	}
}
