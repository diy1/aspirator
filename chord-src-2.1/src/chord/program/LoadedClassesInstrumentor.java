package chord.program;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.File;
import java.io.IOException;

import javassist.NotFoundException;
import javassist.CannotCompileException;
import javassist.CtClass;

import chord.instr.BasicInstrumentor;
import chord.project.Messages;

/**
 * Bytecode instrumentor for determining all loaded classes.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class LoadedClassesInstrumentor extends BasicInstrumentor {
    List<String> loadedClasses = new ArrayList<String>();
    public LoadedClassesInstrumentor(Map<String, String> argsMap) {
        super(argsMap);
        final String fileName = argsMap.get("classes_file");
        assert (fileName != null);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    PrintWriter out = new PrintWriter(new File(fileName));
                    for (String s : loadedClasses)
                        out.println(s);
                    out.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    System.exit(1);
                }
            }
        });
    }

    @Override
    public CtClass edit(String cName) throws NotFoundException, CannotCompileException {
        if (!isExcluded(cName))
            loadedClasses.add(cName);
        return null;
    }

    @Override
    public CtClass edit(CtClass clazz) throws CannotCompileException {
        assert(false);
        return null;
    }
}

