package chord.program;

import java.util.Iterator;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.io.PrintWriter;
import java.io.IOException;

import com.java2html.Java2HTML;

import chord.project.OutDirUtils;
import chord.project.Messages;
import chord.project.Config;
import chord.util.tuple.object.Pair;
import chord.util.IndexSet;
import chord.util.ProcessExecutor;
import chord.util.Utils;
import chord.runtime.BasicEventHandler;
import chord.instr.BasicInstrumentor;
import joeq.Class.jq_Reference.jq_NullType;
import joeq.Compiler.Quad.BytecodeToQuad.jq_ReturnAddressType;
import joeq.Main.HostedVM;
import joeq.Class.jq_Type;
import joeq.Class.jq_Class;
import joeq.Class.jq_Array;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Method;
import joeq.Class.PrimordialClassLoader;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;

/**
 * Quadcode intermediate representation of a Java program.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Program {
    private static final String LOADING_CLASS =
        "INFO: Program: Loading class %s.";
    private static final String MAIN_CLASS_NOT_DEFINED =
        "ERROR: Program: Property chord.main.class must be set to specify the main class of program to be analyzed.";
    private static final String MAIN_METHOD_NOT_FOUND =
        "ERROR: Program: Could not find main class '%s' or main method in that class.";
    private static final String CLASS_PATH_NOT_DEFINED =
        "ERROR: Program: Property chord.class.path must be set to specify location(s) of .class files of program to be analyzed.";
    private static final String SRC_PATH_NOT_DEFINED =
        "ERROR: Program: Property chord.src.path must be set to specify location(s) of .java files of program to be analyzed.";
    private static final String METHOD_NOT_FOUND =
        "ERROR: Program: Could not find method '%s'.";
    private static final String CLASS_NOT_FOUND =
        "ERROR: Program: Could not find class '%s'.";
    private static final String STUBS_FILE_NOT_FOUND =
        "ERROR: Program: Cannot find native method stubs file '%s'.";

    private static Program program = null;
    private boolean isBuilt;
    private IndexSet<jq_Method> methods;
    private Reflect reflect;
    private IndexSet<jq_Reference> classes;
    private IndexSet<jq_Type> types;
    private Map<String, jq_Type> nameToTypeMap;
    private Map<String, jq_Reference> nameToClassMap;
    private Map<String, jq_Method> signToMethodMap;
    private jq_Method mainMethod;
    private boolean HTMLizedJavaSrcFiles;
    private ClassHierarchy ch;

    private Program() {
        if (Config.verbose >= 2)
            jq_Method.setVerbose();
        String ssaKind = Config.ssaKind;
        if (ssaKind.equals("phi"))
            jq_Method.doSSA(true);
        else if (ssaKind.equals("nophi"))
            jq_Method.doSSA(false);
        jq_Method.exclude(Config.scopeExcludeAry);
        Map<String, String> map = new HashMap<String, String>();
        String stubsFileName = Config.stubsFileName;
        BufferedReader r = Utils.getResourceAsReader(stubsFileName);
        if (r == null)
            Messages.fatal(STUBS_FILE_NOT_FOUND, stubsFileName);
        try {
            String s;
            while ((s = r.readLine()) != null) {
                String[] a = s.split(" ");
                assert (a.length == 2);
                map.put(a[0], a[1]);
            }
        } catch (IOException ex) {
            Messages.fatal(ex);
        }
        jq_Method.setNativeCFGBuilders(map);
    }

    /**
     * Provides the program's quadcode representation.
     *
     * @return The program's quadcode representation.
     */
    public static Program g() {
        if (program == null)
            program = new Program();
        return program;
    }

    /**
     * Provides the program's class hierarchy.
     *
     * @return The program's class hierarchy.
     */
    public ClassHierarchy getClassHierarchy() {
        if (ch == null)
            ch = new ClassHierarchy();
        return ch;
    }

    /**
     * Constructs the program's quadcode representation.
     *
     * Users need not call this method explicitly as it is called by each
     * method in this class that requires the representation to be built.
     */
    public void build() {
        if (!isBuilt) {
            buildClasses();
            isBuilt = true;
        }
    }

    /************************************************************************
     * Private helper functions
     ************************************************************************/

    private void buildMethods() {
        assert (methods == null);
        assert (reflect == null);
        assert (signToMethodMap == null);
        File methodsFile = new File(Config.methodsFileName);
        File reflectFile = new File(Config.reflectFileName);
        File extraClassFile = new File (Config.extraClassesFileName);
        if (Config.reuseScope && methodsFile.exists() && reflectFile.exists()) {
            loadMethodsFile(methodsFile);
            buildSignToMethodMap();
            loadReflectFile(reflectFile);
        } else if (extraClassFile.exists()) { // Added by Ding
        	if (Config.verbose >= 1)
        		System.out.println("DEBUG: found extraclassfile" + Config.extraClassesFileName);
        	loadExtraclassFile (extraClassFile);
            buildSignToMethodMap();
            saveMethodsFile(methodsFile);
            if (reflectFile.exists()) {
            	loadReflectFile(reflectFile);
            } 
        } else {
            String scopeKind = Config.scopeKind;
            ScopeBuilder b = null;
            if (scopeKind.equals("rta")) {
                b = new RTA(Config.reflectKind);
            } else if (scopeKind.equals("dynamic")) {
                b = new DynamicBuilder();
            } else if (scopeKind.equals("cha")) {
                b = new CHA(getClassHierarchy());
            } else {
                try {
                    Class<?> scopeBuildClass = Class.forName(scopeKind);
                    b = (ScopeBuilder) scopeBuildClass.newInstance();
                } catch(Exception e) {
                    System.err.println("didn't recognize scope builder named " + scopeKind +
                            ". Expected 'rta', 'cha', 'dynamic', or the name of a class implementing ScopeBuilder.");
                    System.exit(1);
                }
            }
            methods = b.getMethods();
            reflect = b.getReflect();

            buildSignToMethodMap();
            saveMethodsFile(methodsFile);
            saveReflectFile(reflectFile);
        }
    }

    private void buildClasses() {
        if (methods == null)
            buildMethods();
        assert (classes == null);
        assert (types == null);
        assert (nameToClassMap == null);
        assert (nameToTypeMap == null);
        PrimordialClassLoader loader = PrimordialClassLoader.loader;
        jq_Type[] typesAry = loader.getAllTypes();
        int numTypes = loader.getNumTypes();
        Arrays.sort(typesAry, 0, numTypes, comparator);
        types = new IndexSet<jq_Type>(numTypes + 2);
        classes = new IndexSet<jq_Reference>();
        types.add(jq_NullType.NULL_TYPE);
        types.add(jq_ReturnAddressType.INSTANCE);
        for (int i = 0; i < numTypes; i++) {
            jq_Type t = typesAry[i];
            assert (t != null);
            types.add(t);
            if (t instanceof jq_Reference && t.isPrepared()) {
                jq_Reference r = (jq_Reference) t;
                classes.add(r);
            }
        }
        buildNameToClassMap();
        buildNameToTypeMap();
    }
    
    /* Added by Ding. */
    private void loadExtraclassFile(File file) {
        List<String> l = Utils.readFileToList(file);
        methods = new IndexSet<jq_Method>(l.size());
        HostedVM.initialize();
        for (String s : l) {
        	/* Code below is copied from RelExtraEntryPoints.processLine*/
        	jq_Class pubI  =  (jq_Class) jq_Type.parseType(s);

            if (pubI == null) {
                System.out.println("WARNING: no such class from extra class file " + s );
                continue;
            } else {
            	try {
            		pubI.prepare(); 
            	} catch (Error e) {
                    System.out.println("WARNING: Cannot load class: " + s + ", exception: " + e);
            		continue;
            	}
            }
            
            if (pubI.isInterface() || pubI.isAbstract()) {
            	/* Ignore right now.. */
                System.out.println("INFO: class " + pubI.getName() + " is an interface or abstract class. Ignore now...");
                continue;
            }
                		
            for (jq_Method m: pubI.getDeclaredInstanceMethods()) {
                if (!m.isPrivate()) {
                    methods.add(m);
                }
                if (Config.verbose > 0)
                	System.out.println("DEBUG: added extra method:" + m);
            }

            for (jq_Method m: pubI.getDeclaredStaticMethods()) {
                if (!m.isPrivate()) {
                    methods.add(m);
                }
                if (Config.verbose > 0)
                	System.out.println("DEBUG: added extra method:" + m);

            }
        }
    }

    private void loadMethodsFile(File file) {
        List<String> l = Utils.readFileToList(file);
        methods = new IndexSet<jq_Method>(l.size());
        HostedVM.initialize();
        for (String s : l) {
            MethodSign sign = MethodSign.parse(s);
            String cName = sign.cName;
            jq_Class c = (jq_Class) loadClass(cName);
            String mName = sign.mName;
            String mDesc = sign.mDesc;
            jq_Method m = (jq_Method) c.getDeclaredMember(mName, mDesc);
            assert (m != null);
            /* Ding
            if (m == null) {
            	Messages.log("WARNING: cannot load method", s);
            	continue;
            }
            */
            if (!m.isAbstract())
                m.getCFG();
            methods.add(m);
        }
    }

    private void saveMethodsFile(File file) {
        try {
            PrintWriter out = new PrintWriter(file);
            for (jq_Method m : methods)
                out.println(m);
            out.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private List<Pair<Quad, List<jq_Reference>>> loadResolvedSites(BufferedReader in) {
        List<Pair<Quad, List<jq_Reference>>> l = new ArrayList<Pair<Quad, List<jq_Reference>>>();
        String s;
        try {
            while ((s = in.readLine()) != null) {
                if (s.startsWith("#"))
                    break;
                
                if (Utils.buildBoolProperty("chord.reflect.exclude", false)) {
                    boolean excludeLine = false;
                    String cName = strToClassName(s);
                    for (String c : Config.scopeExcludeAry) {
                        if (cName.startsWith(c)) {
                            excludeLine = true;
                            break;
                        }
                    }
                    if (excludeLine)
                        continue;
                }
                
                Pair<Quad, List<jq_Reference>> site = strToSite(s);
                
                l.add(site);
            }
        } catch (IOException ex) {
            Messages.fatal(ex);
        }
        return l;
    }

    private void saveResolvedSites(List<Pair<Quad, List<jq_Reference>>> l, PrintWriter out) {
        for (Pair<Quad, List<jq_Reference>> p : l) {
            String s = siteToStr(p);
            out.println(s);
        }
    }

    private void loadReflectFile(File file) {
        BufferedReader in = null;
        String s = null;
        try {
            in = new BufferedReader(new FileReader(file));
            s = in.readLine();
        } catch (IOException ex) {
            Messages.fatal(ex);
        }
        List<Pair<Quad, List<jq_Reference>>> resolvedClsForNameSites;
        List<Pair<Quad, List<jq_Reference>>> resolvedObjNewInstSites;
        List<Pair<Quad, List<jq_Reference>>> resolvedConNewInstSites;
        List<Pair<Quad, List<jq_Reference>>> resolvedAryNewInstSites;
        if (s == null) {
            resolvedClsForNameSites = Collections.emptyList();
            resolvedObjNewInstSites = Collections.emptyList();
            resolvedConNewInstSites = Collections.emptyList();
            resolvedAryNewInstSites = Collections.emptyList();
        } else {
            resolvedClsForNameSites = loadResolvedSites(in);
            resolvedObjNewInstSites = loadResolvedSites(in);
            resolvedConNewInstSites = loadResolvedSites(in);
            resolvedAryNewInstSites = loadResolvedSites(in);
        }
        reflect = new Reflect(resolvedClsForNameSites, resolvedObjNewInstSites,
            resolvedConNewInstSites, resolvedAryNewInstSites);
    }

    private void saveReflectFile(File file) {
        try {
            PrintWriter out = new PrintWriter(file);
            out.println("# resolvedClsForNameSites");
            saveResolvedSites(reflect.getResolvedClsForNameSites(), out);
            out.println("# resolvedObjNewInstSites");
            saveResolvedSites(reflect.getResolvedObjNewInstSites(), out);
            out.println("# resolvedConNewInstSites");
            saveResolvedSites(reflect.getResolvedConNewInstSites(), out);
            out.println("# resolvedAryNewInstSites");
            saveResolvedSites(reflect.getResolvedAryNewInstSites(), out);
            out.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String strToClassName(String s) {
        String[] a = s.split("->");
        assert (a.length == 2);
        MethodElem e = MethodElem.parse(a[0]);
        return e.cName;
    }
    
    private Pair<Quad, List<jq_Reference>> strToSite(String s) {
        String[] a = s.split("->");
        assert (a.length == 2);
        MethodElem e = MethodElem.parse(a[0]);
        Quad q = getQuad(e, Invoke.class);
        assert (q != null);
        String[] rNames = a[1].split(",");
        List<jq_Reference> rTypes = new ArrayList<jq_Reference>(rNames.length);
        for (String rName : rNames) {
            jq_Reference r = loadClass(rName);
            rTypes.add(r);
        }
        return new Pair<Quad, List<jq_Reference>>(q, rTypes);
    }

    private String siteToStr(Pair<Quad, List<jq_Reference>> p) {
        List<jq_Reference> l = p.val1;
        assert (l != null);
        int n = l.size();
        Iterator<jq_Reference> it = l.iterator();
         assert (n > 0);
        String s = p.val0.toByteLocStr() + "->" + it.next();
        for (int i = 1; i < n; i++)
            s += "," + it.next();
        return s;
    }

    private void buildNameToTypeMap() {
        assert (nameToTypeMap == null);
        assert (types != null);
        nameToTypeMap = new HashMap<String, jq_Type>();
        for (jq_Type t : types) {
            nameToTypeMap.put(t.getName(), t);
        }
    }

    private void buildNameToClassMap() {
        assert (nameToClassMap == null);
        assert (classes != null);
        nameToClassMap = new HashMap<String, jq_Reference>();
        for (jq_Reference c : classes) {
            nameToClassMap.put(c.getName(), c);
        }
    }

    private void buildSignToMethodMap() {
        assert (signToMethodMap == null);
        assert (methods != null);
        signToMethodMap = new HashMap<String, jq_Method>();
        for (jq_Method m : methods) {
            String mName = m.getName().toString();
            String mDesc = m.getDesc().toString();
            String cName = m.getDeclaringClass().getName();
            String sign = mName + ":" + mDesc + "@" + cName;
            signToMethodMap.put(sign, m);
        }
    }

    private static Comparator<jq_Type> comparator = new Comparator<jq_Type>() {
        @Override
        public int compare(jq_Type t1, jq_Type t2) {
            String s1 = t1.getName();
            String s2 = t2.getName();
            return s1.compareTo(s2);
        }
    };

    /**
     * Loads the given class, if it is not already loaded, and provides its quadcode representation.
     *
     * @param s The name of the class to be loaded.  It may be provided in any of several formats, e.g.,
     *        "{@code [I}", "{@code int[]}", "{@code java.lang.String[]}", "{@code [Ljava/lang/String;}".
     *
     * @return The quadcode representation of the given class.
     *
     * @throws Error If the class loading failed.
     */
    public jq_Reference loadClass(String s) throws Error {
        if (Config.verbose >= 2)
            Messages.log(LOADING_CLASS, s);
        jq_Reference c = (jq_Reference) jq_Type.parseType(s);
        if (c == null)
            throw new NoClassDefFoundError(s);
        c.prepare();
        return c;
    }

    /**
     * Provides the quadcode representation of all types deemed reachable.
     * A type is deemed reachable if it is referenced in any loaded class.
     *
     * @return The quadcode representation of all types deemed reachable.
     */
    public IndexSet<jq_Type> getTypes() {
        if (types == null)
            buildClasses();
        return types;
    }

    /**
     * Provides the quadcode representation of all methods deemed reachable by analysis scope construction.
     *
     * @return The quadcode representation of all methods deemed reachable by analysis scope construction.
     */
    public IndexSet<jq_Method> getMethods() {
        if (methods == null)
            buildMethods();
        return methods;
    }

    /**
     * Provides reflection information resolved by analysis scope construction.
     *
     * @return Reflection information resolved by analysis scope construction.
     */
    public Reflect getReflect() {
        if (reflect == null)
            buildMethods();
        return reflect;
    }

    /**
     * Provides the quadcode representation of all classes deemed reachable by analysis scope construction.
     *
     * @return The quadcode representation of all classes deemed reachable by analysis scope construction.
     */
    public IndexSet<jq_Reference> getClasses() {
        if (classes == null)
            buildClasses();
        return classes;
    }

    /**
     * Provides the quadcode representation of the given class, if it is deemed reachable, and null otherwise.
     *
     * @return The quadcode representation of the given class, if it is deemed reachable, and null otherwise.
     */
    public jq_Reference getClass(String name) {
        if (nameToClassMap == null)
            buildClasses();
        return nameToClassMap.get(name);
    }

    /**
     * Provides the quadcode representation of the given method, if it is deemed reachable, and null otherwise.
     *
     * @param mName Name of the method.
     * @param mDesc Descriptor of the method.
     * @param cName Name of the class declaring the method.
     *
     * @return The quadcode representation of the given method, if it is deemed reachable, and null otherwise.
     */
    public jq_Method getMethod(String mName, String mDesc, String cName) {
        return getMethod(mName + ":" + mDesc + "@" + cName);
    }

    /**
     * Provides the quadcode representation of the given method, if it is deemed reachable, and null otherwise.
     *
     * @param sign Signature of the method specifying its name, its descriptor, and its declaring class.
     *
     * @return The quadcode representation of the given method, if it is deemed reachable, and null otherwise.
     */
    public jq_Method getMethod(MethodSign sign) {
        return getMethod(sign.mName, sign.mDesc, sign.cName);
    }

    /**
     * Provides the quadcode representation of the given method, if it is deemed reachable, and null otherwise.
     *
     * @param sign Signature of the method in format {@code mName:mDesc@cName} specifying its name (mName),
     * its descriptor (mDesc), and its declaring class (cName).
     *
     * @return The quadcode representation of the given method, if it is deemed reachable, and null otherwise.
     */
    public jq_Method getMethod(String sign) {
        if (signToMethodMap == null)
            buildMethods();
        return signToMethodMap.get(sign);
    }

    /**
     * Provides the quadcode representation of the main method of the program, if it exists, and exits otherwise.
     */
    public jq_Method getMainMethod() {
        if (mainMethod == null) {
            String mainClassName = Config.mainClassName;
            if (mainClassName == null)
                Messages.fatal(MAIN_CLASS_NOT_DEFINED);
            mainMethod = getMethod("main", "([Ljava/lang/String;)V", mainClassName);
            if (mainMethod == null)
                Messages.fatal(MAIN_METHOD_NOT_FOUND, mainClassName);
        }
        return mainMethod;
    }

    /**
     * Provides the quadcode representation of the {@code start()} method of class {@code java.lang.Thread},
     * if it is deemed reachable, and null otherwise.
     *
     * @return The quadcode representation of the {@code start()} method of class {@code java.lang.Thread},
     * if it is deemed reachable, and null otherwise.
     */
    public jq_Method getThreadStartMethod() {
        return getMethod("start", "()V", "java.lang.Thread");
    }

    /**
     * Provides the quadcode representation of the given type, if it is deemed reachable, and null otherwise.
     *
     * @return The quadcode representation of the given type, if it is deemed reachable, and null otherwise.
     */
    public jq_Type getType(String name) {
        if (nameToTypeMap == null)
            buildClasses();
        return nameToTypeMap.get(name);
    }

    /**
     * Provides the first quad corresponding to the given bytecode instruction, if it exists, and null otherwise.
     *
     * @return The first quad corresponding to the given bytecode instruction, if it exists, and null otherwise.
     */
    public Quad getQuad(MethodElem e) {
        return getQuad(e, new Class[] { Operator.class });
    }

    /**
     * Provides the first quad corresponding to the given bytecode instruction and of the given quad kind,
     * if it exists, and null otherwise.
     *
     * @return The first quad corresponding to the given bytecode instruction and of the given quad kind,
     * if it exists, and null otherwise.
     */
    public Quad getQuad(MethodElem e, Class quadOpClass) {
        return getQuad(e, new Class[] { quadOpClass });
    }

    /**
     * Provides the first quad corresponding to the given bytecode instruction and of any of the given quad kinds,
     * if it exists, and null otherwise.
     *
     * @return The first quad corresponding to the given bytecode instruction and of any of the given quad kinds,
     * if it exists, and null otherwise.
     */
    public Quad getQuad(MethodElem e, Class[] quadOpClasses) {
        int offset = e.offset;
        jq_Method m = getMethod(e.mName, e.mDesc, e.cName);
        assert (m != null) : ("Method elem: " + e);
        return m.getQuad(offset, quadOpClasses);
    }

    /**
     * Provides a human-readable string that corresponds to the given bytecode string encoding a list of zero
     * or more types, if it is well-formed, and null otherwise.
     * Example: Converts "{@code [Ljava/lang/String;I}" to "{@code java.lang.String[],int}".
     *
     * @return A human-readable string that corresponds to the given bytecode string encoding a list of zero
     * or more types, if it is well-formed, and null otherwise.
     */
    public static String typesToStr(String typesStr) {
        String result = "";
        boolean needsSep = false;
        while (typesStr.length() != 0) {
            boolean isArray = false;
            int numDim = 0;
            String baseType;
            // Handle array case
            while (typesStr.startsWith("[")) {
                isArray = true;
                numDim++;
                typesStr = typesStr.substring(1);
            }
            // Determine base type
            if (typesStr.startsWith("B")) {
                baseType = "byte";
                typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("C")) {
                baseType = "char";
                typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("D")) {
                baseType = "double";
                typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("F")) {
                baseType = "float";
                typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("I")) {
                baseType = "int";
                typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("J")) {
                baseType = "long";
                typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("L")) {
                int index = typesStr.indexOf(';');
                if (index == -1)
                    return null;
                String className = typesStr.substring(1, index);
                baseType = className.replace('/', '.');
                typesStr = typesStr.substring(index + 1);
            } else if (typesStr.startsWith("S")) {
                baseType = "short";
                typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("Z")) {
                baseType = "boolean";
                typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("V")) {
                baseType = "void";
                typesStr = typesStr.substring(1);
            } else
                return null;
            if (needsSep)
                result += ",";
            result += baseType;
            if (isArray) {
                for (int i = 0; i < numDim; i++)
                    result += "[]";
            }
            needsSep = true;
        }
        return result;
    }

    /**
     * Executes the program and provides a list of all dynamically loaded classes.
     *
     * @return A list of all dynamically loaded classes.
     */
    public List<String> getDynamicallyLoadedClasses() {
        String mainClassName = Config.mainClassName;
        if (mainClassName == null)
            Messages.fatal(MAIN_CLASS_NOT_DEFINED);
        String classPathName = Config.userClassPathName;
        if (classPathName == null)
            Messages.fatal(CLASS_PATH_NOT_DEFINED);
        String[] runIDs = Config.runIDs.split(Utils.LIST_SEPARATOR);
        assert(runIDs.length > 0);
        List<String> classNames = new ArrayList<String>();
        String fileName = Config.classesFileName;
        
        String runBefore = System.getProperty("chord.dynamic.runBeforeCmd");
        Process beforeProc = null;
        try { 
            
            if (runBefore != null) {
                System.out.println("for dynamic analysis, running pre-command " + runBefore);
                beforeProc = ProcessExecutor.executeAsynch( new String[] {runBefore}, null, null);
            }
        } catch(Throwable ex) {
            ex.printStackTrace();
        }
        
        List<String> basecmd = new ArrayList<String>();
        basecmd.add("java");
        basecmd.addAll(Utils.tokenize(Config.runtimeJvmargs));
        Properties props = System.getProperties();
        basecmd.add("-cp");
        basecmd.add(classPathName);
        String cAgentArgs = "=classes_file=" + Config.classesFileName;
        if (Config.useJvmti)
            basecmd.add("-agentpath:" + Config.cInstrAgentFileName + cAgentArgs);
        else {
            String jAgentArgs = cAgentArgs +
                "=" + BasicInstrumentor.INSTRUMENTOR_CLASS_KEY +
                "=" + LoadedClassesInstrumentor.class.getName().replace('.', '/') +
                "=" + BasicInstrumentor.EVENT_HANDLER_CLASS_KEY +
                "=" + BasicEventHandler.class.getName().replace('.', '/');
            basecmd.add("-javaagent:" + Config.jInstrAgentFileName + jAgentArgs);
            for (Map.Entry e : props.entrySet()) {
                String key = (String) e.getKey();
                if (key.startsWith("chord."))
                    basecmd.add("-D" + key + "=" + e.getValue());
            }
        }
        basecmd.add(mainClassName);
        
        for (String runID : runIDs) {
            String args = System.getProperty("chord.args." + runID, "");
            List<String> fullcmd = new ArrayList<String>(basecmd);
            fullcmd.addAll(Utils.tokenize(args));
            OutDirUtils.executeWithWarnOnError(fullcmd, Config.dynamicTimeout);
            try {
                BufferedReader in = new BufferedReader(new FileReader(fileName));
                String s;
                while ((s = in.readLine()) != null) {
                    // convert "Ljava/lang/Object;" to "java.lang.Object"
                    String cName = Config.useJvmti ? typesToStr(s) : s;
                    classNames.add(cName);
                }
                in.close();
            } catch (Exception ex) {
                Messages.fatal(ex);
            }
        }
        if (beforeProc != null)
            beforeProc.destroy();
        return classNames;
    }

    /**
     * Converts and dumps the program's Java source files specified by property {@code chord.src.path}
     * to HTML files in the directory specified by property {@code chord.out.dir}.
     */
    public void HTMLizeJavaSrcFiles() {
        if (!HTMLizedJavaSrcFiles) {
            String srcPathName = Config.srcPathName;
            if (srcPathName == null)
                Messages.fatal(SRC_PATH_NOT_DEFINED);
            String[] srcDirNames = srcPathName.split(Utils.PATH_SEPARATOR);
            try {
                Java2HTML java2HTML = new Java2HTML();
                java2HTML.setMarginSize(4);
                java2HTML.setTabSize(4);
                java2HTML.setJavaDirectorySource(srcDirNames);
                java2HTML.setDestination(Config.outDirName);
                java2HTML.buildJava2HTML();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            HTMLizedJavaSrcFiles = true;
        }
    }
    
    /************************************************************************
     * Functions for printing methods and classes
     ************************************************************************/

    private void printClass(jq_Reference r) {
        System.out.println("*** Class: " + r);
        if (r instanceof jq_Array)
            return;
        jq_Class c = (jq_Class) r;
        for (jq_Method m : c.getDeclaredInstanceMethods()) {
            printMethod(m);
        }
        for (jq_Method m : c.getDeclaredStaticMethods()) {
            printMethod(m);
        }
    }

    private void printMethod(jq_Method m) {
        System.out.println("Method: " + m);
        if (!m.isAbstract()) {
            ControlFlowGraph cfg = m.getCFG();
            for (BasicBlock bb : cfg.reversePostOrder()) {
                for (Quad q : bb.getQuads()) {
                    int bci = q.getBCI();
                    System.out.println("\t" + bci + "#" + q.getID());
                }
            }
            System.out.println(cfg.fullDump());
        }
    }

    /**
     * Prints the quadcode representation of the given method, if it is deemed reachable, and exits otherwise.
     */
    public void printMethod(String sign) {
        jq_Method m = getMethod(sign);
        if (m == null)
            Messages.fatal(METHOD_NOT_FOUND, sign);
        printMethod(m);
    }

    /**
     * Prints the quadcode representation of the given class, if it is deemed reachable, and exits otherwise.
     */
    public void printClass(String className) {
        jq_Reference c = getClass(className);
        if (c == null)
            Messages.fatal(CLASS_NOT_FOUND, className);
        printClass(c);
    }

    /**
     * Prints the quadcode representation of all reachable classes.
     */
    public void printAllClasses() {
        for (jq_Reference c : getClasses())
            printClass(c);
    }
}

