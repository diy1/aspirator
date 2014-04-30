package chord.project;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.ArrayList;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import chord.util.IndexMap;
import chord.util.Utils;
import chord.util.ProcessExecutor;

/**
 * Common operations on files in the directory specified by system property
 * <tt>chord.out.dir</tt> to which Chord outputs all files.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class OutDirUtils {
    private static final String PROCESS_STARTING = "Starting command: '%s'";
    private static final String PROCESS_FINISHED = "Finished command: '%s'";
    private static final String PROCESS_FAILED = "Command '%s' terminated abnormally: %s";
    private static final String RESOURCE_NOT_FOUND = "Could not find resource '%s'.";

    public static PrintWriter newPrintWriter(String fileName) {
        try {
            return new PrintWriter(new File(Config.outDirName, fileName));
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String copyResourceByName(String srcFileName) {
        return copyResourceByName(srcFileName, (String) null);
    }

    public static String copyResourceByName(String srcFileName, String dstDirName) {
        InputStream is = Utils.getResourceAsStream(srcFileName);
        return copyResource(srcFileName, is, dstDirName, (new File(srcFileName)).getName());
    }

    public static String copyResourceByName(String srcFileName, InputStream is) {
        return copyResourceByName(srcFileName, is, null);
    }

    public static String copyResourceByName(String srcFileName, InputStream is, String dstDirName) {
        return copyResource(srcFileName, is, dstDirName, (new File(srcFileName)).getName());
    }

    public static String copyResourceByPath(String srcFileName) {
        return copyResourceByPath(srcFileName, (String) null);
    }

    public static String copyResourceByPath(String srcFileName, String dstDirName) {
        InputStream is = Utils.getResourceAsStream(srcFileName);
        return copyResource(srcFileName, is, dstDirName, srcFileName.replace('/', '_'));
    }

    public static String copyResourceByPath(String srcFileName, InputStream is) {
        return copyResourceByPath(srcFileName, is, null);
    }

    public static String copyResourceByPath(String srcFileName, InputStream is, String dstDirName) {
        return copyResource(srcFileName, is, dstDirName, srcFileName.replace('/', '_'));
    }

    public static String copyResource(String srcFileName, InputStream is, String dstDirName, String dstFileName) {
        if (is == null)
            Messages.fatal(RESOURCE_NOT_FOUND, srcFileName);
        File dstDir;
        if (dstDirName != null) {
            dstDir = new File(Config.outDirName, dstDirName);
            if (!dstDir.exists())
                dstDir.mkdir();
        } else
            dstDir = new File(Config.outDirName);
        File dstFile = new File(dstDir, dstFileName);
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            PrintWriter w = new PrintWriter(dstFile);
            String s;
            while ((s = r.readLine()) != null)
                w.println(s);
            r.close();
            w.close();
        } catch (IOException ex) {
            Messages.fatal(ex);
        }
        return dstFile.getAbsolutePath();
    }

    public static void writeMapToFile(IndexMap<String> map, String fileName) {
        Utils.writeMapToFile(map, new File(Config.outDirName, fileName));
    }

    public static void runSaxon(String xmlFileName, String xslFileName) {
        String dummyFileName = (new File(Config.outDirName, "dummy")).getAbsolutePath();
        xmlFileName = (new File(Config.outDirName, xmlFileName)).getAbsolutePath();
        xslFileName = (new File(Config.outDirName, xslFileName)).getAbsolutePath();
        try {
            net.sf.saxon.Transform.main(new String[] {
                "-o", dummyFileName, xmlFileName, xslFileName
            });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static final void executeWithFailOnError(List<String> cmdlist) {
        String[] cmdarray = new String[cmdlist.size()];
        executeWithFailOnError(cmdlist.toArray(cmdarray));
    }

    public static final void executeWithFailOnError(String[] cmdarray) {
        String cmd = "";
        for (String s : cmdarray)
            cmd += s + " ";
        if (Config.verbose >= 1) Messages.log(PROCESS_STARTING, cmd);
        try {
            int result = ProcessExecutor.execute(cmdarray);
            if (result != 0)
                throw new RuntimeException("Return value=" + result);
        } catch (Throwable ex) {
            Messages.fatal(PROCESS_FAILED, cmd, ex.getMessage());
        }
        if (Config.verbose >= 1) Messages.log(PROCESS_FINISHED, cmd);
    }

    public static final void executeWithWarnOnError(List<String> cmdlist, int timeout) {
        String[] cmdarray = new String[cmdlist.size()];
        executeWithWarnOnError(cmdlist.toArray(cmdarray), timeout);
    }

    public static final void executeWithWarnOnError(String[] cmdarray, int timeout) {
        String cmd = "";
        for (String s : cmdarray)
            cmd += s + " ";
        Messages.log(PROCESS_STARTING, cmd);
        try {
            ProcessExecutor.execute(cmdarray, null, null, timeout);
        } catch (Throwable ex) {
            Messages.fatal(PROCESS_FAILED, cmd, ex.getMessage());
        }
        Messages.log(PROCESS_FINISHED, cmd);
    }
}
