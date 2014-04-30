package chord.analyses.invk;

import chord.analyses.method.DomM;
import joeq.Class.jq_Class;
import joeq.Class.jq_InstanceMethod;
import joeq.Class.jq_Method;
import joeq.Class.jq_NameAndDesc;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Used to rewrite method calls, to facilitate stub implementations or
 * analyzable models.
 * If property chord.methodRemapFile is set, will read a map from that file.
 * There two sorts of entries supported: overriding a particular concrete method,
 * and overriding an entire class.
 * 
 * For overriding a method, the format is source-method dest-method, separated
 * by a space. Both source and dest should be fully qualified method names, of 
 * the form methodName:desc@declaringClass
 * 
 * Note that for virtual function calls, the rewrite happens AFTER
 * the call target is resolved. So if you have a stub implementation for
 * Derived.foo, then a call to Base.foo on an instance of Derived should
 * call the stub.
 * 
 * Alternatively, you can override an entire class. The format is simply
 * previousClass newClass, separated by a space. Unlike the method case,
 * here the substitution happens BEFORE virtual function resolution.
 * The idea is to let you override the behavior of a whole class hierarchy,
 * e.g. by replacing all Collections with an easier-to-model one.
 * 
 * Be careful about the prototype for the function being mapped; the remap
 * will fail with a warning message if any details do not match.
 * 
 * Blank lines and lines starting with a # are ignored as comments.
 * 
 * Note also that there is no checking performed that the old and new functions
 * have the compatible prototypes. Arguments and return values may wind up
 * not propagating correctly if, e.g., a 2-argument function is remapped
 * to a 3-argument function.
 * 
 *
 */
public class StubRewrite {
    
    private static HashMap<jq_Method,jq_Method> methLookupTable; //initialized only once
    private static HashMap<jq_Reference,jq_Class> classLookupTable; //initialized only once
    private static HashSet<jq_Class> stubDests = new HashSet<jq_Class>();
    
    private static jq_Method getMeth(String clName, String methname, String desc) {
        jq_Class cl = (jq_Class) jq_Type.parseType(clName);
        cl.prepare();

        return (jq_Method) cl.getDeclaredMember(methname,desc);
    }
    

    private static void mapClassNames(String srcClassName, String destClassName) {
        jq_Class src = (jq_Class) jq_Type.parseType(srcClassName);
        src.prepare();
        jq_Class dest = (jq_Class) jq_Type.parseType(destClassName);
        dest.prepare();
        System.out.println("StubRewrite mapping "+ srcClassName + " to " + destClassName);
        classLookupTable.put(src, dest);        
    }
    
    //called for virtual function lookup
    public static jq_Method maybeReplaceVirtCallDest(jq_InstanceMethod base,
            jq_InstanceMethod derived, DomM domM) {
        jq_Class baseClass= base.getDeclaringClass();
        jq_Class remapClass = classLookupTable.get(baseClass);
        if(remapClass != null) {
//            System.out.println("found class-level remap of " + baseClass);
            jq_NameAndDesc nd = base.getNameAndDesc();
            jq_Method remapped = remapClass.getInstanceMethod(nd);
            if(remapped == null) {
                System.err.println("WARN StubRewrite remap failed due to missing target method for " + remapClass + " " + nd);
                assert false;
                return derived; //assume no remap
            } else  if(!domM.contains(remapped)) {
                System.err.println("WARN: StubRewrite tried to map " + derived + " to "+ 
                        remapped + ", which doesn't exist");
                return derived;
            } else
                return remapped;
        } else { //didn't remap entire class. What about methods?
            jq_Method replacement = methLookupTable.get(derived);
            if(replacement == null || replacement.equals(base)) {//can't map derived to base {
//                System.out.println("NOTE: NOT virtual call to " + derived + "; no replacement found");
                return derived;
            } else {
                System.out.println("NOTE: mapping virtual call to " + derived + " being redirected to " + replacement);
                return replacement;
            }
        }
    }

    //called for all other cases
    public static jq_Method maybeReplaceCallDest(jq_Method caller, jq_Method m) {
        //Don't rewrite stub calls that would become recursive.
        //this allows us to insert decorators that call the underlying method.
        jq_Method replacement = methLookupTable.get(m);
        if(replacement == null || replacement.equals(caller))
            return m;
        else return replacement;
    }
    
//    public static jq_Method maybeReplaceCallDest(jq_Method m) {
//        jq_Method replacement = maybeReplaceCallDest
//    }
    
    static Pattern linePat = Pattern.compile("([^:]*):([^@]*)@([^ ]*) ([^:]*):([^@]*)@([^ ]*)");

    static {
        init();
    }
    
    public static void init() {
        if(methLookupTable != null)
            return;
        else { 
            //we iterate over these maps in addNewDests.
            methLookupTable = new LinkedHashMap<jq_Method,jq_Method>();
            classLookupTable = new LinkedHashMap<jq_Reference, jq_Class>();
        }
        try {
            String fileNames = System.getProperty("chord.methodRemapFile");
            if(fileNames == null)
                return;
            String[] names = fileNames.split(",");
            for(String fileName:names) {
                readStubFile(fileName);
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }


    private static void readStubFile(String fileName) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        String ln = null;
        while((ln = br.readLine()) != null) {
            if(ln.length() < 1 || ln.startsWith("#"))
                continue;
            
            Matcher methRewriteMatch = linePat.matcher(ln);
            if(methRewriteMatch.find()) {
                String srcMethName = methRewriteMatch.group(1);
                String srcDesc = methRewriteMatch.group(2);
                String srcClassName = methRewriteMatch.group(3);

                String destMethName = methRewriteMatch.group(4);
                String destDesc = methRewriteMatch.group(5);
                String destClassName = methRewriteMatch.group(6);

                jq_Method src = getMeth(srcClassName, srcMethName, srcDesc);
                jq_Method dest = getMeth(destClassName, destMethName, destDesc);
                if(src != null && dest != null) {
                    //can do more checks here, for e.g., arity matching
                    methLookupTable.put(src, dest);
                    System.out.println("StubRewrite mapping "+ srcClassName + "." + srcMethName + " to " + destClassName+"."+destMethName);
                } else {
                    if(src == null)
                        System.err.println("WARN: StubRewrite failed to map "+ srcClassName + "." + srcMethName+", couldn't resolve source");
                    else
                        System.err.println("WARN: StubRewrite failed to map "+ destClassName + "." + destMethName+ " " + destDesc+" -- couldn't resolve dest");
                }
            } else {
                String[] parts = ln.split(" ");
                if(parts.length == 2) {
                    String srcClassName = parts[0];
                    String destClassName = parts[1];
                    mapClassNames(srcClassName, destClassName);
                } else
                    System.err.println("WARN: StubRewrite couldn't parse line "+ ln);
            }
        } //end while
        br.close();
    }

    /**
     * The stub methods that we intend to call need to be part of domM etc.
     * This method is called by RTA and should add all stub targets to
     * the publicMethods collection that's passed in.
     */
    public static void addNewDests(Collection<jq_Method> publicMethods) {
//        System.out.println("in StubRewrite.addNewDests");
        publicMethods.addAll(methLookupTable.values());
        for(jq_Class cl: classLookupTable.values()) {
            for(jq_Method m: cl.getDeclaredInstanceMethods()) {
//                System.out.println("StubRewrite adding method " + m.getNameAndDesc() + " added to publicMethods");
                publicMethods.add(m);
            }
        }
    }

    public static jq_Reference fakeSubtype(jq_Reference t1) {
        return classLookupTable.get(t1);
    }

}
