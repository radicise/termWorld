package termWorld;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.TreeMap;
class Level {
	static final int extension = 36;
	static final int bytesPerEntity = 19;
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
        this.age = age;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
		this.ent = ent;
		this.VID = VID;
	}
	synchronized byte[] toBytes() {
		ByteBuffer data = ByteBuffer.allocate(terrain.width * terrain.height + extension + (entities.size() * bytesPerEntity)).order(ByteOrder.BIG_ENDIAN);
		data.putInt(VID).putInt(terrain.width).putInt(terrain.height).putInt(ent.length).put(terrain.tiles).putLong(age).putInt(spawnX).putInt(spawnY).putInt(entities.size());
		entities.forEach((Long L, Integer I) -> {
			data.put(ent[I].type).putInt(ent[I].x).putInt(ent[I].y).putLong(ent[I].data).putShort(ent[I].health);
		});
		byte[] result = new byte[terrain.width * terrain.height + extension + (entities.size() * bytesPerEntity)];
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
		ByteBuffer readFrom = ByteBuffer.wrap(Arrays.copyOfRange(data, marker + 16, data.length)).order(ByteOrder.BIG_ENDIAN);
		long age = readFrom.getLong();
		int spawnX = readFrom.getInt();
		int spawnY = readFrom.getInt();
		TreeMap<Long, Integer> entities = new TreeMap<Long, Integer>();
		int numEntities = readFrom.getInt();
		byte type;
		for (int i = 0; i < numEntities; i++) {
			type = readFrom.get();
			switch (type) {
				case(0):
					ent[i] = new Entity(readFrom.getInt(), readFrom.getInt(), readFrom.getLong(), readFrom.getShort());
					break;
				case(1):
					ent[i] = new Dog(readFrom.getInt(), readFrom.getInt(), readFrom.getLong(), readFrom.getShort());
					break;
				case(2):
					ent[i] = new EntityPlayer(readFrom.getInt(), readFrom.getInt(), readFrom.getLong(), readFrom.getShort());
					break;
				default:
					ent[i] = new Entity(readFrom.getInt(), readFrom.getInt(), readFrom.getLong(), readFrom.getShort());
			}
			entities.put((((long) ent[i].x) << 32) | ((long) ent[i].y), i);
		}
		return new Level(new FixedFrame(width, height, tiles), entities, ent, age, VID, spawnX, spawnY);
	}
	void display() throws Exception {
		boolean moreEntities = true;
		Long nextEntity = null;
		Long lastEntity = null;
		if (entities.isEmpty()) {
			moreEntities = false;
		}
		else {
			nextEntity = entities.firstKey();
			lastEntity = entities.lastKey();
		}
		long o = 0;
		for (int i = 0; i < terrain.height; i++) {
			o = i;
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
				o += 4294967296L;
			}
			Text.buffered.write(Text.delimiter);
		}
	}
	synchronized int nextSlot() throws Exception {
		synchronized(entPlace) {
			int start = entPlace;
			while (ent[entPlace] != null) {
				entPlace++;
				if (entPlace == ent.length) {
					entPlace = 0;
				}
				if (entPlace == start) {
					System.out.println("Warning: Could not allocate Entity slot (Too many Entity objects loaded!)");
					throw new Exception("could not allocate Entity slot");
				}
			}
			ent[entPlace] = new Entity(0, 0, 0L, (short) 0);
		}
		return entPlace;
	}
}
