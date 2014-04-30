package chord.instr;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import chord.project.ClassicProject;
import chord.project.Config;

import javassist.CtClass;
import javassist.CannotCompileException;
import javassist.NotFoundException;

import chord.project.Messages;

/**
 * Online (load-time) class-file transformer.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public final class OnlineTransformer implements ClassFileTransformer {
    private static final String RETRANSFORM_NOT_SUPPORTED =
        "ERROR: OnlineTransformer: JVM does not support retransforming classes.";
    private static final String CANNOT_INSTRUMENT_CLASS =
        "ERROR: OnlineTransformer: Skipping instrumenting class %s; reason follows.";
    private static final String CANNOT_MODIFY_CLASS =
        "WARN: OnlineTransformer: Cannot modify class %s.";
    private static final String TRANSFORMING_CLASS =
        "INFO: OnlineTransformer: Transforming class %s ...";

    // use reflection for Java 1.6 API (class retransformation) so that
    // Chord is usable with Java 1.5

    private static Class instrumentationClass;
    private static Method isRetransformClassesSupportedMethod;
    private static Method addTransformerMethod;
    private static Method isModifiableClassMethod;
    private static Method retransformClassesMethod;

    private static boolean isRetransformClassesSupported(Instrumentation i) {
        return (Boolean) invoke(isRetransformClassesSupportedMethod, i);
    }
    private static void addTransformer(Instrumentation i,
            ClassFileTransformer t, boolean b) {
        invoke(addTransformerMethod, i, new Object[] { t, b });
    }
    private static boolean isModifiableClass(Instrumentation i, Class c) {
        return (Boolean) invoke(isModifiableClassMethod, i, new Object[] { c });
    }
    private static void retransformClasses(Instrumentation i, Class[] a) {
        invoke(retransformClassesMethod, i, new Object[] { a });
    }

    private static Object invoke(Method m, Object o, Object... args) {
        Exception ex = null;
        try {
            return m.invoke(o, args);
        } catch (IllegalArgumentException e) {
            ex = e;
        } catch (InvocationTargetException e) {
            ex = e;
        } catch (IllegalAccessException e) {
            ex = e;
        }
        if (ex != null)
            Messages.fatal(ex);
        return null;
    }

    private static void initReflectiveMethods(Instrumentation i) {
        try {
            instrumentationClass = i.getClass();
            isRetransformClassesSupportedMethod = instrumentationClass.getMethod("isRetransformClassesSupported", (Class[]) null);
            addTransformerMethod = instrumentationClass.getMethod("addTransformer", new Class[] { ClassFileTransformer.class, boolean.class });
            isModifiableClassMethod = instrumentationClass.getMethod("isModifiableClass", new Class[] { Class.class });
            retransformClassesMethod = instrumentationClass.getMethod("retransformClasses", new Class[] { Class[].class });
        } catch (NoSuchMethodException e) {
            Messages.fatal(e);
        }
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        initReflectiveMethods(instrumentation);
        boolean isSupported = isRetransformClassesSupported(instrumentation);
        if (!isSupported)
               Messages.fatal(RETRANSFORM_NOT_SUPPORTED);
        Map<String, String> argsMap = new HashMap<String, String>();
        if (agentArgs != null) {
            String[] args = agentArgs.split("=");
            int n = args.length / 2;
            for (int k = 0; k < n; k++)
                argsMap.put(args[k*2], args[k*2+1]);
        }
        String instrClassName = argsMap.get(BasicInstrumentor.INSTRUMENTOR_CLASS_KEY);
        Class instrClass = null;
        if (instrClassName != null) {
            try {
                instrClass = Class.forName(instrClassName.replace('/', '.'));
            } catch (ClassNotFoundException e) {
                Messages.fatal(e);
            }
        } else
            instrClass = BasicInstrumentor.class;
        BasicInstrumentor instr = null;
        Exception ex = null;
        try {
            Constructor c = instrClass.getConstructor(new Class[] { Map.class });
            Object o = c.newInstance(new Object[] { argsMap });
            instr = (BasicInstrumentor) o;
        } catch (InstantiationException e) {
            ex = e;
        } catch (NoSuchMethodException e) {
            ex = e;
        } catch (InvocationTargetException e) {
            ex = e;
        } catch (IllegalAccessException e) {
            ex = e;
        }
        if (ex != null)
            Messages.fatal(ex);
        OnlineTransformer transformer = new OnlineTransformer(instr);
        addTransformer(instrumentation, transformer, true);
        List<Class> l = new ArrayList<Class>();
        for (Class c : instrumentation.getAllLoadedClasses()) {
            String cname = c.getName();
            if (!cname.startsWith("[")) {
                if (!isModifiableClass(instrumentation, c)) {
                    if (Config.verbose >= 2)
                        Messages.log(CANNOT_MODIFY_CLASS, cname);
                } else
                    l.add(c);
            }
        }
        Class[] a = l.toArray(new Class[l.size()]);
        retransformClasses(instrumentation, a);
    }

    private final BasicInstrumentor instrumentor;

    public OnlineTransformer(BasicInstrumentor instr) {
        instrumentor = instr;
    }

    // Note: DO NOT REMOVE THIS SYNCHRONIZATION
    @Override
    public synchronized byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        // Note from javadoc:
        // If this method determines that no transformations are needed, it should return null.
        // Otherwise, it should create a new byte[] array, copy the input classfileBuffer into it,
        // along with all desired transformations, and return the new array.
        // Argument classfileBuffer must not be modified.
        // Argument className is of the form "java/lang/Object".
        String cName = className.replace('/', '.');
        if (Config.verbose >= 2)
            Messages.log(TRANSFORMING_CLASS, cName);
        Exception ex = null;
        try {
            CtClass clazz = instrumentor.edit(cName);
            if (clazz != null) {
                return clazz.toBytecode();
            }
        } catch (IOException e) {
            ex = e;
        } catch (NotFoundException e) {
            ex = e; 
        } catch (CannotCompileException e) {
            ex = e; 
        }
        if (ex != null) {
            if (Config.verbose >= 2) {
                Messages.log(CANNOT_INSTRUMENT_CLASS, cName);
                ex.printStackTrace();
            }
        }
        return null;
    }
}

