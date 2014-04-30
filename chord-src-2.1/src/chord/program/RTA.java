package chord.program;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.lang.NoClassDefFoundError;

import joeq.Class.PrimordialClassLoader;
import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_ClassInitializer;
import joeq.Class.jq_Field;
import joeq.Class.jq_InstanceMethod;
import joeq.Class.jq_Method;
import joeq.Class.jq_NameAndDesc;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Invoke.InvokeInterface;
import joeq.Compiler.Quad.Operator.Invoke.InvokeVirtual;
import joeq.Main.HostedVM;

import chord.project.Messages;
import chord.project.Config;
import chord.program.reflect.CastBasedStaticReflect;
import chord.program.reflect.DynamicReflectResolver;
import chord.program.reflect.StaticReflectResolver;
import chord.util.IndexSet;
import chord.util.Timer;
import chord.util.tuple.object.Pair;

/**
 * Rapid Type Analysis (RTA) based scope builder.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 * @author Omer Tripp (omertripp@post.tau.ac.il)
 */
public class RTA implements ScopeBuilder {
    private static final String MAIN_CLASS_NOT_DEFINED =
        "ERROR: RTA: Property chord.main.class must be set to specify the main class of program to be analyzed.";
    private static final String MAIN_METHOD_NOT_FOUND =
        "ERROR: RTA: Could not find main class '%s' or main method in that class.";
    private static final String METHOD_NOT_FOUND_IN_SUBTYPE =
        "WARN: RTA: Expected instance method %s in class %s implementing/extending interface/class %s.";

    public static final boolean DEBUG = false;

    private final String reflectKind; // [none|static|static_cast|dynamic]

    /////////////////////////

    /*
     * Data structures used only if reflectKind == dynamic
     */

    private List<Pair<String, List<String>>> dynamicResolvedClsForNameSites;
    private List<Pair<String, List<String>>> dynamicResolvedObjNewInstSites;
    private List<Pair<String, List<String>>> dynamicResolvedConNewInstSites;
    private List<Pair<String, List<String>>> dynamicResolvedAryNewInstSites;

    /////////////////////////

    /*
     * Data structures used only if reflectKind == static
     */

    // Intra-procedural analysis for inferring the class loaded by calls to
    // {@code Class.forName(s)} and the class of objects allocated by calls to
    // {@code v.newInstance()}.  The analysis achieves this by
    // intra-procedurally tracking the flow of string constants to {@code s}
    // and the flow of class constants to {@code v}.
    private StaticReflectResolver staticReflectResolver;

    // Methods in which forName/newInstance sites have already been analyzed
    private Set<jq_Method> staticReflectResolved;

    //constructors invoked implicitly via reflection
    private LinkedHashSet<jq_Method> reflectiveCtors;

    /////////////////////////

    /*
     * Transient data structures reset after every iteration.
     */

    // Set of all classes whose clinits and super class/interface clinits
    // have been processed so far in current interation; this set is kept to
    // avoid repeatedly visiting super classes/interfaces within an
    // iteration (which incurs a huge runtime penalty) only to find that all
    // their clinits have already been processed in that iteration.
    private Set<jq_Class> classesVisitedForClinit;

    // Set of all methods deemed reachable so far in current iteration.
    private IndexSet<jq_Method> methods;

    /////////////////////////

    /*
     * Persistent data structures not reset after iterations.
     */

    private Reflect reflect;

    // set of all classes deemed reachable so far
    private IndexSet<jq_Reference> classes;

    // set of all (concrete) classes deemed instantiated so far either
    // by a reachable new/newarray statement or due to reflection
    private IndexSet<jq_Reference> reachableAllocClasses;

    // worklist for methods seen so far in current iteration but whose
    // CFGs haven't been processed yet
    private List<jq_Method> methodWorklist;

    // handle to the representation of class java.lang.Object
    private jq_Class javaLangObject;

