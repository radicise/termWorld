package TWRoot.Plugins;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PluginMaster {
    /** contributed {@link Entity} list generated from all plugins */
    public static Class<? extends Entity>[] contributed;
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
    private static String[] getPaths(String path) throws IOException {
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
        ArrayList<Class<? extends Entity>> lst = new ArrayList<Class<? extends Entity>>(4);
        lst.add(Entity.class);
        lst.add(EntityItem.class);
        lst.add(EntityPlayer.class);
        lst.add(null);
        for (Class<? extends Plugin> p : commonPlugs) {
            Class<? extends Entity>[] x = (Class<? extends Entity>[]) p.getField("contributed").get(null);
            for (Class<? extends Entity> e : x) {
                lst.add(e);
            }
        }
        contributed = (Class<? extends Entity>[]) lst.toArray();
        //TODO: remove debug info once done / add proper display for loaded plugins
        System.out.println(serverPlugs.toString());
        System.out.println(commonPlugs.toString());
        System.out.println(clientPlugs.toString());
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
        init();
    }
}
