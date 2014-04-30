package chord.instr;

import java.util.Map;
import javassist.NotFoundException;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;
import javassist.expr.NewArray;
import javassist.expr.ArrayAccess;
import javassist.expr.FieldAccess;
import javassist.expr.ConstructorCall;
import javassist.expr.MethodCall;
import javassist.expr.MonitorEnter;
import javassist.expr.MonitorExit;
import javassist.CtClass;

import chord.project.Messages;
import chord.project.Config;

/**
 * Basic bytecode instrumentor providing hooks for transforming
 * classes, methods, and instructions.
 * 
 * Custom instrumentors must extend either this class or class
 * {@link chord.instr.Instrumentor}.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class BasicInstrumentor extends ExprEditor {
    public final static String INSTRUMENTOR_CLASS_KEY = "instrumentor_class";
    public final static String EVENT_HANDLER_CLASS_KEY = "event_handler_class";
    public final static String EVENT_HANDLER_ARGS_KEY = "event_handler_args";
    public final static String USE_JVMTI_KEY = "use_jvmti";

    private final static String EXPLICITLY_EXCLUDING_CLASS =
        "WARN: BasicInstrumentor: Not instrumenting class %s as it was excluded by chord.scope.exclude.";
    private final static String IMPLICITLY_EXCLUDING_CLASS =
        "WARN: BasicInstrumentor: Not instrumenting class %s as it was excluded implicity.";
    private final static String EXCLUDING_CLASS =
        "WARN: BasicInstrumentor: Not instrumenting class %s as commanded by overriding instrumentor.";
    private final static String EXPECTED_EVENT_HANDLER_CLASS =
        "ERROR: BasicInstrumentor: Could not find value of key " + EVENT_HANDLER_CLASS_KEY +
            " in instrumentor args; needed if JVMTI is not used.";

    protected int verbose;
    protected final JavassistPool pool;
    protected String[] scopeExcludeAry;
    protected Map<String, String> argsMap;
    protected CtClass currentClass;
    protected CtBehavior currentMethod;
    protected boolean useJvmti;
    protected boolean isMainClass;
    protected String eventHandlerInitCall;
    protected String eventHandlerDoneCall;

    /**
     * Constructor.
     *
     * @param    argsMap    Arguments to the instrumentor in the form of a
     *            map of (key, value) pairs.
     */
    public BasicInstrumentor(Map<String, String> argsMap) {
        this.argsMap = argsMap;
        scopeExcludeAry = Config.scopeExcludeAry;
        verbose = Config.verbose;
        pool = new JavassistPool();
        String s = argsMap.get(USE_JVMTI_KEY);
        useJvmti = (s == null) ? Config.useJvmti : s.equals("true");
        if (!useJvmti) {
            String c = argsMap.get(EVENT_HANDLER_CLASS_KEY);
            if (c == null)
                Messages.fatal(EXPECTED_EVENT_HANDLER_CLASS);
            String a = argsMap.get(EVENT_HANDLER_ARGS_KEY);
            a = (a == null) ? "" : a.replace("@", "=").replace("\\", "\\\\");
            eventHandlerInitCall = c + ".init(\"" + a + "\");";
            eventHandlerDoneCall = c + ".done();"; 
        }
    }

    public JavassistPool getPool() {
        return pool;
    }

    public boolean isExplicitlyExcluded(String cName) {
        for (String s : scopeExcludeAry) {
            if (cName.startsWith(s))
                return true;
        }
        return false;
    }
    
    public boolean isImplicitlyExcluded(String cName) {
        return cName.equals("java.lang.J9VMInternals") ||
            cName.startsWith("sun.reflect.Generated") ||
            cName.startsWith("java.lang.ref.");
    }

    public boolean isExcluded(String cName) {
        if (isImplicitlyExcluded(cName)) {
            if (verbose >= 2) Messages.log(IMPLICITLY_EXCLUDING_CLASS, cName);
            return true;
        }
        if (isExplicitlyExcluded(cName)) {
            if (verbose >= 2) Messages.log(EXPLICITLY_EXCLUDING_CLASS, cName);
            return true;
        }
        return false;
    }

    /**
     * Provides a hook to instrument a class specified by name.
     *
     * The default implementation excludes instrumenting classes that
     * are excluded implicitly or explicitly; for each class that is
     * not excluded, it calls the {@link #edit(CtClass)} method.
     * 
     * @param    cName    Name of the class to be instrumented
     *            (e.g., java.lang.Object).
     * @return    The instrumented class in Javassist's representation.
     *            It must be null if the class is not instrumented.
     * @throws    NotFoundException    If Javassist fails to find the class.
     * @throws    CannotCompileException    If Javassist fails to correctly
     *            instrument the class.
     */
    public CtClass edit(String cName)
            throws NotFoundException, CannotCompileException {
        if (isExcluded(cName))
            return null;
        CtClass clazz = pool.get(cName);
        assert (clazz != null);
        if (!useJvmti)
            isMainClass = cName.equals(Config.mainClassName);
        CtClass clazz2 = edit(clazz);
        if (clazz2 == null) {
            if (verbose >= 2) Messages.log(EXCLUDING_CLASS, cName);
        }
        return clazz2;
    }

    /**
     * Provides a hook to instrument a class specified in Javassist's
     * representation.
     *
     * The default implementation calls the hooks to instrument all
     * its methods, including its class initializer method (if any),
     * all its declared constructors, and all its declared methods.
     *
     * @param    clazz    Javassist's representation of the class to be
     *            instrumented.
     * @return    The instrumented class in Javassist's representation.    
     *            It must be null if the class is not instrumented.
     * @throws    CannotCompileException    If Javassist fails to correctly
     *            instrument the class. 
     */
    public CtClass edit(CtClass clazz) throws CannotCompileException {
        currentClass = clazz;
        CtBehavior clinit = clazz.getClassInitializer();
        if (clinit != null)
            edit(clinit);
        CtBehavior[] inits = clazz.getDeclaredConstructors();
        for (CtBehavior m : inits)
            edit(m);
        CtBehavior[] meths = clazz.getDeclaredMethods();
        for (CtBehavior m : meths) {
            edit(m);
            if (!useJvmti && isMainClass && m.getName().equals("main") &&
                    m.getSignature().equals("([Ljava/lang/String;)V")) {
                m.insertBefore(eventHandlerInitCall);
                m.insertAfter(eventHandlerDoneCall);
            }
        }
        return clazz;
    }

    /**
     * Provides a hook to instrument a method specified in Javassist's
     * representation.
     *
     * The default implementation visits each bytecode instruction in
     * the method's code, calling the {@link #insertBefore(int)}
     * method for each instruction, ws well as the relevant edit
     * method for certain kinds of instructions (namely, object
     * allocation, field access, array access, monitor enter/exit,
     * and method invocation).
     *
     * @param    method    Javassist's representation of the method to be
     *            instrumented in the currently instrumented class.
     * @throws    CannotCompileException    If Javassist fails to correctly
     *            instrument the class.
     */
    public void edit(CtBehavior method) throws CannotCompileException {
        currentMethod = method;
        method.instrument(this);
    }

    /**
     * Provides a hook to insert instrumentation just before the
     * specified bytecode instruction in its containing method.
     *
     * @param    pos    Index of a bytecode instruction in the currently
     *            instrumented method.
     * @return    Code string to be inserted just before the index.
     */
    public String insertBefore(int pos) {
        return null;
    }

    public void edit(NewExpr e) throws CannotCompileException { }

    public void edit(NewArray e) throws CannotCompileException { }

    public void edit(FieldAccess e) throws CannotCompileException { }

    public void edit(ArrayAccess e) throws CannotCompileException { }

    public void edit(MonitorEnter e) throws CannotCompileException { }

    public void edit(MonitorExit e) throws CannotCompileException { }

    public void edit(ConstructorCall e) throws CannotCompileException { }

    public void edit(MethodCall e) throws CannotCompileException { }
}