    // flag indicating that another iteration is needed; it is set if
    // set reachableAllocClasses grows in the current iteration
    private boolean repeat = true;
    
    public RTA(String reflectKind) {
        this.reflectKind = reflectKind;
    }

    /**
     * @see chord.program.ScopeBuilder#getMethods()
     */
    @Override
    public IndexSet<jq_Method> getMethods() {
        if (methods == null) build();
        return methods;
    }

    /**
     * @see chord.program.ScopeBuilder#getReflect()
     */
    @Override
    public Reflect getReflect() {
        if (reflect == null) build();
        return reflect;
    }

    protected void build() {
        classes = new IndexSet<jq_Reference>();
        classesVisitedForClinit = new HashSet<jq_Class>();
        reachableAllocClasses = new IndexSet<jq_Reference>();
        methods = new IndexSet<jq_Method>();
        methodWorklist = new ArrayList<jq_Method>();
    
        if (Config.verbose >= 1) System.out.println("ENTER: RTA");
        Timer timer = new Timer();
        timer.init();
        if (reflectKind.equals("static")) {
            staticReflectResolver = new StaticReflectResolver();
            staticReflectResolved = new HashSet<jq_Method>();
            reflectiveCtors = new LinkedHashSet<jq_Method>();
        } else if (reflectKind.equals("static_cast")) {
            staticReflectResolved = new HashSet<jq_Method>();
            reflectiveCtors = new LinkedHashSet<jq_Method>();
            staticReflectResolver = new CastBasedStaticReflect(reachableAllocClasses, staticReflectResolved);
        } else if (reflectKind.equals("dynamic")) {
            DynamicReflectResolver dynamicReflectResolver = new DynamicReflectResolver();
            dynamicReflectResolver.run();
            dynamicResolvedClsForNameSites = dynamicReflectResolver.getResolvedClsForNameSites();
            dynamicResolvedObjNewInstSites = dynamicReflectResolver.getResolvedObjNewInstSites();
            dynamicResolvedConNewInstSites = dynamicReflectResolver.getResolvedConNewInstSites();
            dynamicResolvedAryNewInstSites = dynamicReflectResolver.getResolvedAryNewInstSites();
            reflectiveCtors = new LinkedHashSet<jq_Method>();
        }
         
        reflect = new Reflect();
        HostedVM.initialize();
        javaLangObject = PrimordialClassLoader.getJavaLangObject();
        String mainClassName = Config.mainClassName;
        if (mainClassName == null)
            Messages.fatal(MAIN_CLASS_NOT_DEFINED);
        jq_Class mainClass = (jq_Class) jq_Type.parseType(mainClassName);
        if (mainClass == null)
            Messages.fatal(MAIN_METHOD_NOT_FOUND, mainClassName);
        prepareClass(mainClass);
        jq_Method mainMethod = (jq_Method) mainClass.getDeclaredMember(
            new jq_NameAndDesc("main", "([Ljava/lang/String;)V"));
        if (mainMethod == null)
            Messages.fatal(MAIN_METHOD_NOT_FOUND, mainClassName);
        
        prepAdditionalEntrypoints(); //called for subclasses
        
        for (int i = 0; repeat; i++) {
            if (Config.verbose >= 1) System.out.println("Iteration: " + i);
            repeat = false;
            classesVisitedForClinit.clear();
            methods.clear();
            visitClinits(mainClass);
            visitMethod(mainMethod);

            visitAdditionalEntrypoints(); //called for subclasses
            
            if (reflectiveCtors != null)
                for (jq_Method m: reflectiveCtors) {
                    visitMethod(m);
                }
            
            while (!methodWorklist.isEmpty()) {
                int n = methodWorklist.size();
                jq_Method m = methodWorklist.remove(n - 1);
                if (DEBUG) System.out.println("Processing CFG of " + m);
                processMethod(m);
            }

            if (staticReflectResolver != null) {
                staticReflectResolver.startedNewIter();
            }
        }

        timer.done();
        if (Config.verbose >= 1) {
            System.out.println("LEAVE: RTA");
            System.out.println("Time: " + timer.getInclusiveTimeStr());
        }
        staticReflectResolver = null; // no longer in use; stop referencing it
    }

