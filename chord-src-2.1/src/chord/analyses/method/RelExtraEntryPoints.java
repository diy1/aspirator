package chord.analyses.method;

import java.io.*;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import chord.program.ClassHierarchy;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

@Chord(
    name = "MentryPoints",
    sign = "M0:M0"
)
/**
 * A relation over domain M containing additional entry points for the program.
 * The values of this relation are derived from the file indicated by property 
 * chord.entrypoints.file.
 * 
 * File should be a list whose entries are class names, interface names, or fully qualified method names.
 *  (A fully qualified method name is of the form <name>:<desc>@<classname>.)
 *  If a concrete class is listed, all non-private methods of that class will be added as entry points.
 *  If an interface or abstract is listed, all public declared methods of that interaface/class in
 *  all concrete subclasses will be added as entry points.
 */
public class RelExtraEntryPoints extends ProgramRel {

    public final static String extraMethodsFile = System.getProperty("chord.entrypoints.file");
    public final static String extraMethodsList = System.getProperty("chord.entrypoints");
    static LinkedHashSet<jq_Method> methods;

    @Override
    public void fill() {
        Iterable<jq_Method> publicMethods =  slurpMList();

        for (jq_Method m: publicMethods) {
            super.add(m);
        }
    }



    public static Collection<jq_Method> slurpMList() {
        if (methods != null)
            return methods;
        if (extraMethodsList == null && extraMethodsFile == null)
            return Collections.emptyList();

        methods = new LinkedHashSet<jq_Method>();
        ClassHierarchy ch = Program.g().getClassHierarchy();

        if (extraMethodsList != null) {
            String[] entries = extraMethodsList.split(",");
            for (String s: entries)
                processLine(s, ch);
        }

        try {
            if (extraMethodsFile != null) {
                String s = null;
                BufferedReader br = new BufferedReader(new FileReader(extraMethodsFile));
                while( (s = br.readLine()) != null) {
                    if (s.startsWith("#"))
                        continue;
                    processLine(s, ch);
                }
                br.close();
            }
        } catch(IOException e) {
            e.printStackTrace();
        } 

        return methods;
    }

    private static void processLine(String s, ClassHierarchy ch) {
        try { 
            if (s.contains("@")) {
                // s is a method.
                int strudelPos = s.indexOf('@');
                int colonPos = s.indexOf(':');
                if (strudelPos > colonPos && colonPos > 0) {
                    String cName = s.substring(strudelPos+1);
                    String mName = s.substring(0, colonPos);
                    String mDesc = s.substring(colonPos+1, strudelPos);

                    jq_Class parentClass  =  (jq_Class) jq_Type.parseType(cName);

                    parentClass.prepare();  
                    jq_Method m = (jq_Method) parentClass.getDeclaredMember(mName, mDesc);
                    methods.add(m);
                } //badly formatted; skip
            } else { //s is a class name

                jq_Class pubI  =  (jq_Class) jq_Type.parseType(s);

                if (pubI == null) {
                    System.err.println("ERR: no such class " + s );
                    return;
                } else
                    pubI.prepare();  

                //two cases: pubI is an interface/abstract class or pubI is a concrete class.
                if (pubI.isInterface() || pubI.isAbstract()) {
                    Set<String> impls =  ch.getConcreteSubclasses(pubI.getName());
                    if (impls == null) {
                        System.err.println("ExtraEntryPoints: found no concrete impls or subclasses of " + pubI.getName());
                        return;
                    }

                    for (String impl:impls) {
                        jq_Class implClass = (jq_Class) jq_Type.parseType(impl);
                        implClass.prepare();
                        for (jq_Method ifaceM:   pubI.getDeclaredInstanceMethods()) {

                            jq_Class implementingClass = implClass;
                            while(implementingClass != null) {
                                jq_Method implM = implementingClass.getDeclaredInstanceMethod(ifaceM.getNameAndDesc());
                                if (implM != null) {
                                    methods.add(implM);
                                    break;
                                } else {
                                    implementingClass = implementingClass.getSuperclass();
                                    implementingClass.prepare();
                                }
                            }
                        }
                    }
                } else { //class is concrete
                    for (jq_Method m: pubI.getDeclaredInstanceMethods()) {
                        if (!m.isPrivate()) {
                            methods.add(m);
                        }
                    }

                    for (jq_Method m: pubI.getDeclaredStaticMethods()) {
                        if (!m.isPrivate()) {
                            methods.add(m);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
