package TWRoot.Util;

public class Runner {
    /**
     * EXIT CODE TABLE (CODE = c):
     * 0   -   no error
     * 1   -   command not exist
     * 2   -   malformed command
     * 3   -   subprocess error
     * 4   -   command failure
     */

    // constants
    static final String red = "\u001b[38;5;9m";
    static final String blu = "\u001b[38;5;31m";
    static final String def = "\u001b[0m";

    static Runtime runtime = Runtime.getRuntime();

    // process
    static Process proc;
    static Thread helper;
    static boolean running = false;

    static String cmd[];
    
    
    static String readLine() throws Exception {
        StringBuilder sb = new StringBuilder();
        int b = System.in.read();
        while ((char) b != '\n') {
            sb.append((char) b);
            b = System.in.read();
        }
        return sb.toString();
    }
    @SuppressWarnings("all")
    static void stopProc() {
        running = false;
        if (proc != null) {
            proc.destroy();
        }
        if (helper != null) {
            helper.stop();
        }
    }
    static void startProc() throws Exception {
        stopProc();
        running = true;
        proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        helper = new Thread(new ServiceHelper(proc.getInputStream()));
        helper.start();
    }
    public static void main(String[] args) throws Exception {
        cmd = args;
        System.out.println(cmd);
        System.out.println("TERMWORLD RUNNER");
        while (true) {
            String s = readLine();
            if (s.equals("quit")) {
                System.out.println(blu+"quitting"+def);
                break;
            }
            if (s.equals("start")) {
                if (running) {
                    System.out.println(red+"already running"+def);
                    continue;
                }
                startProc();
                System.out.println(blu+"started"+def);
            } else if (s.equals("stop")) {
                if (!running) {
                    System.out.println(red+"already stopped"+def);
                    continue;
                }
                stopProc();
                System.out.println(blu+"stopped"+def);
            } else if (s.equals("rs")) {
                if (!running) {
                    System.out.println(red+"nothing to restart (did you mean \"start\"?)"+def);
                    continue;
                }
                stopProc();
                startProc();
                System.out.println(blu+"restarted"+def);
            } else if (s.equals("rc")) {
                stopProc();
                if (new ProcessBuilder("javac", cmd[1]+".java").inheritIO().start().waitFor() == 0) {
                    startProc();
                    System.out.println(blu+"recompiled"+def);
                } else {
                    System.out.println(red+"failed to recompile"+def);
                }
            }
        }
        stopProc();
    }
}