    /**
     * Invoked by RTA before starting iterations. A hook so subclasses can
     * add additional things to visit.
     * 
     * Note that this is invoked AFTER the hosted JVM is set up.
     */
    protected void prepAdditionalEntrypoints() {
        
    }

    /**
     * Invoked by RTA each iteration. A hook so subclasses can
     * add additional things to visit.
     */
    protected void visitAdditionalEntrypoints() {
        
    }

    /**
     * Called whenever RTA sees a method.
     * Adds to worklist if it hasn't previously been seen on this iteration.
     * @param m
     */
    protected void visitMethod(jq_Method m) {
        if (methods.add(m)) {
            if (DEBUG) System.out.println("\tAdding method: " + m);
            if (!m.isAbstract()) {
                methodWorklist.add(m);
            }
        }
    }

    private static boolean isClassDefined(Quad q, jq_Reference r) {
        try {
            r.load(); // triggers NoClassDefFoundError if not found. Do this before adding to reflect.
            return true;
        } catch(NoClassDefFoundError e) {
            String qpos = q.getMethod().getDeclaringClass() + " " +  q.getMethod() + ":" + q.getLineNumber(); 
            Messages.log(qpos + " references class "+ r + " via reflection. Class not found in classpath");
            return false;
        }
    }

    /*
     * It can happen that we see Class.forName("something not in classpath").
     * Should handle this gracefully.
     */
    private void processResolvedClsForNameSite(Quad q, jq_Reference r) {
        if (isClassDefined(q, r)) {
            reflect.addResolvedClsForNameSite(q, r);
            visitClass(r);
        }
    }

    private void processResolvedObjNewInstSite(Quad q, jq_Reference r) {
        if (!isClassDefined(q, r))
            return;

        reflect.addResolvedObjNewInstSite(q, r);
        visitClass(r);
        if (reachableAllocClasses.add(r) ||
                (staticReflectResolver != null && staticReflectResolver.needNewIter()))
            repeat = true;
        if (r instanceof jq_Class) {
            jq_Class c = (jq_Class) r;
            
        //two cases: call was Constructor.newInstance or call was Class.newInstance
        //Static reflection analysis folds these together, so we pull them apart here
            String cName =Invoke.getMethod(q).getMethod().getDeclaringClass().getName();
            if(cName.equals("java.lang.reflect.Constructor")) {
                processResolvedConNewInstSite(q, r);
            } else {
                jq_Method n = c.getInitializer(new jq_NameAndDesc("<init>", "()V"));
                if (n != null) {
                    visitMethod(n);
                    reflectiveCtors.add(n);
                }
            }
        }
    }

    private void processResolvedAryNewInstSite(Quad q, jq_Reference r) {
        if (!isClassDefined(q, r))
            return;
        reflect.addResolvedAryNewInstSite(q, r);
        visitClass(r);
        if (reachableAllocClasses.add(r))
            repeat = true;
    }

    private void processResolvedConNewInstSite(Quad q, jq_Reference r) {
        if (!isClassDefined(q, r))
            return;
        reflect.addResolvedConNewInstSite(q, r);
        visitClass(r);
        if (reachableAllocClasses.add(r))
            repeat = true;
        jq_Class c = (jq_Class) r;
        jq_InstanceMethod[] meths = c.getDeclaredInstanceMethods();
        // this is imprecise in that we are visiting all constrs instead of the called one
        // this is also unsound because we are not visiting constrs in superclasses
        for (int i = 0; i < meths.length; i++) {
            jq_InstanceMethod m = meths[i];
            if (m.getName().toString().equals("<init>")) {
                visitMethod(m);
                reflectiveCtors.add(m);
            }
        }
    }

