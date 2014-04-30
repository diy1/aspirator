package chord.program;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import joeq.Class.PrimordialClassLoader;
import joeq.Class.jq_Class;
import joeq.Class.jq_Array;
import joeq.Class.jq_Reference;
import joeq.Class.jq_ClassInitializer;
import joeq.Class.jq_Field;
import joeq.Class.jq_InstanceMethod;
import joeq.Class.jq_Method;
import joeq.Class.jq_NameAndDesc;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Invoke.InvokeInterface;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import joeq.Compiler.Quad.Operator.Invoke.InvokeVirtual;
import joeq.Main.HostedVM;

import chord.project.Config;
import chord.project.Messages;
import chord.util.IndexSet;
import chord.util.Timer;

/**
 * Class Hierarchy Analysis (CHA) based scope builder.
 *
 * This scope builder currently does not resolve any reflection; use RTA instead.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class CHA implements ScopeBuilder {
    private static final String MAIN_CLASS_NOT_DEFINED =
        "ERROR: Property chord.main.class must be set to specify the main class of program to be analyzed.";
    private static final String MAIN_METHOD_NOT_FOUND =
        "ERROR: Could not find main class '%s' or main method in that class.";

    public static final boolean DEBUG = false;

    private IndexSet<jq_Reference> classes;

    // classes whose clinit and super class/interface clinits have been processed
    private Set<jq_Class> classesVisitedForClinit;

    // methods deemed reachable so far
    private IndexSet<jq_Method> methods;

    // worklist for methods that have been seen but whose cfg isn't processed yet
    private List<jq_Method> methodWorklist;

    private jq_Class javaLangObject;

    private final ClassHierarchy ch;

    public CHA(ClassHierarchy _ch) {
        ch = _ch;
    }

    @Override
    public IndexSet<jq_Method> getMethods() {
        if (methods != null)
            return methods;
        System.out.println("ENTER: CHA");
        Timer timer = new Timer();
        timer.init();
         classes = new IndexSet<jq_Reference>();
         classesVisitedForClinit = new HashSet<jq_Class>();
        methods = new IndexSet<jq_Method>();
         methodWorklist = new ArrayList<jq_Method>();
        HostedVM.initialize();
        javaLangObject = PrimordialClassLoader.getJavaLangObject();
        String mainClassName = Config.mainClassName;
        if (mainClassName == null)
            Messages.fatal(MAIN_CLASS_NOT_DEFINED);
           jq_Class mainClass = (jq_Class) jq_Type.parseType(mainClassName);
        prepareClass(mainClass);
        jq_NameAndDesc nd = new jq_NameAndDesc("main", "([Ljava/lang/String;)V");
        jq_Method mainMethod = (jq_Method) mainClass.getDeclaredMember(nd);
        if (mainMethod == null)
            Messages.fatal(MAIN_METHOD_NOT_FOUND, mainClassName);
        visitClinits(mainClass);
           visitMethod(mainMethod);
        while (!methodWorklist.isEmpty()) {
            jq_Method m = methodWorklist.remove(methodWorklist.size() - 1);
            ControlFlowGraph cfg = m.getCFG();
            if (DEBUG) System.out.println("Processing CFG of method: " + m);
            processCFG(cfg);
        }
        System.out.println("LEAVE: CHA");
        timer.done();
        System.out.println("Time: " + timer.getInclusiveTimeStr());
        return methods;
    }

    private void visitMethod(jq_Method m) {
        if (methods.add(m)) {
            if (!m.isAbstract()) {
                if (DEBUG) System.out.println("\tAdding method: " + m);
                methodWorklist.add(m);
            }
        }
    }

    private void processCFG(ControlFlowGraph cfg) {
        for (BasicBlock bb : cfg.reversePostOrder()) {
            for (Quad q : bb.getQuads()) {
                Operator op = q.getOperator();
                if (op instanceof Invoke) {
                    if (DEBUG) System.out.println("Quad: " + q);
                    jq_Method n = Invoke.getMethod(q).getMethod();
                    jq_Class c = n.getDeclaringClass();
                    visitClass(c);
                    visitMethod(n);
                    if (op instanceof InvokeVirtual ||
                            op instanceof InvokeInterface) {
                        jq_NameAndDesc nd = n.getNameAndDesc();
                        String cName = c.getName();
                        Set<String> subs = ch.getConcreteSubclasses(cName);
                        if (subs == null)
                            continue;
                        for (String dName : subs) {
                            jq_Class d = (jq_Class) jq_Type.parseType(dName);
                            visitClass(d);
                            assert (!d.isInterface());
                            assert (!d.isAbstract());
                            jq_InstanceMethod m2 = d.getVirtualMethod(nd);
                            if (m2 == null)
                                System.out.println(d + " " + nd);
                            assert (m2 != null);
                            visitMethod(m2);
                        }
                    } else
                        assert (op instanceof InvokeStatic);
                } else if (op instanceof Getstatic) {
                    if (DEBUG) System.out.println("Quad: " + q);
                    jq_Field f = Getstatic.getField(q).getField();
                    jq_Class c = f.getDeclaringClass();
                    visitClass(c);
                } else if (op instanceof Putstatic) {
                    if (DEBUG) System.out.println("Quad: " + q);
                    jq_Field f = Putstatic.getField(q).getField();
                    jq_Class c = f.getDeclaringClass();
                    visitClass(c);
                } else if (op instanceof New) {
                    if (DEBUG) System.out.println("Quad: " + q);
                    jq_Class c = (jq_Class) New.getType(q).getType();
                    visitClass(c);
                } else if (op instanceof NewArray) {
                    if (DEBUG) System.out.println("Quad: " + q);
                    jq_Array a = (jq_Array) NewArray.getType(q).getType();
                    visitClass(a);
                }
            }
        }
    }

    private void prepareClass(jq_Reference r) {
        if (classes.add(r)) {
            r.prepare();
            if (DEBUG) System.out.println("\tAdding class: " + r);
            if (r instanceof jq_Array)
                return;
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

    private void visitClass(jq_Reference r) {
        prepareClass(r);
        if (r instanceof jq_Array)
            return;
        jq_Class c = (jq_Class) r;
        visitClinits(c);
    }

    private void visitClinits(jq_Class c) {
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

    @Override
    public Reflect getReflect() {
        return new Reflect();
    }
}
