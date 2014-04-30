package chord.instr;

import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.io.FilenameFilter;

import chord.project.Messages;
import chord.project.Config;

import chord.util.Utils;
import javassist.NotFoundException;
import javassist.ClassPool;
import javassist.CtClass;

/**
 * Class pool specifying program classpath for Javassist bytecode instrumentor.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class JavassistPool {
    private static final String IGNORE_PATH_ELEMENT =
        "WARN: JavassistPool: Skipping path element %s from %s";
    private static final String APPEND_PATH_ELEMENT =
        "INFO: JavassistPool: Appending path element %s from %s";
    private final Set<String> bootClassPathResourceNames;
    private final Set<String> userClassPathResourceNames;
    private final ClassPool pool;
    public JavassistPool() {
        pool = new ClassPool();
        bootClassPathResourceNames = new HashSet<String>();
        userClassPathResourceNames = new HashSet<String>();

        String bootClassPathName = System.getProperty("sun.boot.class.path");
        String[] bootClassPathElems = bootClassPathName.split(Utils.PATH_SEPARATOR);
        for (String pathElem : bootClassPathElems) {
            appendClassPathElem((new File(pathElem)).getAbsolutePath(), true);
        }

        String javaHomeDir = System.getProperty("java.home");
        assert (javaHomeDir != null);
        File libExtDir = new File(javaHomeDir, File.separator + "lib" + File.separator + "ext");
        if (libExtDir.exists()) {
            final FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    if (name.endsWith(".jar"))
                        return true;
                    return false;
                }
            };
            File[] subFiles = libExtDir.listFiles(filter);
            for (File file : subFiles) {
                appendClassPathElem(file.getAbsolutePath(), false);
            }
        }

        String userClassPathName = System.getProperty("java.class.path");
        String[] userClassPathElems = userClassPathName.split(Utils.PATH_SEPARATOR);
        for (String pathElem : userClassPathElems) {
            appendClassPathElem((new File(pathElem)).getAbsolutePath(), false);
        }
    }

    private void appendClassPathElem(String absPathElem, boolean isBoot) {
        try {
            pool.appendClassPath(absPathElem);
        } catch (NotFoundException ex) {
            Messages.log(IGNORE_PATH_ELEMENT, absPathElem, isBoot ? "boot classpath" : "user classpath");
            return;
        }
        if (Config.verbose >= 2)
            Messages.log(APPEND_PATH_ELEMENT, absPathElem, isBoot ? "boot classpath" : "user classpath");
        if (isBoot)
            bootClassPathResourceNames.add(absPathElem);
        else
            userClassPathResourceNames.add(absPathElem);
    }

    // never returns null
    public CtClass get(String cName) throws NotFoundException {
        return pool.get(cName);
    }

    public String getResource(String cName) {
        return pool.getResource(cName);
    }

    public boolean isBootResource(String rName) {
        return bootClassPathResourceNames.contains(rName);
    }

    public boolean isUserResource(String rName) {
        return userClassPathResourceNames.contains(rName);
    }

    public ClassPool getPool() {
        return pool;
    }

}