    private void processMethod(jq_Method m) {
        if (staticReflectResolved != null && staticReflectResolved.add(m)) {
            staticReflectResolver.run(m);
            Set<Pair<Quad, jq_Reference>> resolvedClsForNameSites =
                staticReflectResolver.getResolvedClsForNameSites();
            Set<Pair<Quad, jq_Reference>> resolvedObjNewInstSites =
                staticReflectResolver.getResolvedObjNewInstSites();
            for (Pair<Quad, jq_Reference> p : resolvedClsForNameSites)
                processResolvedClsForNameSite(p.val0, p.val1);
            for (Pair<Quad, jq_Reference> p : resolvedObjNewInstSites)
                processResolvedObjNewInstSite(p.val0, p.val1);
        }
        ControlFlowGraph cfg = m.getCFG();
        for (BasicBlock bb : cfg.reversePostOrder()) {
            for (Quad q : bb.getQuads()) {
                if (DEBUG) System.out.println("Quad: " + q);
                Operator op = q.getOperator();
                if (op instanceof Invoke) {
                    if (op instanceof InvokeVirtual || op instanceof InvokeInterface)
                        processVirtualInvk(m, q);
                    else
                        processStaticInvk(m, q);
                } else if (op instanceof Getstatic) {
                    jq_Field f = Getstatic.getField(q).getField();
                    jq_Class c = f.getDeclaringClass();
                    visitClass(c);
                } else if (op instanceof Putstatic) {
                    jq_Field f = Putstatic.getField(q).getField();
                    jq_Class c = f.getDeclaringClass();
                    visitClass(c);
                } else if (op instanceof New) {
                    jq_Class c = (jq_Class) New.getType(q).getType();
                    visitClass(c);
                    if (reachableAllocClasses.add(c))
                        repeat = true;
                } else if (op instanceof NewArray) {
                    jq_Array a = (jq_Array) NewArray.getType(q).getType();
                    visitClass(a);
                    if (reachableAllocClasses.add(a))
                        repeat = true;
/*
                } else if (op instanceof Move) {
                    Operand ro = Move.getSrc(q);
                    if (ro instanceof AConstOperand) {
                        Object c = ((AConstOperand) ro).getValue();
                        if (c instanceof Class) {
                            String s = ((Class) c).getName();
                            // s is in encoded form only if it is an array type
                            jq_Reference d = (jq_Reference) jq_Type.parseType(s);
                            if (d != null)
                                visitClass(d);
                        }
                    }
*/
                }
            }
        }
    }

    // does qStr (in format bci!mName:mDesc@cName) correspond to quad q in method m?
    private static boolean matches(String qStr, jq_Method m, Quad q) {
        MethodElem me = MethodElem.parse(qStr);
        return me.mName.equals(m.getName().toString()) &&
            me.mDesc.equals(m.getDesc().toString()) &&
            me.cName.equals(m.getDeclaringClass().getName()) &&
            q.getBCI() == me.offset;
    }

