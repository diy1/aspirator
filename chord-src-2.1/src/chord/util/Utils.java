package chord.util;

import java.io.File;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.FileReader;

import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;

import org.scannotation.AnnotationDB;

/**
 * Commonly-used utilities.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 * @author Percy Liang (pliang@cs.berkeley.edu)
 */
public final class Utils {
    public final static String LIST_SEPARATOR = ",|:|;";
    public final static String PATH_SEPARATOR = File.pathSeparator + "|;";
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    /**
     * Just disables an instance creation of this utility class.
     *
     * @throws UnsupportedOperationException always.
     */
    private Utils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if the given objects are equal, namely, they are both null or they are equal by the {@code equals()} method.
     *
     * @param x the first compared object.
     * @param y the second compared object.
     *
     * @return true if the given objects are equal.
     */
    public static boolean areEqual(final Object x, final Object y) {
        return x == null ? y == null : x.equals(y);
    }

    /**
     * Determines whether a given collection contains duplicate values.
     *
     * @param <T> The type of the collection elements.
     * @param elements    A collection.
     *
     * @return true if the given collection contains duplicate values.
     */
    public static <T> boolean hasDuplicates(final List<T> elements) {
        int n = elements.size();
        for (int i = 0; i < n - 1; i++) {
            final T e1 = elements.get(i);
            for (int j = i + 1; j < n; j++) {
                final T e2 = elements.get(j);
                if (areEqual(e1, e2))
                    return true;
            }
        }
        return false;
    }

    /**
     * Provides a string representation of the elements in the given collection.
     *
     * @param c            A collection of elements.  It may be null.
     * @param prefix    String to be used as prefix.
     * @param sep        String to be used to separate elements in the collection.
     * @param suffix    String to be used as suffix.
     * @param <T>        The type of elements in the collection.
     *
     * @return String representation of the elements in the collection.
     */
    public static <T> String toString(Collection<T> c, String prefix, String sep, String suffix) {
        if (c == null || c.size() == 0)
            return prefix + suffix;
        Iterator<T> it = c.iterator();
        String result = prefix + it.next();
        while (it.hasNext())
            result += sep + it.next();
        return result + suffix;
    }

    public static <T> String toString(final Collection<T> a) {
        return toString(a, "", ",", "");
    }

    /**
     * Provides a string representation of the elements in the given array.
     *
     * @param <T>    The type of array elements.
     * @param array  An array of elements.  It may be null.
     * @param prefix String to be used as prefix.
     * @param sep    String to be used to separate array elements.
     * @param suffix String to be used as suffix.
     *
     * @return String representation of the elements in the array.
     */
    public static <T> String toString(T[] array, String prefix, String sep, String suffix) {
        if (array == null || array.length == 0) 
            return prefix + suffix;
        String result = prefix + array[0];
        for (int i = 1; i < array.length; i++) {
            result += sep + array[i];
        }
        return result + suffix;
    }

    /**
     * Provides a string representation of the elements in the given array.
     *
     * @param <T>   The type of the array elements.
     * @param array An array of elements.
     *
     * @return string representation of elements in given array.
     */
    public static <T> String toString(final T[] array) {
        return toString(array, "", ",", "");
    }

    public static <T> String join(List<T> objs, String delim) {
        if (objs == null) return "";
        return join(objs, delim, 0, objs.size());
    }

    public static <T> String join(List<T> objs, String delim, int start, int end) {
        if (objs == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = start; i < end; i++) {
            if (!first) sb.append(delim);
            sb.append(objs.get(i));
            first = false;
        }
        return sb.toString();
    }

    public static String[] toArray(String str) {
        return str.equals("") ? new String[0] : str.split(LIST_SEPARATOR);
    }

    public static String concat(String s1, String sep, String s2) {
        if (s1.equals("")) return s2;
        if (s2.equals("")) return s1;
        return s1 + sep + s2;
    }

    public static int[] samplePermutation(Random random, int n) {
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        for (int i = 0; i < n-1; i++) {
            int j = i+random.nextInt(n-i);
            int tmp = perm[i]; perm[i] = perm[j]; perm[j] = tmp; // Swap
        }
        return perm;
    }

    public static <S, T> void add(Map<S, List<T>> map, S key1, T key2) {
        List<T> s = map.get(key1);
        if (s == null) map.put(key1, s = new ArrayList<T>());
        s.add(key2);
    }

    /**
     * Checks a string against a set of prefixes and returns true iff the string starts with one of the prefixes.
     *
     * @param str a string
     * @param prefixes an array of prefixes
     *
     * @return true iff the string starts with one of the prefixes.
     */
    public static boolean prefixMatch(String str, String[] prefixes) {
        for (String prefix : prefixes) {
            if (str.startsWith(prefix))
                return true;
        }
        return false;
    }

