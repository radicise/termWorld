package TWRoot.Plugins;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import TWRoot.TWCommon.LevelRefactored;

public class PluginMaster {
    /** contributed {@link SpaceFiller} list generated from all plugins */
    public static Class<? extends SpaceFiller>[] contributed;
    public static Class<? extends SpaceFiller>[] contiles;
    // used to cache id lookup results to improve performance, espescially with string lookups
    private static HashMap<String, Integer> entStrIdCache = new HashMap<>();
    private static HashMap<String, Integer> tilStrIdCache = new HashMap<>();
    // transform ids from server order to client order
    public static int[] entmap = null;
    public static int[] tilemap = null;
    // transform ids from client order to server order
    public static int[] rentmap = null;
    public static int[] rtilemap = null;
    // plugin lists
    public static ArrayList<Class<? extends Plugin>> serverPlugs = new ArrayList<>();
    public static ArrayList<Class<? extends Plugin>> commonPlugs = new ArrayList<>();
    public static ArrayList<Class<? extends Plugin>> clientPlugs = new ArrayList<>();
    // event listeners
    private static HashMap<PluginHook, HashSet<Method>> eventMap = new HashMap<>();
    // validators
    private static HashMap<PluginValidator, HashSet<Method>> valMap = new HashMap<>();
    // has been initialized
    private static boolean inited = false;
    // base path
    private static Path anchor = Path.of(System.getProperty("user.dir")+"/TWRoot/Plugins");
    // level for use by plugins
    public static LevelRefactored level;

    // data
    public static boolean[] tileSolidity;

    public static void sendIdMap(DataOutputStream strm) throws Exception {
        strm.writeInt(contributed.length);
        for (int i = 0; i < contributed.length; i ++) {
            // if (contributed[i] == null) {
            //     strm.writeInt(0);
            //     strm.writeInt(i);
            //     continue;
            // }
            String name = contributed[i].getSimpleName();
            System.out.println(name);
            byte[] n = name.getBytes(StandardCharsets.UTF_8);
            System.out.println(n.length + " " + Arrays.toString(n));
            strm.writeInt(n.length);
            strm.write(n);
            strm.writeInt(i);
        }
        // System.out.println(Arrays.toString(contiles));
        strm.writeInt(contiles.length);
        for (int i = 0; i < contiles.length; i ++) {
            byte[] n = contiles[i].getSimpleName().getBytes(StandardCharsets.UTF_8);
            strm.writeInt(n.length);
            strm.write(n);
            strm.writeInt(i);
        }
    }

    public static void loadIdMap(DataInputStream strm) throws Exception {
        entmap = new int[strm.readInt()];
        rentmap = new int[entmap.length];
        int id; // avoid allocating memory during loop by defining "id" outisde of loops and setting it within loops
        // int len;
        for (int _loop = 0; _loop < entmap.length; _loop ++) {
            // len = strm.readInt();
            // String name = len > 0 ? new String(strm.readNBytes(len)) : "null";
            // id = getClassIdByString(name, true);
            // System.out.println(name + " " + id + " " + len);
            id = getClassIdByString(new String(strm.readNBytes(strm.readInt())), true);
            int i = strm.readInt();
            entmap[i] = id;
            rentmap[id] = i;
        }
        System.out.println("PAST ENT MAP");
        tilemap = new int[strm.readInt()];
        rtilemap = new int[tilemap.length];
        for (int _loop = 0; _loop < tilemap.length; _loop ++) {
            id = getClassIdByString(new String(strm.readNBytes(strm.readInt())), false);
            int i = strm.readInt();
            tilemap[i] = id;
            tilemap[id] = i;
        }
        System.out.println(Arrays.toString(entmap));
        System.out.println(Arrays.toString(rentmap));
        System.out.println(Arrays.toString(tilemap));
        System.out.println(Arrays.toString(rtilemap));
    }

    private static int getClassIdByStringSlowSearch(String name, boolean isEntity) {
        // System.out.println(name);
        Class<? extends SpaceFiller>[] test = isEntity ? contributed : contiles;
        for (int i = 0; i < test.length; i ++) {
            // if (test[i] == null) {
            //     if (name.equals("null")) {
            //         return i;
            //     }
            //     continue;
            // }
            String n = test[i].getSimpleName();
            // System.out.println(n + " " + name + " " + n.length() + " " + name.length());
            if (n.equals(name)) {
                // System.out.println("FOUND MATCH");
                return i;
            }
        }
        System.out.println(Arrays.toString(test));
        return -1;
    }

    public static int getClassIdByString(String name, boolean isEntity) {
        // avoid if statement which would invite code repetition
        HashMap<String, Integer> cache = isEntity ? entStrIdCache : tilStrIdCache;
        Integer cached = cache.get(name); // avoid repeating get call
        if (cached == null) { // check for result not being already cached
            int res = getClassIdByStringSlowSearch(name, isEntity); // get id by iterating
            cache.put(name, res); // cache result
            return res;
        }
        return cached.intValue();
    }