    private void processVirtualInvk(jq_Method m, Quad q) {
        jq_Method n = Invoke.getMethod(q).getMethod();
        jq_Class c = n.getDeclaringClass();
        visitClass(c);
        visitMethod(n);
        String cName = c.getName();
        if (cName.equals("java.lang.Class")) {
            if (dynamicResolvedObjNewInstSites != null &&
                    n.getName().toString().equals("newInstance") &&
                    n.getDesc().toString().equals("()Ljava/lang/Object;")) {
                for (Pair<String, List<String>> p : dynamicResolvedObjNewInstSites) {
                    if (matches(p.val0, m, q)) {
                        for (String s : p.val1) {
                            jq_Reference r = (jq_Reference) jq_Type.parseType(s);
                            if (r != null)
                                processResolvedObjNewInstSite(q, r);
                        }
                        break;
                    }
                }
            }
        } else if (cName.equals("java.lang.reflect.Constructor")) {
            if (dynamicResolvedConNewInstSites != null &&
                    n.getName().toString().equals("newInstance") &&
                    n.getDesc().toString().equals("([Ljava/lang/Object;)Ljava/lang/Object;")) {
                for (Pair<String, List<String>> p : dynamicResolvedConNewInstSites) {
                    if (matches(p.val0, m, q)) {
                        for (String s : p.val1) {
                            jq_Reference r = (jq_Reference) jq_Type.parseType(s);
                            if (r != null)
                                processResolvedConNewInstSite(q, r);
                        }
                        break;
                    }
                }
            }
        }
        jq_NameAndDesc nd = n.getNameAndDesc();
        boolean isInterface = c.isInterface();
        for (jq_Reference r : reachableAllocClasses) {
            if (r instanceof jq_Array)
                continue;
            jq_Class d = (jq_Class) r;
            assert (!d.isInterface());
            assert (!d.isAbstract());
            boolean matches = isInterface ? d.implementsInterface(c) : d.extendsClass(c);
            if (matches) {
                jq_InstanceMethod m2 = d.getVirtualMethod(nd);
                if (m2 == null) {
                    Messages.log(METHOD_NOT_FOUND_IN_SUBTYPE,
                        nd.toString(), d.getName(), c.getName());
                } else {
                    visitMethod(m2);
                }
            }
        }
    }

    private void processStaticInvk(jq_Method m, Quad q) {
        jq_Method n = Invoke.getMethod(q).getMethod();
        jq_Class c = n.getDeclaringClass();
        visitClass(c);
        visitMethod(n);
        String cName = c.getName();
        if (cName.equals("java.lang.Class")) {
            if (dynamicResolvedClsForNameSites != null &&
                    n.getName().toString().equals("forName") &&
                    n.getDesc().toString().equals("(Ljava/lang/String;)Ljava/lang/Class;")) {
                for (Pair<String, List<String>> p : dynamicResolvedClsForNameSites) {
                    if (matches(p.val0, m, q)) {
                        for (String s : p.val1) {
                            jq_Reference r = (jq_Reference) jq_Type.parseType(s);
                            if (r != null)
                                processResolvedClsForNameSite(q, r);
                        }
                        break;
                    }
                }
            }
        } else if (cName.equals("java.lang.reflect.Array")) {
            if (dynamicResolvedAryNewInstSites != null &&
                    n.getName().toString().equals("newInstance") &&
                    n.getDesc().toString().equals("(Ljava/lang/Class;I)Ljava/lang/Object;")) {
                for (Pair<String, List<String>> p : dynamicResolvedAryNewInstSites) {
                    if (matches(p.val0, m, q)) {
                        for (String s : p.val1) {
                            jq_Reference r = (jq_Reference) jq_Type.parseType(s);
                            if (r != null)
                                processResolvedAryNewInstSite(q, r);
                        }
                        break;
                    }
                }
            }
        }
    }

    private void prepareClass(jq_Reference r) {
        if (classes.add(r)) {
            r.prepare();
            if (DEBUG) System.out.println("\tAdding class: " + r);
            if (r instanceof jq_Array) return;
            jq_Class c = (jq_Class) r;
            jq_Class d = c.getSuperclass();
            if (d == null)
                assert (c == javaLangObject);
            else
                prepareClass(d);
            for (jq_Class i : c.getDeclaredInterfaces())
                prepareClass(i);
        }
    }
    
    protected void visitClass(jq_Reference r) {
        prepareClass(r);
        if (r instanceof jq_Array) return;
        jq_Class c = (jq_Class) r;
        visitClinits(c);
    }

    protected void visitClinits(jq_Class c) {
        if (classesVisitedForClinit.add(c)) {
            jq_ClassInitializer m = c.getClassInitializer();
            // m is null for classes without class initializer method
            if (m != null)
                visitMethod(m);
            jq_Class d = c.getSuperclass();
            if (d != null)
                visitClinits(d);
            for (jq_Class i : c.getDeclaredInterfaces())
                visitClinits(i);
        }
    }
}
