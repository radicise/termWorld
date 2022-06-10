package termWorld;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;
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
	TreeMap<Long, Integer> dispFaces;
	private int entPlace = 0;
	private int start;
	Level(FixedFrame terrain, TreeMap<Long, Integer> entities, Entity[] ent, long age, int VID, int spawnX, int spawnY) {
		this.terrain = terrain;
		this.entities = entities;
        this.age = age;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
		this.ent = ent;
		this.VID = VID;
		dispFaces = new TreeMap<Long, Integer>();
		entities.forEach((Long L, Integer I) -> {
			dispFaces.put((L >>> 32) ^ (L << 32), I);
		});
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
		int VID = (((((data[0] & 0xff) << 8) | (data[1] & 0xff) << 8) | (data[2] & 0xff)) << 8) | (data[3] & 0xff);
		int width = (((((data[4] & 0xff) << 8) | (data[5] & 0xff) << 8) | (data[6] & 0xff)) << 8) | (data[7] & 0xff);
		int height = (((((data[8] & 0xff) << 8) | (data[9] & 0xff) << 8) | (data[10] & 0xff)) << 8) | (data[11] & 0xff);
		int marker = width * height;
		Entity[] ent = new Entity[(((((data[12] & 0xff) << 8) | (data[13] & 0xff) << 8) | (data[14] & 0xff)) << 8) | (data[15] & 0xff)];
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
			switch (type) {//UIC
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
		if (dispFaces.isEmpty()) {
			moreEntities = false;
		}
		else {
			nextEntity = dispFaces.firstKey();
			lastEntity = dispFaces.lastKey();
		}
		long o = 0;
		int p;
		Text.buffered.write(Text.delimiter);
		for (int i = 0; i < terrain.height; i++) {
			o = ((long) i) << 32;
			for (int n = 0; n < terrain.width; n++) {
				if (moreEntities && (nextEntity == o)) {
					p = dispFaces.get(nextEntity);
					if (Text.escapes) {
						Text.buffered.write(Text.colors[Server.level.ent[p].color]);
						Text.buffered.write(Server.level.ent[p].face);
						Text.buffered.write(Text.colorClear);
					}
					else {
						Text.buffered.write(Server.level.ent[p].face);
					}
					nextEntity = dispFaces.higherKey(nextEntity);
					if (o == lastEntity) {
						moreEntities = false;
					}
				}
				else {
					Text.buffered.write(Text.tiles[terrain.tiles[i * terrain.width + n]]);
				}
				o += 1;
			}
			Text.buffered.write(Text.delimiter);
		}
		Text.buffered.write(Text.delimiter);
	}
	synchronized int nextSlot() throws Exception {
		start = entPlace;
		while (ent[entPlace] != null) {
			entPlace++;
			if (entPlace == ent.length) {
				entPlace = 0;
			}
			if (entPlace == start) {
				throw new Exception("Could not allocate Entity slot");
			}
		}
		ent[entPlace] = new Entity(0, 0, 0L, (short) 0);
		return entPlace;
	}
	static Level generate(int width, int height, long seed) {
		Random rand = new Random(seed);
		byte[] terrain = new byte[width * height];
		TreeMap<Long, Integer> entities = new TreeMap<Long, Integer>();
		int entSize = 1024;
		Entity[] ent = new Entity[entSize];
		int r;
		int ePlace = 0;
		for (int i = 0; i < terrain.length; i++) {
			r = rand.nextInt();
			if (r < -((Integer.MAX_VALUE / 2) + 1)) {
				terrain[i] = 1;
			}
			else {
				switch (r & 0xf) {
					case (0):
					case (1):
						terrain[i] = 3;
						break;
					case (2):
						terrain[i] = 2;
						break;
				}
				switch ((r >>> 4) & 0x3f) {
					case (0):
						ent[ePlace] = new Dog(i % width, i / width, (r >>> 10) & 0x38f, (short) 10);
						entities.put((((long) (i % width)) << 32) ^ ((long) (i / width)), ePlace);
						ePlace++;
						break;
				}
			}
		}
		return new Level(new FixedFrame(width, height, terrain), entities, ent, 0L, Server.version, 0, 0);
	}
}
