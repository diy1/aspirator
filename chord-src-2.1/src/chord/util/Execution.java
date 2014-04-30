package chord.util;

import java.io.File;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import chord.project.Config;

/**
 * Utility for organized execution of experiments.
 *
 * @author Percy Liang (pliang@cs.berkeley.edu)
 */
public class Execution {
    public HashMap<Object,Object> options = new LinkedHashMap<Object,Object>(); // For statistics, which get dumped
    public HashMap<Object,Object> output  = new LinkedHashMap<Object,Object>(); // For statistics, which get dumped
    private int numErrors = 0;
    private StopWatch watch = new StopWatch();
    private List<String> saveFiles = new ArrayList<String>();
    private final String name;
    private String basePath;
    private PrintWriter logOut;
    private Random random = new Random();
    public String symlinkPath = null; // Link from symlinkPath to the final exec path

    private static Execution singleton;

    public static Execution v() {
        if (singleton == null) return singleton = new Execution(System.getProperty("execName"));
        return singleton;
    }

    public String path(String name) { return name == null ? basePath : basePath+"/"+name; }

    public Execution(String name) {
        this.name = name;
        basePath = Config.outDirName;
        logOut = new PrintWriter(System.out);
        output.put("hostname", getHostName());
        output.put("exec.status", "running");

        addSaveFiles("log.txt", "options.map", "output.map", "addToView");
        
        String view = System.getProperty("chord."+name+".addToView", null);
        if (view != null) {
            PrintWriter out = Utils.openOut(path("addToView"));
            out.println(view);
            out.close();
        }
        watch.start();
    }

    public void logs(String format, Object... args) {
        logOut.println(String.format(format, args));
        logOut.flush();
    }

    public void errors(String format, Object... args) {
        numErrors++;
        logOut.print("ERROR: ");
        logOut.println(String.format(format, args));
        logOut.flush();
    }

    public void putOption(String key, Object value) {
        logs("OPT %s = %s", key, value);
        options.put(key, value);
    }

    public void putOutput(String key, Object value) {
        logs("OUT %s = %s", key, value);
        output.put(key, value);
    }

    public void flushOutput() { writeMap("output.map", output); }

    public void flushOptions() { writeMap("options.map", options); }

    public void writeMap(String name, HashMap<Object,Object> map) {
        PrintWriter out = Utils.openOut(path(name));
        for (Object key : map.keySet()) {
            out.println(key+"\t"+map.get(key));
        }
        out.close();
    }

    public void finish(Throwable t) {
        if (t == null)
            output.put("exec.status", "done");
        else {
            output.put("exec.status", "failed");
            errors("%s", t);
            for (StackTraceElement e : t.getStackTrace())
                logs("    %s", e);
        }

        watch.stop();
        output.put("exec.time", watch);
        output.put("exec.errors", numErrors);
        flushOptions();
        flushOutput();

        // Delete stuff
        String files = System.getProperty("chord."+name+".deleteFiles");
        if (files != null) {
            for (String file : files.split(",")) {
                if (file.equals("")) continue;
                logs("Deleting %s", file);
                systemHard(new String[] { "rm", "-rf", basePath+"/"+file });
            }
        }

        String finalPoolPath = System.getProperty("chord."+name+".finalPoolPath");
        if (finalPoolPath != null) {
            String path;
            for (int i = random.nextInt(1000); new File(path = finalPoolPath+"/"+i+".exec").exists(); i++)
                ;
            logs("Copying %s to %s", saveFiles, path);
            if (!new File(path).mkdir()) throw new RuntimeException("Tried to created directory "+path+" but it already exists");
            for (String file : saveFiles)
                systemHard(new String[] { "cp", basePath+"/"+file, path });

            if (symlinkPath != null) systemHard(new String[] { "ln", "-s", path, symlinkPath });
        } else {
            if (symlinkPath != null) systemHard(new String[] { "ln", "-s", basePath, symlinkPath });
        }

        logs("Execution.finish() done");

        if (t != null) System.exit(1); // Die violently
    }

    public String getStringArg(String key, String defaultValue) {
        String value = System.getProperty("chord."+name+"."+key, defaultValue);
        if (value != null)
            putOption(key, value);
        return value;
    }

    public boolean getBooleanArg(String key, boolean defaultValue) {
        String s = getStringArg(key, ""+defaultValue);
        return s == null ? defaultValue : s.equals("true");
    }

    public int getIntArg(String key, int defaultValue) {
        String s = getStringArg(key, ""+defaultValue);
        return s == null ? defaultValue : Integer.parseInt(s);
    }

    public double getDoubleArg(String key, double defaultValue) {
        String s = getStringArg(key, ""+defaultValue);
        return s == null ? defaultValue : Double.parseDouble(s);
    }

    public void addSaveFiles(String... files) {
        for (String file : files) saveFiles.add(file);
    }

    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch(UnknownHostException e) {
            return "(unknown)";
        }
    }

    public static boolean system(String[] cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            p.getOutputStream().close();
            p.getInputStream().close();
            p.getErrorStream().close();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static <T> String join(T[] l) {
        StringBuilder buf = new StringBuilder();
        for (T x : l) {
            if (buf.length() > 0) buf.append(' ');
            buf.append(x);
        }
        return buf.toString();
    }

    public static void systemHard(String[] cmd) {
        if (!system(cmd))
            throw new RuntimeException("Command failed: " + join(cmd));
    }
}