    /**
     * Determines whether a given class is a subclass of another.
     *
     * @param subclass    An intended subclass.
     * @param superclass  An intended superclass.
     *
     * @return {@code true} iff class {@code subclass} is a subclass of class <tt>superclass</tt>.
     */
    public static boolean isSubclass(final Class subclass, final Class superclass) {
        try {
            subclass.asSubclass(superclass);
        } catch (final ClassCastException ex) {
            return false;
        }
        return true;
    }

    public static InputStream getResourceAsStream(String resName) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl.getResourceAsStream(resName);
    }

    public static BufferedReader getResourceAsReader(String resName) {
        InputStream is = getResourceAsStream(resName);
        return (is == null) ? null : new BufferedReader(new InputStreamReader(is));
    }

    public static Set<String> getClassNames(final String classPath) {
        if (classPath == null) {
            throw new IllegalArgumentException();
        }
        final List<URL> list = new ArrayList<URL>();
        for (final String fileName : classPath.split(PATH_SEPARATOR)) {
            final File file = new File(fileName);
            if (!file.exists()) {
                System.out.println("WARNING: Ignoring: " + fileName);
                continue;
            }
            try {
                list.add(file.toURL());
            } catch (final MalformedURLException ex) {
                throw new RuntimeException(ex);
            }
        }
        final AnnotationDB db = new AnnotationDB();
        db.setIgnoredPackages(EMPTY_STRING_ARRAY);
        try {
            db.scanArchives(list.toArray(new URL[list.size()]));
        } catch (final IOException ex) {
            throw new RuntimeException(ex);
        }
        return db.getClassIndex().keySet();
    }

    /**
     * Trim the numerical suffix of the given string.  For instance, converts "abc123xyz456" to "abc123xyz". 
     * If given string is empty, also returns an empty string.
     *
     * @param s The string whose numerical suffix is to be trimmed.
     *
     * @return A copy of the given string without any numerical suffix.
     *
     * @throws IllegalArgumentException if {@code s} is {@code null}.
     */
    public static String trimNumSuffix(final String s) {
        if (s == null) {
            throw new IllegalArgumentException();
        }
        if (s.length() == 0) {
            return s;
        }
        int i = s.length() - 1;
        while (Character.isDigit(s.charAt(i))) {
            i--;
        }
        return s.substring(0, i + 1);
    }

    /**
     * Create an array of strings by concatenating two given arrays of strings.
     *
     * @param a The first array of strings.
     * @param b The second array of strings.
     *
     * @return A new array of strings containing those in {@code a} followed by those in {@code b}.
     *
     * @throws IllegalArgumentException if any of arguments is {@code null}.
     */
    public static String[] concat(final String[] a, final String[] b) {
        if (a == null) {
            throw new IllegalArgumentException();
        }
        if (b == null) {
            throw new IllegalArgumentException();
        }
        final String[] result = new String[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    public static List<String> tokenize(String s) {
        StringTokenizer st = new StringTokenizer(s);
        List<String> l = new ArrayList<String>(st.countTokens());
        for (int i = 0; st.hasMoreTokens(); i++)
            l.add(st.nextToken());
        return l;
    }

    /**
     * Determines whether a given array contains a given value.
     *
     * @param <T>   The type of the array elements and the value to be checked for containment in the array.
     * @param array An array.
     * @param s     A value to be checked for containment in the given array.
     *
     * @return true iff the given array contains the given value.
     */
    public static <T> boolean contains(final T[] array, final T s) {
        if (array == null) {
            throw new IllegalArgumentException();
        }
        for (final T t : array) {
            if (t == null) {
                if (s == null) {
                    return true;
                }
            } else if (t.equals(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether a given array contains duplicate values.
     *
     * @param <T>   The type of the array elements.
     * @param array An array.
     *
     * @return true iff the given array contains duplicate values.
     *
     * @throws IllegalArgumentException if {@code array} is {@code null}.
     */
    public static <T> boolean hasDuplicates(final T[] array) {
        if (array == null) {
            throw new IllegalArgumentException();
        }
        for (int i = 0; i < array.length - 1; i++) {
            final T x = array[i];
            for (int j = i + 1; j < array.length; j++) {
                final T y = array[j];
                if (x.equals(y)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static PrintWriter openOut(String path) {
        try {
            return new PrintWriter(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static PrintWriter openOutAppend(String path) {
        try {
            return new PrintWriter(new FileOutputStream(path, true));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getAbsolutePath(String parent, String child) {
        return (new File(parent, child)).getAbsolutePath();
    }

    public static void copy(String fromFileName, String toFileName) {
        try {
            FileInputStream fis = new FileInputStream(fromFileName);
            FileOutputStream fos = new FileOutputStream(toFileName);
            byte[] buf = new byte[1024];
            int i = 0;
            while((i = fis.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
            fis.close();
            fos.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static boolean mkdirs(String dirName) {
        return mkdirs(new File(dirName));
    }

    public static boolean mkdirs(String parentName, String childName) {
        return mkdirs(new File(parentName, childName));
    }

    public static boolean mkdirs(File file) {
        if (file.exists()) {
            if (!file.isDirectory()) {
                throw new RuntimeException("File '" + file + "' is not a directory.");
            }
            return false;
        }
        if (file.mkdirs())
            return true;
        throw new RuntimeException("Failed to create directory '" + file + "'");
    }

    public static Object readSerialFile(String serialFileName) {
        try {
            FileInputStream fs = new FileInputStream(serialFileName);
            ObjectInputStream os = new ObjectInputStream(fs);
            Object o = os.readObject();
            os.close();
            return o;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void writeSerialFile(Object o, String serialFileName) {
        try {
            FileOutputStream fs = new FileOutputStream(serialFileName);
            ObjectOutputStream os = new ObjectOutputStream(fs);
            os.writeObject(o);
            os.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void readFileToList(String fileName, List<String> list) {
        readFileToList(new File(fileName), list);
    }

    public static void readFileToList(File file, List<String> list) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            String s;
            while ((s = in.readLine()) != null) {
                list.add(s);
            }
            in.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static List<String> readFileToList(String fileName) {
        return readFileToList(new File(fileName));
    }

    public static List<String> readFileToList(File file) {
        List<String> list = new ArrayList<String>();
        readFileToList(file, list);
        return list;
    }

    public static IndexMap<String> readFileToMap(String fileName) {
        return readFileToMap(new File(fileName));
    }

    public static IndexMap<String> readFileToMap(File file) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            IndexMap<String> map = new IndexMap<String>();
            String s;
            while ((s = in.readLine()) != null) {
                map.getOrAdd(s);
            }
            in.close();
            return map;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void writeListToFile(List<String> list, String fileName) {
        writeListToFile(list, new File(fileName));
    }

    public static void writeListToFile(List<String> list, File file) {
        try {
            PrintWriter out = new PrintWriter(file);
            for (String s : list) {
                out.println(s);
            }
            out.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void writeMapToFile(IndexMap<String> map, String fileName) {
        writeMapToFile(map, new File(fileName));
    }

    public static void writeMapToFile(IndexMap<String> map, File file) {
        try {
            PrintWriter out = new PrintWriter(file);
            for (String s : map) {
                out.println(s);
            }
            out.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void deleteFile(String fileName) {
        deleteFile(new File(fileName));
    }

    public static void deleteFile(File file) {
        if (file.exists())
            delete(file);
    }

    // file is assumed to exist
    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File file2 : files)
                delete(file2);
        }
        if (!file.delete())
            throw new RuntimeException("Failed to delete file: " + file);
    }

    public static boolean exists(String fileName) {
        return (new File(fileName)).exists();
    }

    public static boolean buildBoolProperty(String propName, boolean defaultVal) {
        return System.getProperty(propName, Boolean.toString(defaultVal)).equals("true");
    }
    
    public static String[] split(String s, String sep, boolean trimWhiteSpace, boolean noEmptyString, int limit) {
        if (trimWhiteSpace == false && noEmptyString == false) {
            return s.split(sep, limit);
        } else if (trimWhiteSpace == false && noEmptyString == true) {
            String[] sArr = s.split("^(\\Q"+sep+"\\E)+");
            if (sArr.length == 1 && sArr[0].length() == 0)
                return (new String[0]);
            else {
                return (sArr[sArr.length - 1].split("(\\Q"+sep+"\\E)+", limit));
            }
        } else if (trimWhiteSpace == true && noEmptyString == false) {
            return (s.split("\\s*\\Q"+sep+"\\E\\s*", limit));
        } else {
            String[] sArr = s.split("^(\\s*\\Q"+sep+"\\E\\s*)+");
            if (sArr.length == 1 && sArr[0].length() == 0)
                return (new String[0]);
            else
                return (sArr[sArr.length - 1].split("(\\s*\\Q"+sep+"\\E\\s*)+", limit));
        }
        
    }
    
    public static String htmlEscape(String input) {
        HashMap<Character, String> replacements = new HashMap<Character, String>();
        replacements.put('&', "&amp;");
        replacements.put('>', "&gt;");
        replacements.put('<', "&lt;");
        replacements.put('\'', "&apos;");
        replacements.put('\"', "&quot;");
        replacements.put(' ', "&nbsp;");
        
        StringBuilder sb = new StringBuilder(input.length());
        for (char c: input.toCharArray()) {
            if (replacements.containsKey(c)) {
                sb.append(replacements.get(c));
            } else {
                sb.append(c);
            }
        }
        
        return sb.toString();
        
//        return input.replace("&", "&amp;").replace(">", "&gt;").replace("<", "&lt;")
//            .replace("'", "&apos;").replaceAll("\"", "&quot;");
    }
}