    private static String[] getPaths(String path) throws IOException {
        // check directory exists
        if (!Files.exists(anchor.resolve(path))) {
            return new String[]{};
        }
        // check if manifest exists
        if (Files.exists(anchor.resolve(path+"/manifest.txt"))) {
            FileInputStream fIn = new FileInputStream(new File(anchor.resolve(path+"/manifest.txt").toString()));
            String[] x = new String(fIn.readAllBytes()).split("\n");
            fIn.close();
            return x;
        }
        // resultant
        List<String> result;
        // try to walk the filesystem tree
        try (Stream<Path> walk = Files.walk(anchor.resolve(path))) {
            result = walk.filter(Files::isRegularFile).filter((f) -> f.toString().endsWith(".java")).map((t) -> t.toString())
                    .collect(Collectors.toList());
        }
        String[] x = result.toArray(new String[result.size()]);
        // format correctly
        for (int i = 0; i < x.length; i ++) {
            x[i] = ("+"+x[i].split("TWRoot/Plugins/")[1]).split(".java")[0];
        }
        return x;
    }
    /**
     * load plugins from paths
     * @param lines String[]
     * @throws Exception
     */
    private static void loadPlugs(String[] lines) throws Exception {
        for (String line : lines) {
            // check if plugin has been disabled
            if (line.charAt(0) != '+') {continue;}
            // check for wildcard (getting all classes from directory)
            int wild = line.indexOf("*");
            if (wild > 0) {
                String[] nlines = getPaths(line.substring(1,wild));
                loadPlugs(nlines);
                continue;
            }
            // convert from filesystem path to valid java classpath
            line = line.replace('/', '.');
            // get plugin class
            @SuppressWarnings("unchecked")
            Class<Plugin> cls = (Class<Plugin>) Class.forName("TWRoot.Plugins."+line.substring(1));
            // get main method
            Method m = cls.getDeclaredMethod("main", new Class[]{String[].class});
            // invoke main method
            m.invoke(null, new Object[]{new String[]{}});
        }
    }

    // used because class comparasons are much faster that string comparasons, so they are preferred and used when possible
    private static int getClassIdSlowSearch(Class<? extends SpaceFiller> cls, boolean isEntity) {
        if (isEntity) {
            for (int i = 0; i < contributed.length; i ++) {
                if (contributed[i].equals(cls)) {
                    return i;
                }
            }
        } else {
            for (int i = 0; i < contiles.length; i ++) {
                if (contiles[i].equals(cls)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static int getClassId(Class<? extends SpaceFiller> cls, boolean isEntity) {
        HashMap<String, Integer> cache = isEntity ? entStrIdCache : tilStrIdCache;
        // avoid multiple method calls which could both allocate memory for a string
        String name = cls.getSimpleName();
        // avoid repeated calls to get
        Integer cached = cache.get(name);
        if (cached == null) { // check if the result has not been cached previously
            int res = getClassIdSlowSearch(cls, isEntity); // get the id by iterating
            cache.put(name, res); // cache result
            return res;
        }
        return cached.intValue();
    }
    /**
     * do important setup
     * @param party {@code 0} if party is {@link TWRoot.termWorld.Server}, {@code 1} if party is {@link TWRoot.TWClient.Client}
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static void init(int party) throws Exception {
        // ensures not initialized more than once
        if (inited) {return;}
        inited = true;
        for (PluginHook p : PluginHook.values()) {
            eventMap.put(p, new HashSet<>());
        }
        // gets plugin paths
        FileInputStream fIn = new FileInputStream(new File("TWRoot/Plugins/manifest.txt"));
        String[] lines;
        try {
        lines = new String(fIn.readAllBytes()).split("\n");
        } catch (IOException E) {System.out.println("COULD NOT LOAD MANIFEST");throw E;}
        fIn.close();
        // loads plugins
        loadPlugs(lines);
        // gets contributed entities
        ArrayList<Class<? extends SpaceFiller>> lst = new ArrayList<Class<? extends SpaceFiller>>(4);
        lst.add(Entity.class);
        lst.add(EntityItem.class);
        lst.add(EntityPlayer.class);
        // lst.add(null);
        for (Class<? extends Plugin> p : commonPlugs) {
            // System.out.println(p.getSimpleName());
            Class<? extends SpaceFiller>[] x = (Class<? extends SpaceFiller>[]) p.getField("contributes").get(null);
            for (Class<? extends SpaceFiller> e : x) {
                if (e != null) {
                    lst.add(e);
                }
            }
        }
        // gets contributed tiles
        ArrayList<Class<? extends SpaceFiller>> tlst = new ArrayList<>(2);
        tlst.add(Tile.class);
        tlst.add(TileEmpty.class);
        // contributed = (Class<? extends Entity>[]) lst.toArray();
        contributed = lst.toArray(new Class[lst.size()]);
        contiles = tlst.toArray(new Class[tlst.size()]);
        int uplugc = party == 0 ? serverPlugs.size() : clientPlugs.size();
        //TODO: remove debug info once done / add proper display for loaded plugins
        System.out.println(String.format("%d plugins loaded, %d %s, %d common", commonPlugs.size()+uplugc, uplugc, party == 0 ? "Server Side" : "Client Side", commonPlugs.size()));
    }
    /**
     * adds a rule to the specified {@link PluginValidator}
     * @param name {@link PluginValidator}
     * @param plug {@link Method} rule, must return a boolean
     * @return {@link HashSet#add}
     */
    public static boolean addValidator(PluginValidator name, Method plug) {
        return valMap.get(name).add(plug);
    }
    /**
     * adds a listener to the specified {@link PluginHook}
     * @param hook {@link PluginHook} event to listen to
     * @param plug {@link Method} listener
     * @return {@link HashSet#add}
     */
    public static boolean hook(PluginHook hook, Method plug) {
        return eventMap.get(hook).add(plug);
    }
    /**
     * fires the specified event
     * @param hook {@link PluginHook} event to fire
     * @return {@link HashSet}
     */
    public static HashSet<Method> fire(PluginHook hook) {
        return eventMap.get(hook);
    }
    public static void main(String[] args) throws Exception {
        init(Integer.parseInt(args[0]));
    }
}
