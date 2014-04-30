package chord.program;

import java.util.List;
import chord.util.IndexSet;
import chord.project.Messages;

 
import joeq.Main.HostedVM;
import joeq.Class.*;

/**
 * Dynamic analysis-based scope builder.
 *
 * Constructs scope by running the given Java program on the given input,
 * observing which classes are loaded (either using JVMTI or load-time bytecode
 * instrumentation, depending upon whether property {@code chord.use.jvmti}
 * is set to true or false, respectively), and then regarding all methods
 * declared in those classes as reachable.
 *
 * This scope builder does not currently resolve any reflection; use RTA instead.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class DynamicBuilder implements ScopeBuilder {
    private IndexSet<jq_Method> methods;

    @Override
    public IndexSet<jq_Method> getMethods() {
    	Messages.log("Ding: DEBUG: getMethods called");
        if (methods != null)
            return methods;
        Program program = Program.g();
        List<String> classNames = program.getDynamicallyLoadedClasses();
    	Messages.log("Ding: DEBUG: getMethods called getDynamicallyLoadedClasses");

        HostedVM.initialize();
        methods = new IndexSet<jq_Method>();
        for (String s : classNames) {
            jq_Class c = (jq_Class) program.loadClass(s);
            for (jq_Method m : c.getDeclaredStaticMethods()) {
                if (!m.isAbstract())
                    m.getCFG();
                methods.add(m);
            }
            for (jq_Method m : c.getDeclaredInstanceMethods()) {
                if (!m.isAbstract())
                    m.getCFG();
            	Messages.log("Ding: DEBUG: added method:" + m + " from dynamically loaded class");
                methods.add(m);
            }
        }
        return methods;
    }

    /*
     * Returns an empty reflect. Dynamic scope doesn't do reflection analysis.
     * Instead, Program.java uses the concrete classes that got created at run time.
     */
    @Override
    public Reflect getReflect() {
        return new Reflect();
    }
}
