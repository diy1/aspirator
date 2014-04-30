package chord.instr;

import java.io.IOException;

import joeq.Class.jq_Reference;
import joeq.Class.jq_Class;
import joeq.Class.jq_Array;

import javassist.CtClass;
import javassist.CannotCompileException;
import javassist.NotFoundException;

import chord.program.Program;
import chord.project.Messages;
import chord.project.Config;
import chord.util.Utils;

/**
 * Offline class-file transformer.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public final class OfflineTransformer {
    private static final String INSTR_STARTING =
        "INFO: OfflineTransformer: Starting to instrument all classes; this may take a while ...";
    private static final String INSTR_FINISHED =
        "INFO: OfflineTransformer: Finished instrumenting all classes.";
    private static final String CANNOT_INSTRUMENT_CLASS =
        "ERROR: OfflineTransformer: Skipping instrumenting class %s; reason follows.";
    private static final String CLASS_NOT_BOOT_NOR_USER =
        "ERROR: OfflineTransformer: Skipping instrumenting class %s; its defining resource %s is neither in the boot nor user classpath.";
    private static final String WROTE_INSTRUMENTED_CLASS =
        "INFO: OfflineTransformer: Wrote instrumented class %s.";
    private static final String CLASS_NOT_FOUND =
        "WARN: OfflineTransformer: Could not find class %s in Javassist class pool.";

    private final String bootClassesDirName;
    private final String userClassesDirName;
    private final JavassistPool pool;
    private final BasicInstrumentor instrumentor;

    public OfflineTransformer(BasicInstrumentor instr) {
        instrumentor = instr;
        bootClassesDirName = Config.bootClassesDirName;
        userClassesDirName = Config.userClassesDirName;
        pool = instr.getPool();
    }

    public void run() {
        if (Config.verbose >= 1) Messages.log(INSTR_STARTING);
        Utils.deleteFile(bootClassesDirName);
        Utils.deleteFile(userClassesDirName);
        Program program = Program.g();
        for (jq_Reference r : program.getClasses()) {
            if (r instanceof jq_Array)
                continue;
            jq_Class c = (jq_Class) r;
            String cName = c.getName();
            Exception ex = null;
            try {
                CtClass clazz = instrumentor.edit(cName);
                if (clazz != null) {
                    String outDir = getOutDir(cName);
                    if (outDir != null) {
                        clazz.writeFile(outDir);
                        if (Config.verbose >= 2)
                            Messages.log(WROTE_INSTRUMENTED_CLASS, cName);
                    }
                }
            } catch (IOException e) {
                ex = e;
            } catch (NotFoundException e) {
                ex = e;
            } catch (CannotCompileException e) {
                ex = e;
            }
            if (ex != null) {
                Messages.log(CANNOT_INSTRUMENT_CLASS, cName);
                ex.printStackTrace();
            }
        }
        if (Config.verbose >= 1) Messages.log(INSTR_FINISHED);
    }

    public String getOutDir(String cName) {
        String rName = pool.getResource(cName);
        if (rName == null) {
            Messages.log(CLASS_NOT_FOUND, cName);
            return null;
        }
        if (pool.isBootResource(rName))
            return bootClassesDirName;
        if (pool.isUserResource(rName))
            return userClassesDirName;
        Messages.log(CLASS_NOT_BOOT_NOR_USER, cName, rName);
        return null;
    }

    public String getBootClassesDirName() {
        return bootClassesDirName;
    }

    public String getUserClassesDirName() {
        return userClassesDirName;
    }

    public JavassistPool getPool() {
        return pool;
    }

    public BasicInstrumentor getInstrumentor() {
        return instrumentor;
    }
}

