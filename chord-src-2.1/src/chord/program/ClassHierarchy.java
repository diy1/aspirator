package chord.program;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.DataInput;
import java.io.IOException;

import joeq.Class.jq_ClassFileConstants;
import joeq.UTF.Utf8;
import joeq.Class.Classpath;
import joeq.Class.ClasspathElement;
import chord.project.Messages;
import chord.project.Config;
import chord.util.ArraySet;
import chord.util.Utils;
import chord.util.tuple.object.Pair;

/**
 * Class hierarchy implementation.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class ClassHierarchy {
    private static final String IGNORED_DUPLICATE_TYPES =
        "INFO: Class hierarchy builder: Ignored the following duplicate classes/interfaces coming from the indicated classpath elements:";
    private static final String EXCLUDED_TYPES_IN_CHORD =
        "WARN: Excluded the following classes/interfaces from scope because the classpath elements from which they originate are in chord.main.class.path:";
    private static final String EXCLUDED_TYPES_NOT_DYN_LOADED =
        "WARN: Excluded the following classes/interfaces from scope because they were not loaded dynamically and chord.ch.dynamic=true:";
    private static final String MISSING_TYPES =
        "WARN: Class hierarchy builder: Following classes/interfaces were not found in scope but each of them was either declared as a superclass or an implemented/extended interface of some class/interface in scope:";
    private static final String MISSING_SUPERCLASSES =
        "WARN: Class hierarchy builder: Ignored the following classes as some (direct or transitive) superclass of each of them is missing in scope:";
    private static final String MISSING_SUPERINTERFS =
        "WARN: Class hierarchy builder: Ignored the following classes/interfaces as some (direct or transitive) interface implemented/extended by each of them is missing in scope:";

    /**
     * Map from each class or interface in scope to the kind of its type (interface, abstract class, or concrete class).
     */
    private Map<String, TypeKind> clintToKind;
    /**
     * Map from each (concrete or abstract) class c in scope to the class d (not necessarily in scope) such that:
     * 1. if c == java.lang.Object then d == null, and
     * 2. if c is a class other than java.lang.Object then d is the declared superclass of c.
     * Note that c cannot be an interface.
     */
    private Map<String, String> classToDeclaredSuperclass;
    /**
     * Map from each class/interface c in scope to the set of interfaces S (not necessarily in scope) such that:
     * 1. if c is an interface then S is the set of interfaces that c declares it extends, and
     * 2. if c is a class then S is the set of interfaces that c declares it implements.
     */
    private Map<String, Set<String>> clintToDeclaredInterfaces;
    /**
     * Map from:
     * 1. each (concrete or abstract) class in scope to the set of all its concrete subclasses in scope (including itself if it is concrete).
     * 2. each interface in scope to the set of all concrete classes in scope that implement it.
     */
    private Map<String, Set<String>> clintToAllConcreteSubs;

    /**
     * Set consisting of:
     * 1. each class not in scope but declared as the superclass of some class in scope, and
     * 2. each interface not in scope but declared as an implemented/extended interface of some class/interface respectively in scope.
     */
    private Set<String> missingClints;

    public String getDeclaredSuperclass(String c) {
        if (classToDeclaredSuperclass == null)
            build();
        return classToDeclaredSuperclass.get(c);
    }

    public Set<String> getDeclaredInterfaces(String t) {
        if (clintToDeclaredInterfaces == null)
            build();
        return clintToDeclaredInterfaces.get(t);
    }

    /**
     * Provides the set of all concrete classes that subclass/implement a given class/interface.
     *
     * @param s The name of a class or interface.
     *
     * @return The set of all concrete classes that subclass/implement (directly or transitively) the 
     *         class/interface named s, if it exists in the class hierarchy, and null otherwise.
     */
    public Set<String> getConcreteSubclasses(final String s) {
        if (clintToAllConcreteSubs == null) {
            if (clintToKind == null)
                build();
            missingClints = new HashSet<String>();
            Set<String> missingSuperclasses = new HashSet<String>();
            Set<String> missingSuperInterfs = new HashSet<String>();
            // Map from each concrete class in scope to set containing
            // itself and its (direct and transitive) superclasses and
            // its implemented interfaces (not necessarily in scope).
            Map<String, Set<String>> concreteClassToAllSups =
                new HashMap<String, Set<String>>();
            clintToAllConcreteSubs = new HashMap<String, Set<String>>();
            Set<String> emptyRdOnlySet = Collections.emptySet();
            for (String c : clintToKind.keySet()) {
                clintToAllConcreteSubs.put(c, emptyRdOnlySet);
                if (clintToKind.get(c) != TypeKind.CONCRETE_CLASS)
                    continue;
                Set<String> clints = new ArraySet<String>(2);
                clints.add(c);  // every concrete class is a concrete successor to itself
                boolean success1 = true;
                boolean success2 = true;
                String d = c;
                while (true) {
                    success2 &= populateInterfaces(d, clints);
                    String superClass = classToDeclaredSuperclass.get(d);
                    if (superClass == null) {
                        if (!d.equals("java.lang.Object")) {
                            missingClints.add(d);
                            success1 = false;
                        }
                        break;
                    }
                    boolean added = clints.add(superClass);
                    assert (added);
                    d = superClass;
                }
                if (success1 && success2) {
                    concreteClassToAllSups.put(c, clints);
                    continue;
                }
                if (!success1)
                    missingSuperclasses.add(c);
                if (!success2)
                    missingSuperInterfs.add(c);
            }
            if (!missingClints.isEmpty()) {
                Messages.log(MISSING_TYPES);
                for (String c : missingClints)
                    Messages.log("\t" + c);
            }
            if (!missingSuperclasses.isEmpty()) {
                Messages.log(MISSING_SUPERCLASSES);
                for (String c : missingSuperclasses)
                    Messages.log("\t" + c);
            }
            if (!missingSuperInterfs.isEmpty()) {
                Messages.log(MISSING_SUPERINTERFS);
                for (String c : missingSuperInterfs)
                    Messages.log("\t" + c);
            }
            for (String c : concreteClassToAllSups.keySet()) {
                Set<String> sups = concreteClassToAllSups.get(c);
                for (String d : sups) {
                    Set<String> subs = clintToAllConcreteSubs.get(d);
                    if (subs == null || subs == emptyRdOnlySet) {
                        subs = new ArraySet<String>(2);
                        clintToAllConcreteSubs.put(d, subs);
                    }
                    subs.add(c);
                }
            }
            missingClints.clear();
        }

        return clintToAllConcreteSubs.get(s);
    }

    // builds maps clintToKind, classToDeclaredSuperclass, and clintToDeclaredInterfaces
    private void build() {
        System.out.println("Starting to build class hierarchy; this may take a while ...");
        Set<String> dynLoadedTypes = null;
        if (Config.CHkind.equals("dynamic")) {
            List<String> list = Program.g().getDynamicallyLoadedClasses();
            dynLoadedTypes = new HashSet<String>(list.size());
            dynLoadedTypes.addAll(list);
        }
        Classpath cp = new Classpath();
        cp.addToClasspath(System.getProperty("sun.boot.class.path"));
        cp.addExtClasspath();
        cp.addToClasspath(Config.userClassPathName);
        List<ClasspathElement> cpeList = cp.getClasspathElements();

        // logging info
        List<Pair<String, String>> duplicateTypes = new ArrayList<Pair<String, String>>();
        List<String> excludedTypes = new ArrayList<String>();
        List<String> typesNotDynLoaded = new ArrayList<String>();

        clintToKind = new HashMap<String, TypeKind>();
        classToDeclaredSuperclass = new HashMap<String, String>();
        clintToDeclaredInterfaces = new HashMap<String, Set<String>>();

        for (ClasspathElement cpe : cpeList) {
            for (String fileName : cpe.getEntries()) {
                if (!fileName.endsWith(".class"))
                    continue;
                String baseName = fileName.substring(0, fileName.length() - 6);
                String typeName = baseName.replace('/', '.');

                // ignore types excluded from scope
                if (Config.isExcludedFromScope(typeName)) {
                    excludedTypes.add(typeName);
                    continue;
                }

                // ignore duplicate types in classpath
                if (clintToKind.containsKey(typeName)) {
                    duplicateTypes.add(new Pair<String, String>(typeName, cpe.toString()));
                    continue;
                }

                if (dynLoadedTypes != null && !dynLoadedTypes.contains(typeName)) {
                    typesNotDynLoaded.add(typeName);
                    continue;
                }
                InputStream is = cpe.getResourceAsStream(fileName);
                assert (is != null);
                DataInputStream in = new DataInputStream(is);
                if (Config.verbose >= 2)
                    Messages.log("Processing class file %s from %s", fileName, cpe);
                processClassFile(in, typeName);
            }
        }

        if (Config.verbose >= 2) {
            if (!duplicateTypes.isEmpty()) {
                Messages.log(IGNORED_DUPLICATE_TYPES);
                for (Pair<String, String> p : duplicateTypes)
                    Messages.log("\t%s, %s", p.val0, p.val1);
            }
            if (!excludedTypes.isEmpty()) {
                Messages.log(EXCLUDED_TYPES_IN_CHORD);
                for (String s : excludedTypes)
                    Messages.log("\t" + s);
            }
            if (!typesNotDynLoaded.isEmpty()) {
                Messages.log(EXCLUDED_TYPES_NOT_DYN_LOADED);
                for (String s : typesNotDynLoaded)
                    Messages.log("\t" + s);
            }
        }
        System.out.println("Finished building class hierarchy.");
    }

    // Description of superclass and interfaces sections in class file of class c:
    // 1. superclass d:
    //   if c is an interface then d is java.lang.Object
    //   if c == java.lang.Object then d is null (has index 0 in constant pool)
    //   if c is a class other than java.lang.Object then d is the declared superclass of c
    // 2. interfaces S:
    //   if c is an interface then S is the set of interfaces c declares it extends
    //   if c is a class then S is the set of interfaces c declares it implements
    private void processClassFile(DataInputStream in, String className) {
        try {
            int magicNum = in.readInt(); // 0xCAFEBABE
            if (magicNum != 0xCAFEBABE) {
                throw new ClassFormatError("bad magic number: " + Integer.toHexString(magicNum));
            }
            in.readUnsignedShort(); // read minor_version
            in.readUnsignedShort(); // read major_version
            int constant_pool_count = in.readUnsignedShort();
            Object[] constant_pool = processConstantPool(in, constant_pool_count);
            char access_flags = (char) in.readUnsignedShort();  // read access_flags
            int self_index = in.readUnsignedShort();
            int super_index = in.readUnsignedShort();
            if (super_index == 0) {
                assert (className.equals("java.lang.Object"));
                classToDeclaredSuperclass.put(className, null);
                clintToKind.put(className, TypeKind.CONCRETE_CLASS);
            } else {
                int c = (Integer) constant_pool[super_index];
                Utf8 utf8 = (Utf8) constant_pool[c];
                String superclassName = utf8.toString().replace('/', '.');
                if (isInterface(access_flags)) {
                    assert (superclassName.equals("java.lang.Object"));
                    clintToKind.put(className, TypeKind.INTERFACE);
                } else {
                    classToDeclaredSuperclass.put(className, superclassName);
                    if (isAbstract(access_flags))
                        clintToKind.put(className, TypeKind.ABSTRACT_CLASS);
                    else
                        clintToKind.put(className, TypeKind.CONCRETE_CLASS);
                }
            }
            int n_interfaces = (int) in.readUnsignedShort();
            Set<String> interfaces = new ArraySet<String>(n_interfaces);
            clintToDeclaredInterfaces.put(className, interfaces);
            for (int i = 0; i < n_interfaces; ++i) {
                int interface_index = in.readUnsignedShort();
                int c = (Integer) constant_pool[interface_index];
                Utf8 utf8 = (Utf8) constant_pool[c];
                String interfaceName = utf8.toString().replace('/', '.');
                interfaces.add(interfaceName);
            }
            in.close();
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean isInterface(char access_flags) {
        return (access_flags & jq_ClassFileConstants.ACC_INTERFACE) != 0;
    }

    private static boolean isAbstract(char access_flags) {
        return (access_flags & jq_ClassFileConstants.ACC_ABSTRACT) != 0;
    }

    private boolean populateInterfaces(String c, Set<String> result) {
        Set<String> interfaces = clintToDeclaredInterfaces.get(c);
        if (interfaces == null) {
            missingClints.add(c);
            return false;
        }
        boolean success = true;
        for (String d : interfaces) {
            if (result.add(d)) {
                success &= populateInterfaces(d, result);
            }
        }
        return success;
    }

    private Object[] processConstantPool(DataInput in, int size) throws IOException {
        Object[] constant_pool = new Object[size];
        for (int i = 1; i < size; ++i) { // CP slot 0 is unused
            byte b = in.readByte();
            switch (b) {
            case jq_ClassFileConstants.CONSTANT_Integer:
                in.readInt();
                break;
            case jq_ClassFileConstants.CONSTANT_Float:
                in.readFloat();
                break;
            case jq_ClassFileConstants.CONSTANT_Long:
                ++i;
                in.readLong();
                break;
            case jq_ClassFileConstants.CONSTANT_Double:
                ++i;
                in.readDouble();
                break;
            case jq_ClassFileConstants.CONSTANT_Utf8:
            {
                byte utf[] = new byte[in.readUnsignedShort()];
                in.readFully(utf);
                constant_pool[i] = Utf8.get(utf);
                break;
            }
            case jq_ClassFileConstants.CONSTANT_Class:
                constant_pool[i] = new Integer(in.readUnsignedShort());
                break;
            case jq_ClassFileConstants.CONSTANT_String:
                in.readUnsignedShort();
                break;
            case jq_ClassFileConstants.CONSTANT_NameAndType:
            case jq_ClassFileConstants.CONSTANT_FieldRef:
            case jq_ClassFileConstants.CONSTANT_MethodRef:
            case jq_ClassFileConstants.CONSTANT_InterfaceMethodRef:
                in.readUnsignedShort();
                in.readUnsignedShort();
                break;
            default:
                throw new ClassFormatError("bad constant pool entry tag: entry="+i+", tag="+b);
            }
        }
        return constant_pool;
    }
    
    /**
     * Returns the set of every class name examined by ClassHierarchy
     */
    public Set<String> allClassNamesInPath() {
        if (clintToKind == null)
            build();
        return clintToKind.keySet();
    }
}
