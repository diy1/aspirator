package chord.bddbddb;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import chord.project.Config;

import chord.util.tuple.integer.*;

import chord.util.tuple.object.*;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDDomain;
import net.sf.javabdd.BDDException;
import net.sf.javabdd.BDDFactory;

/**
 * Generic implementation of a BDD-based relation.
 * <p>
 * Typical usage is as follows:
 * <ul>
 *   <li>
 *    The relation is initialized by calling the following:
 *       <ul>
 *         <li>{@link #setName(String)}, which sets the name of the relation,</li>
 *         <li>{@link #setSign(RelSign)}, which sets the signature of the relation, and</li>
 *         <li>{@link #setDoms(Dom[])}, which sets the domains of the relation.</li>
 *       </ul>
 *   </li>
 *   <li>
 *    The relation is next built either on disk, by executing a Datalog analysis that declares the
 *       relation as an output relation, or in memory, by executing the following sequence of operations:
 *       <ul>
 *         <li>calling {@link #zero()} or {@link #one()} which initializes the relation in memory to an
 *             empty or full one, respectively;</li>
 *         <li>repeatedly calling {@link #add(int[])} or {@link #add(Object[])} with the argument in each
 *             call being a tuple to be added to the relation in memory.  If the tuple already exists in
 *             the relation then the call does not have any effect.</li>
 *       </ul>
 *   </li>
 *   <li>
 *    The relation built in memory is reflected onto disk by calling {@link #save(String)}, which also
 *       removes the relation from memory (i.e., BDDs allocated for the relation in memory are freed).
 *   </li>
 *   <li>
 *    The relation on disk can be read by a Datalog analysis that declares it as an input relation.
 *   </li>
 *   <li>
 *    The relation on disk can also be read by first calling {@link #load(String)}, which loads the
 *       relation from disk into memory, and then calling any of the following:
 *       <ul>
 *         <li>{@link #size()}, which provides the number of tuples in the relation in memory, and</li>
 *         <li>{@link #getView()}, which provides an immutable view of the relation in memory;
 *             operations such as selection and projection can be performed on the view.</li>
 *       </ul>
 *   </li>
 *   <li>
 *    The relation can be removed from memory by calling {@link #close()}.
 *   </li>
 * </ul>
 * <p>
 * Note: Much of the BDD-related code in this class is adapted from bddbddb.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Rel {
    protected String name;
    protected RelSign sign;
    protected Dom[] doms;
    protected int numDoms;
    protected BDDFactory factory;
    protected int[] domIdxs;
    protected BDDDomain[] domBdds;
    protected BDD bdd;
    protected BDD iterBdd;
    /**
     * Sets the name of this relation.
     * 
     * The name must be set only once.
     * 
     * @param name The name of this relation.
     */
    public void setName(String name) {
        assert (name != null);
        assert (this.name == null);
        this.name = name;
    }
    /**
     * Provides the name of this relation.
     * 
     * @return The name of this relation.
     */
    public String getName() {
        return name;
    }
    /**
     * Sets the signature of this relation.
     * 
     * The signature must be set only once.
     * 
     * @param domNames An ordered list of comma-separated domain names of this relation.
     * @param domOrder The BDD ordering of the domain names.
     */
    public void setSign(String domNames, String domOrder) {
        setSign(domNames.split(","), domOrder);
    }
    /**
     * Sets the signature of this relation.
     * 
     * The signature must be set only once.
     *
     * @param domNames An ordered list of domain names of this relation.
     * @param domOrder The BDD ordering of the domain names.
     */
    public void setSign(String[] domNames, String domOrder) {
        setSign(new RelSign(domNames, domOrder));
    }
    /**
     * Sets the signature of this relation.
     * 
     * The signature must be set only once.
     * 
     * @param sign The signature of this relation.
     */
    public void setSign(RelSign sign) {
        assert (this.sign == null);
        assert (sign != null);
        assert (sign.val1 != null);
        this.sign = sign;
        numDoms = sign.val0.length;
    }
    /**
     * Provides the signature of this relation.
     * 
     * @return The signature of this relation.
     */
    public RelSign getSign() {
        return sign;
    }
    public Dom[] getDoms() {
        return doms;
    }
    /**
     * Sets the domains of this relation.
     * 
     * The domains must be set only once.
     * <p>
     * The signature of the relation must be set before the domains are set.
     * <p>
     * The contents of the domains need not be uptodate when this method is called.
     * The contents of the domains are used only when the contents of this relation
     * are initialized (by calling one of methods {@link #one()}, {@link #zero()},
     * and {@link #load(String)}).
     * 
     * @param doms The domains of this relation.
     */
    public void setDoms(Dom[] doms) {
        assert (this.sign != null);
        assert (numDoms == doms.length);
        this.doms = doms;
    }
    protected void initialize() {
        if (doms == null)
            throw new RuntimeException("");
        int bddnodes = Integer.parseInt(
            System.getProperty("bddnodes", "500000"));
        int bddcache = Integer.parseInt(
            System.getProperty("bddcache", "125000"));
        double bddminfree = Double.parseDouble(
            System.getProperty("bddminfree", ".20"));
        // Note: Do not change the argument "java" below to "buddy".
        // We require a separate BDD factory for each relation in Chord,
        // for modularity purposes.  We also require the ability for
        // multiple such factories to be active simultaneously.  But
        // BuDDyFactory, the factory of choice (since it is more
        // efficient and is used for solving Datalog analyses in Chord)
        // allows at most one instance of itself to be active at a time.
        // Hence, we need to use JFactory here instead, which allows
        // multiple instances of itself be active simultaneously.
        factory = BDDFactory.init("java", bddnodes, bddcache);
        factory.setVerbose(Config.verbose);
        factory.setIncreaseFactor(2);
        factory.setMinFreeNodes(bddminfree);
        domBdds = new BDDDomain[numDoms];
        String[] domNames = sign.val0;
        for (int i = 0; i < numDoms; i++) {
            String name = domNames[i];
            int numElems = doms[i].size();
            if (numElems == 0)
                numElems = 1;
            BDDDomain d = factory.extDomain(new long[] { numElems })[0];
            d.setName(name);
            domBdds[i] = d;
        }
        boolean reverseLocal = System.getProperty("bddreverse","true").equals("true");
        int[] order = factory.makeVarOrdering(reverseLocal, sign.val1);
        factory.setVarOrder(order);
        domIdxs = new int[numDoms];
        iterBdd = factory.one();
        for (int i = 0; i < numDoms; i++) {
            BDDDomain domBdd = domBdds[i]; 
            domIdxs[i] = domBdd.getIndex();
            iterBdd = iterBdd.andWith(domBdd.set());
        }
    }
    /**
     * Sets this relation in memory to the full relation (containing all tuples).
     */
    public void one() {
        initialize();
        bdd = factory.one();
    }
    /**
     * Sets this relation in memory to the empty relation (containing no tuples).
     */
    public void zero() {
        initialize();
        bdd = factory.zero();
    }
    /**
     * Copies this relation from disk to memory.
     */
    public void load(String dirName) {
        initialize();
        try {
            File file = new File(dirName, name + ".bdd");
            BufferedReader in = new BufferedReader(new FileReader(file));
            {
                String s = in.readLine();
                assert (s != null && s.startsWith("#"));
                StringTokenizer st = new StringTokenizer(s.substring(2));
                for (int i = 0; i < numDoms; i++) {
                    String dname = st.nextToken(": ");
                    int dbits = Integer.parseInt(st.nextToken());
                    BDDDomain d = domBdds[i];
                    System.out.println("Ding: Rel.java.load: d: "+d.getName());
                    assert (d.getName().equals(dname));
                    assert (d.varNum() == dbits);
                }
                assert (!st.hasMoreTokens());
            }
            int[] map = null;
            for (BDDDomain d : domBdds) {
                String s = in.readLine();
                assert (s != null && s.startsWith("#"));
                StringTokenizer st = new StringTokenizer(s.substring(2));
                int[] vars = d.vars();
                for (int j = 0; j < vars.length; ++j) {
                    int k = Integer.parseInt(st.nextToken());
                    if (vars[j] == k)
                        continue;
                    if (k >= factory.varNum())
                        factory.setVarNum(k + 1);
                    if (map == null || map.length < factory.varNum()) {
                        int[] t = new int[factory.varNum()];
                        for (int x = 0; x < t.length; x++)
                            t[x] = x;
                        if (map != null)
                            System.arraycopy(map, 0, t, 0, map.length);
                        map = t;
                    }
                    map[k] = vars[j];
                }
                assert (!st.hasMoreTokens());
            }
            bdd = factory.load(in, map);
            in.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    /**
     * Frees this relation from memory.
     */
    public void close() {
        if (bdd == null)
            throw new RuntimeException("");
        factory.done();
        bdd = null;
    }
    /**
     * Copies the relation from memory to disk and frees it from memory.
     */
    public void save(String dirName) {
        if (bdd == null)
            throw new RuntimeException("");
        try {
            File file = new File(dirName, name + ".bdd");
            BufferedWriter out = new BufferedWriter(new FileWriter(file));
            out.write('#');
            for (BDDDomain d : domBdds)
                out.write(" " + d + ":" + d.varNum());
            out.write('\n');
            for (BDDDomain d : domBdds) {
                out.write('#');
                for (int v : d.vars())
                    out.write(" " + v);
                out.write('\n');
            }
            factory.save(out, bdd);
            out.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        close();
    }
    public void print(String dirName) {
        if (bdd == null)
            throw new RuntimeException("");
        try {
            File file = new File(dirName, name + ".txt");
            PrintWriter out = new PrintWriter(new FileWriter(file));
            AryNIterable tuples = getAryNValTuples();
            int n = doms.length;
            for (Object[] tuple : tuples) {
                String s = "<";
                for (int i = 0; i < n; i++) {
                    Object o = tuple[i];
                    s += doms[i].toUniqueString(o);
                    if (i < n - 1)
                        s += ",";
                }
                s += ">";
                out.println(s);
            }
            out.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        close();
    }
    private BDD makeIterBdd(boolean[] keptDoms) {
        BDD iterBdd = factory.one();
        for (int i = 0; i < keptDoms.length; i++) {
            if (keptDoms[i]) {
                iterBdd.andWith(domBdds[i].set());
            }
        }
        return iterBdd;
    }
    /**
     * An immutable view of a relation.
     */
    public class RelView {
        private final BDD b;
        private final boolean[] keptDoms;
        public RelView() {
            if (bdd == null)
                throw new RuntimeException("");
            b = bdd.id();
            keptDoms = new boolean[numDoms];
            for (int i = 0; i < numDoms; i++)
                keptDoms[i] = true;
        }
        private int getNextDomIdx(int idx) {
            for (int i = idx + 1; i < keptDoms.length; i++) {
                if (keptDoms[i])
                    return i;
            }
            return -1;
        }
        /**
         * Provides the number of tuples in this view.
         * 
         * @return The number of tuples in this view.
         */
        public int size() {
            return (int) b.satCount(makeIterBdd(keptDoms));
        }
        /**
         * Determines whether this view contains the specified 1-tuple.
         * 
         * @param <T> The type of the lone object in the 1-tuple.
         * @param val The lone object in the 1-tuple.
         * 
         * @return true iff this view contains the specified tuple.
         */
        public <T> boolean contains(T val) {
            int domIdx = getNextDomIdx(-1);
            int idx = doms[domIdx].indexOf(val);
            try {
                return !b.id().andWith(
                    domBdds[domIdx].ithVar(idx)).isZero();
            } catch (BDDException ex) {
                checkRange(idx, domIdx);
                throw new RuntimeException(ex);
            }
        }
        /**
         * Determines whether this view contains the specified 2-tuple.
         * 
         * @param <T0> The type of the 0th object in the 2-tuple.
         * @param <T1> The type of the 1st object in the 2-tuple.
         * @param val0 The 0th object in the 2-tuple.
         * @param val1 The 1st object in the 2-tuple.
         * 
         * @return true iff this view contains the specified tuple.
         */
        public <T0, T1> boolean contains(T0 val0, T1 val1) {
            int domIdx0 = getNextDomIdx(-1);
            int domIdx1 = getNextDomIdx(domIdx0);
            int idx0 = doms[domIdx0].indexOf(val0);
            int idx1 = doms[domIdx1].indexOf(val1);
            try {
                return !b.id().andWith(
                    domBdds[domIdx0].ithVar(idx0).andWith(
                    domBdds[domIdx1].ithVar(idx1)
                    )).isZero();
            } catch (BDDException ex) {
                checkRange(idx0, domIdx0);
                checkRange(idx1, domIdx1);
                throw new RuntimeException(ex);
            }
        }
        /**
         * Determines whether this view contains the specified
         * 3-tuple.
         * 
         * @param <T0> The type of the 0th object in the 3-tuple.
         * @param <T1> The type of the 1st object in the 3-tuple.
         * @param <T2> The type of the 2nd object in the 3-tuple.
         * @param val0 The 0th object in the 3-tuple.
         * @param val1 The 1st object in the 3-tuple.
         * @param val2 The 2nd object in the 3-tuple.
         * 
         * @return true iff this view contains the specified tuple.
         */
        public <T0, T1, T2> boolean contains(T0 val0, T1 val1, T2 val2) {
            int domIdx0 = getNextDomIdx(-1);
            int domIdx1 = getNextDomIdx(domIdx0);
            int domIdx2 = getNextDomIdx(domIdx1);
            int idx0 = doms[domIdx0].indexOf(val0);
            int idx1 = doms[domIdx1].indexOf(val1);
            int idx2 = doms[domIdx2].indexOf(val2);
            try {
                return !b.id().andWith(
                    domBdds[domIdx0].ithVar(idx0).andWith(
                    domBdds[domIdx1].ithVar(idx1).andWith(
                    domBdds[domIdx2].ithVar(idx2)
                    ))).isZero();
            } catch (BDDException ex) {
                checkRange(idx0, domIdx0);
                checkRange(idx1, domIdx1);
                checkRange(idx2, domIdx2);
                throw new RuntimeException(ex);
            }
        }
        /**
         * Determines whether this view contains the specified
         * 4-tuple.
         * 
         * @param <T0> The type of the 0th object in the 4-tuple.
         * @param <T1> The type of the 1st object in the 4-tuple.
         * @param <T2> The type of the 2nd object in the 4-tuple.
         * @param <T3> The type of the 3rd object in the 4-tuple.
         * @param val0 The 0th object in the 4-tuple.
         * @param val1 The 1st object in the 4-tuple.
         * @param val2 The 2nd object in the 4-tuple.
         * @param val3 The 3rd object in the 4-tuple.
         * 
         * @return true iff this view contains the specified tuple.
         */
        public <T0, T1, T2, T3> boolean contains(T0 val0, T1 val1, T2 val2, T3 val3) {
            int domIdx0 = getNextDomIdx(-1);
            int domIdx1 = getNextDomIdx(domIdx0);
            int domIdx2 = getNextDomIdx(domIdx1);
            int domIdx3 = getNextDomIdx(domIdx2);
            int idx0 = doms[domIdx0].indexOf(val0);
            int idx1 = doms[domIdx1].indexOf(val1);
            int idx2 = doms[domIdx2].indexOf(val2);
            int idx3 = doms[domIdx3].indexOf(val3);
            try {
                return !b.id().andWith(
                    domBdds[domIdx0].ithVar(idx0).andWith(
                    domBdds[domIdx1].ithVar(idx1).andWith(
                    domBdds[domIdx2].ithVar(idx2).andWith(
                    domBdds[domIdx3].ithVar(idx3)
                    )))).isZero();
            } catch (BDDException ex) {
                checkRange(idx0, domIdx0);
                checkRange(idx1, domIdx1);
                checkRange(idx2, domIdx2);
                checkRange(idx3, domIdx3);
                throw new RuntimeException(ex);
            }
        }
        public <T0, T1, T2, T3, T4> boolean contains(T0 val0, T1 val1, T2 val2, T3 val3, T4 val4) {
            int domIdx0 = getNextDomIdx(-1);
            int domIdx1 = getNextDomIdx(domIdx0);
            int domIdx2 = getNextDomIdx(domIdx1);
            int domIdx3 = getNextDomIdx(domIdx2);
            int domIdx4 = getNextDomIdx(domIdx3);
            int idx0 = doms[domIdx0].indexOf(val0);
            int idx1 = doms[domIdx1].indexOf(val1);
            int idx2 = doms[domIdx2].indexOf(val2);
            int idx3 = doms[domIdx3].indexOf(val3);
            int idx4 = doms[domIdx4].indexOf(val4);
            try {
                return !b.id().andWith(
                    domBdds[domIdx0].ithVar(idx0).andWith(
                    domBdds[domIdx1].ithVar(idx1).andWith(
                    domBdds[domIdx2].ithVar(idx2).andWith(
                    domBdds[domIdx3].ithVar(idx3).andWith(
                    domBdds[domIdx4].ithVar(idx4)
                    ))))).isZero();
            } catch (BDDException ex) {
                checkRange(idx0, domIdx0);
                checkRange(idx1, domIdx1);
                checkRange(idx2, domIdx2);
                checkRange(idx3, domIdx3);
                checkRange(idx4, domIdx4);
                throw new RuntimeException(ex);
            }
        }
        public <T0, T1, T2, T3, T4, T5> boolean contains(T0 val0, T1 val1, T2 val2, T3 val3, T4 val4, T5 val5) {
            int domIdx0 = getNextDomIdx(-1);
            int domIdx1 = getNextDomIdx(domIdx0);
            int domIdx2 = getNextDomIdx(domIdx1);
            int domIdx3 = getNextDomIdx(domIdx2);
            int domIdx4 = getNextDomIdx(domIdx3);
            int domIdx5 = getNextDomIdx(domIdx4);
            int idx0 = doms[domIdx0].indexOf(val0);
            int idx1 = doms[domIdx1].indexOf(val1);
            int idx2 = doms[domIdx2].indexOf(val2);
            int idx3 = doms[domIdx3].indexOf(val3);
            int idx4 = doms[domIdx4].indexOf(val4);
            int idx5 = doms[domIdx5].indexOf(val5);
            try {
                return !b.id().andWith(
                    domBdds[domIdx0].ithVar(idx0).andWith(
                    domBdds[domIdx1].ithVar(idx1).andWith(
                    domBdds[domIdx2].ithVar(idx2).andWith(
                    domBdds[domIdx3].ithVar(idx3).andWith(
                    domBdds[domIdx4].ithVar(idx4).andWith(
                    domBdds[domIdx5].ithVar(idx5)
                    )))))).isZero();
            } catch (BDDException ex) {
                checkRange(idx0, domIdx0);
                checkRange(idx1, domIdx1);
                checkRange(idx2, domIdx2);
                checkRange(idx3, domIdx3);
                checkRange(idx4, domIdx4);
                checkRange(idx5, domIdx5);
                throw new RuntimeException(ex);
            }
        }
        public boolean contains(Object[] vals) {
            throw new UnsupportedOperationException();
        }
        public <T> Iterable<T> getAry1ValTuples() {
            return new SelfIterable<T>(b, keptDoms);
        }
        public <T0, T1> PairIterable<T0, T1> getAry2ValTuples() {
            return new PairIterable<T0,T1>(b, keptDoms);
        }
        public <T0, T1, T2> TrioIterable<T0, T1, T2> getAry3ValTuples() {
            return new TrioIterable<T0, T1, T2>(b, keptDoms);
        }
        public <T0,T1,T2,T3> QuadIterable<T0,T1,T2,T3> getAry4ValTuples() {
            return new QuadIterable<T0,T1,T2,T3>(b, keptDoms);
        }
        public <T0,T1,T2,T3,T4> PentIterable<T0,T1,T2,T3,T4> getAry5ValTuples() {
            return new PentIterable<T0,T1,T2,T3,T4>(b, keptDoms);
        }
        public <T0,T1,T2,T3,T4,T5> HextIterable<T0,T1,T2,T3,T4,T5> getAry6ValTuples() {
            return new HextIterable<T0,T1,T2,T3,T4,T5>(b, keptDoms);
        }
        public AryNIterable getAryNValTuples() {
            return new AryNIterable(b, keptDoms);
        }
        /**
         * Frees this view.
         */
        public void free() {
            b.free();
        }
        public void select(int domIdx, Object val) {
            assert (keptDoms[domIdx]);
            try {
                int idx = doms[domIdx].indexOf(val);
                b.restrictWith(domBdds[domIdx].ithVar(idx));
            } catch (BDDException ex) {
                checkRange(val, domIdx);
            }
        }
        public void select(int domIdx, int idx) {
            assert (keptDoms[domIdx]);
            try {
                b.restrictWith(domBdds[domIdx].ithVar(idx));
            } catch (BDDException ex) {
                checkRange(idx, domIdx);
            }
        }
        public void delete(int domIdx) {
            assert (keptDoms[domIdx]);
            b.exist(domBdds[domIdx].set());
            keptDoms[domIdx] = false;
        }
        public void selectAndDelete(int domIdx, Object val) {
            assert (keptDoms[domIdx]);
            try {
                int idx = doms[domIdx].indexOf(val);
                b.restrictWith(domBdds[domIdx].ithVar(idx));
                b.exist(domBdds[domIdx].set());
                keptDoms[domIdx] = false;
            } catch (BDDException ex) {
                checkRange(val, domIdx);
            }
        }
        public void selectAndDelete(int domIdx, int idx) {
            assert (keptDoms[domIdx]);
            try {
                b.restrictWith(domBdds[domIdx].ithVar(idx));
                b.exist(domBdds[domIdx].set());
                keptDoms[domIdx] = false;
            } catch (BDDException ex) {
                checkRange(idx, domIdx);
            }
        }
    };
    /**
     * Provides a fresh view of the relation.
     * 
     * @return A fresh view of the relation.
     */
    public RelView getView() {
        return new RelView();
    }
    protected void checkRange(Object val, int domIdx) {
        int idx = doms[domIdx].indexOf(val);
        if (idx == -1)
            throw new RuntimeException("Cannot find value '" + val +
                "' in domain #" + domIdx + " named '" + doms[domIdx] +
                "' in relation named '" + name + "'.");
        int size = doms[domIdx].size();
        if (idx >= size) {
            throw new RuntimeException("Object " + val + " has out of range index " + idx +
                " in domain #" + domIdx + " named '" + doms[domIdx] + "' of size " + size +
                " in relation named '" + name + "'.");
        }
    }
    protected void checkRange(int idx, int domIdx) {
        if (idx == -1)
            throw new RuntimeException("Cannot find value" +
                " in domain #" + domIdx + " named '" + doms[domIdx] +
                "' in relation named '" + name + "'.");
        int size = doms[domIdx].size();
        if (idx >= size) {
            throw new RuntimeException("Value has out of range index " + idx +
                " in domain #" + domIdx + " named '" + doms[domIdx] + "' of size " + size +
                " in relation named '" + name + "'.");
        }
    }
    /**
     * Determines whether the relation in memory is initialized.
     * 
     * @return true iff the relation in memory is initialized.
     */
    public boolean isOpen() {
        return bdd != null;
    }
    /**
     * Provides the size of the relation.
     * 
     * @return The size of the relation.
     */
    public int size() {
        if (bdd == null)
            throw new RuntimeException("");
        return (int) bdd.satCount(iterBdd);
    }
    /**
     * Iterator that returns all satisfying assignments as byte arrays.
     * In the byte arrays, -1 means dont-care, 0 means 0, and 1 means 1.
     */
    private class AllSatIterator implements Iterator {
        protected LinkedList loStack, hiStack;
        protected byte[] allsatProfile;
        protected final boolean useLevel;
        /**
         * Constructs a satisfying-assignment iterator on the given BDD.
         * next() returns a byte array indexed by BDD variable number.
         */
        public AllSatIterator() {
            this(bdd, false);
        }
        
        /**
         * Constructs a satisfying-assignment iterator on the given BDD.
         * If lev is true, next() will returns a byte array indexed by level.
         * If lev is false, the byte array will be indexed by BDD variable number.
         * 
         * @param r    BDD to iterate over
         * @param lev  Whether to index byte array by level instead of var
         */
        public AllSatIterator(BDD r, boolean lev) {
            useLevel = lev;
            if (r.isZero()) return;
            allsatProfile = new byte[factory.varNum()];
            Arrays.fill(allsatProfile, (byte) -1);
            loStack = new LinkedList();
            hiStack = new LinkedList();
            if (!r.isOne()) {
                loStack.addLast(r.id());
                if (!gotoNext()) allsatProfile = null;
            }
        }
        
        private boolean gotoNext() {
            BDD r;
            for (;;) {
                boolean lo_empty = loStack.isEmpty();
                if (lo_empty) {
                    if (hiStack.isEmpty()) {
                        return false;
                    }
                    r = (BDD) hiStack.removeLast();
                } else {
                    r = (BDD) loStack.removeLast();
                }
                int LEVEL_r = r.level();
                allsatProfile[useLevel?LEVEL_r:factory.level2Var(LEVEL_r)] =
                    lo_empty ? (byte)1 : (byte)0;
                BDD rn = lo_empty ? r.high() : r.low();
                for (int v = rn.level() - 1; v > LEVEL_r; --v) {
                    allsatProfile[useLevel?v:factory.level2Var(v)] = -1;
                }
                if (!lo_empty) {
                    hiStack.addLast(r);
                } else {
                    r.free();
                }
                if (rn.isOne()) {
                    rn.free();
                    return true;
                }
                if (rn.isZero()) {
                    rn.free();
                    continue;
                }
                loStack.addLast(rn);
            }
        }
        public boolean hasNext() {
            return allsatProfile != null;
        }
        public byte[] nextSat() {
            if (allsatProfile == null)
                throw new NoSuchElementException();
            byte[] b = new byte[allsatProfile.length];
            System.arraycopy(allsatProfile, 0, b, 0, b.length);
            if (!gotoNext()) allsatProfile = null;
            return b;
        }
        public Object next() {
            return nextSat();
        }
        public void remove() {
        }
    }

    private class BDDIterator implements Iterator {
        final AllSatIterator i;
        // Reference to the initial BDD object, used to support the remove() operation.
        final BDD initialBDD;
        // List of levels that we care about.
        final int[] v;
        // Current bit assignment, indexed by indices of v.
        final boolean[] b;
        // Latest result from allsat iterator.
        byte[] a;
        // Last BDD returned.  Used to support the remove() operation.
        BDD lastReturned;

        /**
         * Construct a new BDDIterator on the given BDD.
         * The var argument is the set of variables that will be mentioned in the result.
         * 
         * @param bdd BDD to iterate over
         * @param var variable set to mention in result
         */
        public BDDIterator(BDD bdd, BDD var) {
            initialBDD = bdd;
            i = new AllSatIterator(bdd, true);
            // init v[]
            int n = 0;
            BDD p = var.id();
            while (!p.isOne()) {
                ++n;
                BDD q = p.high();
                p.free();
                p = q;
            }
            p.free();
            v = new int[n];
            n = 0;
            p = var.id();
            while (!p.isOne()) {
                v[n++] = p.level();
                BDD q = p.high();
                p.free();
                p = q;
            }
            p.free();
            // init b[]
            b = new boolean[n];
            gotoNext();
        }
        
        protected void gotoNext() {
            if (i.hasNext()) {
                a = (byte[]) i.next();
            } else {
                a = null;
                return;
            }
            for (int i = 0; i < v.length; ++i) {
                int vi = v[i];
                if (a[vi] == 1) b[i] = true;
                else b[i] = false;
            }
        }
        
        protected boolean gotoNextA() {
            for (int i = v.length-1; i >= 0; --i) {
                int vi = v[i];
                if (a[vi] != -1) continue;
                if (b[i] == false) {
                    b[i] = true;
                    return true;
                }
                b[i] = false;
            }
            return false;
        }
        
        /* (non-Javadoc)
         * @see java.util.Iterator#hasNext()
         */
        public boolean hasNext() {
            return a != null;
        }
        
        /* (non-Javadoc)
         * @see java.util.Iterator#next()
         */
        public Object next() {
            return nextBDD();
        }
        
        public int nextValue(BDDDomain dom) {
            if (a == null) {
                throw new NoSuchElementException();
            }
            lastReturned = null;
            int val = 0;
            int[] ivar = dom.vars();
            for (int m = dom.varNum() - 1; m >= 0; m--) {
                val = val << 1;
                int level = factory.var2Level(ivar[m]);
                int k = Arrays.binarySearch(v, level);
                if (k < 0) {
                    val = -1;  // TODO
                    break;
                }
                if (b[k]) {
                    val++;
                }
            }
            if (!gotoNextA()) {
                gotoNext();
            }
            return val;
        }
        
        /**
         * Return the next tuple of domain values in the iteration.
         * 
         * @return the next tuple of domain values in the iteration.
         */
        public int[] nextTuple() {
            if (a == null) {
                throw new NoSuchElementException();
            }
            lastReturned = null;
            int[] result = new int[factory.numberOfDomains()];
            for (int i = 0; i < result.length; ++i) {
                BDDDomain dom = factory.getDomain(i);
                int[] ivar = dom.vars();
                int val = 0;
                for (int m = dom.varNum() - 1; m >= 0; m--) {
                    val = val << 1;
                    int level = factory.var2Level(ivar[m]);
                    int k = Arrays.binarySearch(v, level);
                    if (k < 0) {
                        val = -1;  // TODO
                        break;
                    }
                    if (b[k]) {
                        val++;
                    }
                }
                result[i] = val;
            }
            if (!gotoNextA()) {
                gotoNext();
            }
            return result;
        }
        
        /**
         * An alternate implementation of nextTuple().
         * This may be slightly faster than the default if there are many domains.
         * 
         * @return the next tuple of domain values in the iteration.
         */
        public int[] nextTuple2() {
            boolean[] store = nextSat();
            int[] result = new int[factory.numberOfDomains()];
            for (int i = 0; i < result.length; ++i) {
                BDDDomain dom = factory.getDomain(i);
                int[] ivar = dom.vars();
                int val = 0;
                for (int m = dom.varNum() - 1; m >= 0; m--) {
                    val = val << 1;
                    if (store[ivar[m]])
                        val++;
                }
                result[i] = val;
            }
            return result;
        }
        
        /**
         * Return the next single satisfying assignment in the iteration.
         * 
         * @return the next single satisfying assignment in the iteration.
         */
        public boolean[] nextSat() {
            if (a == null) {
                throw new NoSuchElementException();
            }
            lastReturned = null;
            boolean[] result = new boolean[factory.varNum()];
            for (int i = 0; i < b.length; ++i) {
                result[factory.level2Var(v[i])] = b[i];
            }
            if (!gotoNextA()) {
                gotoNext();
            }
            return result;
        }
        
        /**
         * Return the next BDD in the iteration.
         * 
         * @return the next BDD in the iteration
         */
        public BDD nextBDD() {
            if (a == null) {
                throw new NoSuchElementException();
            }
            //if (lastReturned != null) lastReturned.free();
            lastReturned = factory.one();
            //for (int i = 0; i < v.length; ++i) {
            for (int i = v.length-1; i >= 0; --i) {
                int li = v[i];
                int vi = factory.level2Var(li);
                if (b[i] == true) lastReturned.andWith(factory.ithVar(vi));
                else lastReturned.andWith(factory.nithVar(vi));
            }
            if (!gotoNextA()) {
                gotoNext();
            }
            return lastReturned;
        }
        public void remove() {
        }
    }

    private abstract class TupleIterable<T> implements Iterable<T> {
        protected final BDD bdd;
        protected final BDD iterBdd;
        protected final int[] domIdxs;
        public TupleIterable(BDD bdd, boolean[] keptDoms) {
            this.bdd = bdd;
            if (keptDoms == null) {
                iterBdd = Rel.this.iterBdd;
                domIdxs = Rel.this.domIdxs;
            } else {
                iterBdd = makeIterBdd(keptDoms); 
                int numKeptDoms = 0;
                for (int i = 0; i < keptDoms.length; i++) {
                    if (keptDoms[i])
                        numKeptDoms++;
                }
                domIdxs = new int[numKeptDoms];
                for (int i = 0, j = 0; i < keptDoms.length; i++) {
                    if (keptDoms[i]) {
                        domIdxs[j] = i;
                        j++;
                    }
                }
            }
        }
    };

    public abstract class TupleIterator<T> implements Iterator<T> {
        protected final BDDIterator it;
        protected final int[] domIdxs;
        public TupleIterator(BDDIterator it, int[] domIdxs) {
            this.it = it;
            this.domIdxs = domIdxs;
        }
        public boolean hasNext() {
            return it.hasNext();
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    };

    public class SelfIterable<T> extends TupleIterable<T> {
        public SelfIterable(BDD bdd, boolean[] keptDoms) {
            super(bdd, keptDoms);
        }
        public Iterator<T> iterator() {
            BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<T>(it, domIdxs) {
                public T next() {
                    int[] tuple = it.nextTuple2();
                    int domIdx = domIdxs[0];
                    int idx = tuple[domIdx];
                    T val = (T) doms[domIdx].get(idx);
                    return val;
                }
            };
        }
    };

    // TODO: IntSelfIterable

    public class PairIterable<T0,T1> extends TupleIterable<Pair<T0,T1>> {
        public PairIterable(BDD bdd, boolean[] keptDoms) {
            super(bdd, keptDoms);
        }
        public Iterator<Pair<T0,T1>> iterator() {
            BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<Pair<T0,T1>>(it, domIdxs) {
                public Pair<T0,T1> next() {
                    int[] tuple = it.nextTuple2();
                    int domIdx0 = domIdxs[0];
                    int idx0 = tuple[domIdx0];
                    T0 val0 = (T0) doms[domIdx0].get(idx0);
                    int domIdx1 = domIdxs[1];
                    int idx1 = tuple[domIdx1];
                    T1 val1 = (T1) doms[domIdx1].get(idx1);
                    return new Pair<T0,T1>(val0, val1);
                }
            };
        }
    };

    public class IntPairIterable extends TupleIterable<IntPair> {
        public IntPairIterable(BDD bdd, boolean[] keptDoms) {
            super(bdd, keptDoms);
        }
        public Iterator<IntPair> iterator() {
            BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<IntPair>(it, domIdxs) {
                public IntPair next() {
                    int[] tuple = it.nextTuple2();
                    int domIdx0 = domIdxs[0];
                    int idx0 = tuple[domIdx0];
                    int domIdx1 = domIdxs[1];
                    int idx1 = tuple[domIdx1];
                    return new IntPair(idx0, idx1);
                }
            };
        }
    };

    public class TrioIterable<T0,T1,T2> extends TupleIterable<Trio<T0,T1,T2>> {
        public TrioIterable(BDD bdd, boolean[] keptDoms) {
            super(bdd, keptDoms);
        }
        public Iterator<Trio<T0,T1,T2>> iterator() {
            BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<Trio<T0,T1,T2>>(it, domIdxs) {
                public Trio<T0,T1,T2> next() {
                    int[] tuple = it.nextTuple2();
                    int domIdx0 = domIdxs[0];
                    int idx0 = tuple[domIdx0];
                    T0 val0 = (T0) doms[domIdx0].get(idx0);
                    int domIdx1 = domIdxs[1];
                        int idx1 = tuple[domIdx1];
                    T1 val1 = (T1) doms[domIdx1].get(idx1);
                    int domIdx2 = domIdxs[2];
                    int idx2 = tuple[domIdx2];
                    T2 val2 = (T2) doms[domIdx2].get(idx2);
                    return new Trio<T0,T1,T2>(val0, val1, val2);
                }
            };
        }
    };

    public class IntTrioIterable extends TupleIterable<IntTrio> {
        public IntTrioIterable(BDD bdd, boolean[] keptDoms) {
            super(bdd, keptDoms);
        }
        public Iterator<IntTrio> iterator() {
            BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<IntTrio>(it, domIdxs) {
                public IntTrio next() {
                    int[] tuple = it.nextTuple2();
                    int domIdx0 = domIdxs[0];
                    int idx0 = tuple[domIdx0];
                    int domIdx1 = domIdxs[1];
                    int idx1 = tuple[domIdx1];
                    int domIdx2 = domIdxs[2];
                    int idx2 = tuple[domIdx2];
                    return new IntTrio(idx0, idx1, idx2);
                }
            };
        }
    };

    public class QuadIterable<T0,T1,T2,T3> extends TupleIterable<Quad<T0,T1,T2,T3>> {
        public QuadIterable(BDD bdd, boolean[] keptDoms) {
            super(bdd, keptDoms);
        }
        public Iterator<Quad<T0,T1,T2,T3>> iterator() {
            BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<Quad<T0,T1,T2,T3>>(it, domIdxs) {
                public Quad<T0,T1,T2,T3> next() {
                    int[] tuple = it.nextTuple2();
                    int domIdx0 = domIdxs[0];
                    int idx0 = tuple[domIdx0];
                    T0 val0 = (T0) doms[domIdx0].get(idx0);
                    int domIdx1 = domIdxs[1];
                    int idx1 = tuple[domIdx1];
                    T1 val1 = (T1) doms[domIdx1].get(idx1);
                    int domIdx2 = domIdxs[2];
                    int idx2 = tuple[domIdx2];
                    T2 val2 = (T2) doms[domIdx2].get(idx2);
                    int domIdx3 = domIdxs[3];
                    int idx3 = tuple[domIdx3];
                    T3 val3 = (T3) doms[domIdx3].get(idx3);
                    return new Quad<T0,T1,T2,T3>(val0, val1, val2, val3);
                }
            };
        }
    };

    public class IntQuadIterable extends TupleIterable<IntQuad> {
        public IntQuadIterable(BDD bdd, boolean[] keptDoms) {
            super(bdd, keptDoms);
        }
        public Iterator<IntQuad> iterator() {
            BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<IntQuad>(it, domIdxs) {
                public IntQuad next() {
                    int[] tuple = it.nextTuple2();
                    int domIdx0 = domIdxs[0];
                    int idx0 = tuple[domIdx0];
                    int domIdx1 = domIdxs[1];
                    int idx1 = tuple[domIdx1];
                    int domIdx2 = domIdxs[2];
                    int idx2 = tuple[domIdx2];
                    int domIdx3 = domIdxs[3];
                    int idx3 = tuple[domIdx3];
                    return new IntQuad(idx0, idx1, idx2, idx3);
                }
            };
        }
    };

    public class PentIterable<T0,T1,T2,T3,T4> extends TupleIterable<Pent<T0,T1,T2,T3,T4>> {
        public PentIterable(BDD bdd, boolean[] keptDoms) {
            super(bdd, keptDoms);
        }
        public Iterator<Pent<T0,T1,T2,T3,T4>> iterator() {
            BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<Pent<T0,T1,T2,T3,T4>>(it, domIdxs) {
                public Pent<T0,T1,T2,T3,T4> next() {
                    int[] tuple = it.nextTuple2();
                    int domIdx0 = domIdxs[0];
                    int idx0 = tuple[domIdx0];
                    T0 val0 = (T0) doms[domIdx0].get(idx0);
                    int domIdx1 = domIdxs[1];
                    int idx1 = tuple[domIdx1];
                    T1 val1 = (T1) doms[domIdx1].get(idx1);
                    int domIdx2 = domIdxs[2];
                    int idx2 = tuple[domIdx2];
                    T2 val2 = (T2) doms[domIdx2].get(idx2);
                    int domIdx3 = domIdxs[3];
                    int idx3 = tuple[domIdx3];
                    T3 val3 = (T3) doms[domIdx3].get(idx3);
                    int domIdx4 = domIdxs[4];
                    int idx4 = tuple[domIdx4];
                    T4 val4 = (T4) doms[domIdx4].get(idx4);
                    return new Pent<T0,T1,T2,T3,T4>(val0, val1, val2, val3, val4);
                }
            };
        }
    };

    public class IntPentIterable extends TupleIterable<IntPent> {
        public IntPentIterable(BDD bdd, boolean[] keptDoms) {
            super(bdd, keptDoms);
        }
        public Iterator<IntPent> iterator() {
            BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<IntPent>(it, domIdxs) {
                public IntPent next() {
                    int[] tuple = it.nextTuple2();
                    int domIdx0 = domIdxs[0];
                    int idx0 = tuple[domIdx0];
                    int domIdx1 = domIdxs[1];
                    int idx1 = tuple[domIdx1];
                    int domIdx2 = domIdxs[2];
                    int idx2 = tuple[domIdx2];
                    int domIdx3 = domIdxs[3];
                    int idx3 = tuple[domIdx3];
                    int domIdx4 = domIdxs[4];
                    int idx4 = tuple[domIdx4];
                    return new IntPent(idx0, idx1, idx2, idx3, idx4);
                }
            };
        }
    };

    public class HextIterable<T0,T1,T2,T3,T4,T5> extends TupleIterable<Hext<T0,T1,T2,T3,T4,T5>> {
        public HextIterable(BDD bdd, boolean[] keptDoms) {
            super(bdd, keptDoms);
        }
        public Iterator<Hext<T0,T1,T2,T3,T4,T5>> iterator() {
            BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<Hext<T0,T1,T2,T3,T4,T5>>(it, domIdxs) {
                public Hext<T0,T1,T2,T3,T4,T5> next() {
                    int[] tuple = it.nextTuple2();
                    int domIdx0 = domIdxs[0];
                    int idx0 = tuple[domIdx0];
                    T0 val0 = (T0) doms[domIdx0].get(idx0);
                    int domIdx1 = domIdxs[1];
                    int idx1 = tuple[domIdx1];
                    T1 val1 = (T1) doms[domIdx1].get(idx1);
                    int domIdx2 = domIdxs[2];
                    int idx2 = tuple[domIdx2];
                    T2 val2 = (T2) doms[domIdx2].get(idx2);
                    int domIdx3 = domIdxs[3];
                    int idx3 = tuple[domIdx3];
                    T3 val3 = (T3) doms[domIdx3].get(idx3);
                    int domIdx4 = domIdxs[4];
                    int idx4 = tuple[domIdx4];
                    T4 val4 = (T4) doms[domIdx4].get(idx4);
                    int domIdx5 = domIdxs[5];
                    int idx5 = tuple[domIdx5];
                    T5 val5 = (T5) doms[domIdx5].get(idx5);
                    return new Hext<T0,T1,T2,T3,T4,T5>(val0, val1, val2, val3, val4, val5);
                }
            };
        }
    };

    public class IntHextIterable extends TupleIterable<IntHext> {
        public IntHextIterable(BDD bdd, boolean[] keptDoms) {
            super(bdd, keptDoms);
        }
        public Iterator<IntHext> iterator() {
            BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<IntHext>(it, domIdxs) {
                public IntHext next() {
                    int[] tuple = it.nextTuple2();
                    int domIdx0 = domIdxs[0];
                    int idx0 = tuple[domIdx0];
                    int domIdx1 = domIdxs[1];
                    int idx1 = tuple[domIdx1];
                    int domIdx2 = domIdxs[2];
                    int idx2 = tuple[domIdx2];
                    int domIdx3 = domIdxs[3];
                    int idx3 = tuple[domIdx3];
                    int domIdx4 = domIdxs[4];
                    int idx4 = tuple[domIdx4];
                    int domIdx5 = domIdxs[5];
                    int idx5 = tuple[domIdx5];
                    return new IntHext(idx0, idx1, idx2, idx3, idx4, idx5);
                }
            };
        }
    };

    public class AryNIterable extends TupleIterable<Object[]> {
        public AryNIterable(BDD bdd, boolean[] keptDoms) {
            super(bdd, keptDoms);
        }
        public Iterator<Object[]> iterator() {
            BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<Object[]>(it, domIdxs) {
                public Object[] next() {
                    int numDoms = domIdxs.length;
                    int[] tuple = it.nextTuple2();
                    Object[] vals = new Object[numDoms];
                    for (int i = 0; i < numDoms; i++) {
                        int domIdx = domIdxs[i];
                        int idx = tuple[domIdx];
                        Object val = doms[domIdx].get(idx);
                        vals[i] = val;
                    }
                    return vals;
                }
            };
        }
    };

    public class IntAryNIterable extends TupleIterable<int[]> {
        public IntAryNIterable(BDD bdd, boolean[] keptDoms) {
           super(bdd, keptDoms);
        }
        public Iterator<int[]> iterator() {
           BDDIterator it = new BDDIterator(bdd, iterBdd);
            return new TupleIterator<int[]>(it, domIdxs) {
                public int[] next() {
                    int numDoms = domIdxs.length;
                    int[] tuple = it.nextTuple2();
                    int[] vals = new int[numDoms];
                    for (int i = 0; i < numDoms; i++) {
                        int domIdx = domIdxs[i];
                        int idx = tuple[domIdx];
                        vals[i] = idx;
                    }
                    return vals;
                }
            };
        }
    };

    /*
     * Ary1Rel operations.
     */

    public <T0> void add(T0 val0) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        try {
            bdd.orWith(domBdds[0].ithVar(idx0));
        } catch (BDDException ex) {
            checkRange(val0, 0);
            throw new RuntimeException(ex);
        }
    }
    public void add(int idx0) {
        if (bdd == null)
            throw new RuntimeException("");
        try {
            bdd.orWith(domBdds[0].ithVar(idx0));
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            throw new RuntimeException(ex);
        }
    }
    public <T0> void remove(T0 val0) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        try {
            bdd.andWith(domBdds[0].ithVar(idx0).not());
        } catch (BDDException ex) {
            checkRange(val0, 0);
            throw new RuntimeException(ex);
        }
    }
    public void remove(int idx0) {
        if (bdd == null)
            throw new RuntimeException("");
        try {
            bdd.andWith(domBdds[0].ithVar(idx0).not());
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            throw new RuntimeException(ex);
        }
    }
    public <T0> boolean contains(T0 val0) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        try {
            return !bdd.id().andWith(domBdds[0].ithVar(idx0)).isZero();
        } catch (BDDException ex) {
            checkRange(val0, 0);
            throw new RuntimeException(ex);
        }
    }
    public boolean contains(int idx0) {
        if (bdd == null)
            throw new RuntimeException("");
        try {
            return !bdd.id().andWith(domBdds[0].ithVar(idx0)).isZero();
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            throw new RuntimeException(ex);
        }
    }
    public <T0> Iterable<T0> getAry1ValTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new SelfIterable<T0>(bdd, null);
    }

    /*
     * Ary2Rel operations.
     */
    
    public <T0,T1> void add(T0 val0, T1 val1) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        int idx1 = doms[1].indexOf(val1);
        try {
            bdd.orWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1)));
        } catch (BDDException ex) {
            checkRange(val0, 0);
            checkRange(val1, 1);
            throw new RuntimeException(ex);
        }
    }
    public void add(int idx0, int idx1) {
        if (bdd == null)
            throw new RuntimeException("");
        try {
            bdd.orWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1)));
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            checkRange(idx1, 1);
            throw new RuntimeException(ex);
        }
    }
    public <T0,T1> void remove(T0 val0, T1 val1) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        int idx1 = doms[1].indexOf(val1);
        try {
            bdd.andWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1)).not());
        } catch (BDDException ex) {
            checkRange(val0, 0);
            checkRange(val1, 1);
            throw new RuntimeException(ex);
        }
    }
    public void remove(int idx0, int idx1) {
        if (bdd == null)
            throw new RuntimeException("");
        try {
            bdd.andWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1)).not());
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            checkRange(idx1, 1);
            throw new RuntimeException(ex);
        }
    }
    public <T0,T1> boolean contains(T0 val0, T1 val1) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        int idx1 = doms[1].indexOf(val1);
        try {
            return !bdd.id().andWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1))).isZero();
        } catch (BDDException ex) {
            checkRange(val0, 0);
            checkRange(val1, 1);
            throw new RuntimeException(ex);
        }
    }
    public boolean contains(int idx0, int idx1) {
        if (bdd == null)
            throw new RuntimeException("");
        try {
            return !bdd.id().andWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1))).isZero();
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            checkRange(idx1, 1);
            throw new RuntimeException(ex);
        }
    }
    public <T0,T1> PairIterable<T0,T1> getAry2ValTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new PairIterable<T0,T1>(bdd, null);
    }
    public IntPairIterable getAry2IntTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new IntPairIterable(bdd, null);
    }

    /*
     * Ary3Rel operations.
     */
    
    public <T0,T1,T2> void add(T0 val0, T1 val1, T2 val2) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        int idx1 = doms[1].indexOf(val1);
        int idx2 = doms[2].indexOf(val2);
        try {
            bdd.orWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1).andWith(
                domBdds[2].ithVar(idx2))));
        } catch (BDDException ex) {
            checkRange(val0, 0);
            checkRange(val1, 1);
            checkRange(val2, 2);
            throw new RuntimeException(ex);
        }
    }
    public void add(int idx0, int idx1, int idx2) {
        if (bdd == null)
            throw new RuntimeException("");
        try {
            bdd.orWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1).andWith(
                domBdds[2].ithVar(idx2))));
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            checkRange(idx1, 1);
            checkRange(idx2, 2);
            throw new RuntimeException(ex);
        }
    }
    public <T0,T1,T2> boolean contains(T0 val0, T1 val1, T2 val2) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        int idx1 = doms[1].indexOf(val1);
        int idx2 = doms[2].indexOf(val2);
        try {
            return !bdd.id().andWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1).andWith(
                domBdds[2].ithVar(idx2)))).isZero();
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            checkRange(idx1, 1);
            checkRange(idx2, 2);
            throw new RuntimeException(ex);
        }
    }
    public <T0,T1,T2> TrioIterable<T0,T1,T2> getAry3ValTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new TrioIterable<T0,T1,T2>(bdd, null);
    }
    public IntTrioIterable getAry3IntTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new IntTrioIterable(bdd, null);
    }

    /*
     * Ary4Rel operations.
     */
    
    public <T0,T1,T2,T3> void add(T0 val0, T1 val1, T2 val2, T3 val3) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        int idx1 = doms[1].indexOf(val1);
        int idx2 = doms[2].indexOf(val2);
        int idx3 = doms[3].indexOf(val3);
        try {
            bdd.orWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1).andWith(
                domBdds[2].ithVar(idx2).andWith(
                domBdds[3].ithVar(idx3)))));
        } catch (BDDException ex) {
            checkRange(val0, 0);
            checkRange(val1, 1);
            checkRange(val2, 2);
            checkRange(val3, 3);
            throw new RuntimeException(ex);
        }
    }
    public void add(int idx0, int idx1, int idx2, int idx3) {
        if (bdd == null)
            throw new RuntimeException("");
        try {
            bdd.orWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1).andWith(
                domBdds[2].ithVar(idx2).andWith(
                domBdds[3].ithVar(idx3)))));
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            checkRange(idx1, 1);
            checkRange(idx2, 2);
            checkRange(idx3, 3);
            throw new RuntimeException(ex);
        }
    }
    public <T0,T1,T2,T3> boolean contains(T0 val0, T1 val1, T2 val2, T3 val3) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        int idx1 = doms[1].indexOf(val1);
        int idx2 = doms[2].indexOf(val2);
        int idx3 = doms[3].indexOf(val3);
        try {
            return !bdd.id().andWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1).andWith(
                domBdds[2].ithVar(idx2).andWith(
                domBdds[3].ithVar(idx3))))).isZero();
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            checkRange(idx1, 1);
            checkRange(idx2, 2);
            checkRange(idx3, 3);
            throw new RuntimeException(ex);
        }
    }
    public <T0,T1,T2,T3> QuadIterable<T0,T1,T2,T3> getAry4ValTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new QuadIterable<T0,T1,T2,T3>(bdd, null);
    }
    public IntQuadIterable getAry4IntTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new IntQuadIterable(bdd, null);
    }

    /*
     * Ary5Rel operations.
     */
    
    public <T0,T1,T2,T3,T4> void add(T0 val0, T1 val1, T2 val2, T3 val3, T4 val4) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        int idx1 = doms[1].indexOf(val1);
        int idx2 = doms[2].indexOf(val2);
        int idx3 = doms[3].indexOf(val3);
        int idx4 = doms[4].indexOf(val4);
        try {
            bdd.orWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1).andWith(
                domBdds[2].ithVar(idx2).andWith(
                domBdds[3].ithVar(idx3).andWith(
                domBdds[4].ithVar(idx4))))));
        } catch (BDDException ex) {
            checkRange(val0, 0);
            checkRange(val1, 1);
            checkRange(val2, 2);
            checkRange(val3, 3);
            checkRange(val4, 4);
            throw new RuntimeException(ex);
        }
    }
    public void add(int idx0, int idx1, int idx2, int idx3, int idx4) {
        if (bdd == null)
            throw new RuntimeException("");
        try {
            bdd.orWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1).andWith(
                domBdds[2].ithVar(idx2).andWith(
                domBdds[3].ithVar(idx3).andWith(
                domBdds[4].ithVar(idx4))))));
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            checkRange(idx1, 1);
            checkRange(idx2, 2);
            checkRange(idx3, 3);
            checkRange(idx4, 4);
            throw new RuntimeException(ex);
        }

    }
    public <T0,T1,T2,T3,T4> boolean contains(T0 val0, T1 val1, T2 val2, T3 val3, T4 val4) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        int idx1 = doms[1].indexOf(val1);
        int idx2 = doms[2].indexOf(val2);
        int idx3 = doms[3].indexOf(val3);
        int idx4 = doms[4].indexOf(val4);
        try {
            return !bdd.id().andWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1).andWith(
                domBdds[2].ithVar(idx2).andWith(
                domBdds[3].ithVar(idx3).andWith(
                domBdds[4].ithVar(idx4)))))).isZero();
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            checkRange(idx1, 1);
            checkRange(idx2, 2);
            checkRange(idx3, 3);
            checkRange(idx4, 4);
            throw new RuntimeException(ex);
        }
    }
    public <T0,T1,T2,T3,T4> PentIterable<T0,T1,T2,T3,T4> getAry5ValTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new PentIterable<T0,T1,T2,T3,T4>(bdd, null);
    }
    public IntPentIterable getAry5IntTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new IntPentIterable(bdd, null);
    }
    
    /*
     * Ary6Rel operations.
     */
    
    public <T0,T1,T2,T3,T4,T5> void add(T0 val0, T1 val1, T2 val2, T3 val3, T4 val4, T5 val5) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        int idx1 = doms[1].indexOf(val1);
        int idx2 = doms[2].indexOf(val2);
        int idx3 = doms[3].indexOf(val3);
        int idx4 = doms[4].indexOf(val4);
        int idx5 = doms[5].indexOf(val5);
        try {
            bdd.orWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1).andWith(
                domBdds[2].ithVar(idx2).andWith(
                domBdds[3].ithVar(idx3).andWith(
                domBdds[4].ithVar(idx4).andWith(
                domBdds[5].ithVar(idx5)))))));
        } catch (BDDException ex) {
            checkRange(val0, 0);
            checkRange(val1, 1);
            checkRange(val2, 2);
            checkRange(val3, 3);
            checkRange(val4, 4);
            checkRange(val5, 5);
            throw new RuntimeException(ex);
        }
    }
    public void add(int idx0, int idx1, int idx2, int idx3, int idx4, int idx5) {
        if (bdd == null)
            throw new RuntimeException("");
        try {
            bdd.orWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1).andWith(
                domBdds[2].ithVar(idx2).andWith(
                domBdds[3].ithVar(idx3).andWith(
                domBdds[4].ithVar(idx4).andWith(
                domBdds[5].ithVar(idx5)))))));
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            checkRange(idx1, 1);
            checkRange(idx2, 2);
            checkRange(idx3, 3);
            checkRange(idx4, 4);
            checkRange(idx5, 5);
        }
    }
    public <T0,T1,T2,T3,T4,T5> boolean contains(T0 val0, T1 val1, T2 val2, T3 val3, T4 val4, T5 val5) {
        if (bdd == null)
            throw new RuntimeException("");
        int idx0 = doms[0].indexOf(val0);
        int idx1 = doms[1].indexOf(val1);
        int idx2 = doms[2].indexOf(val2);
        int idx3 = doms[3].indexOf(val3);
        int idx4 = doms[4].indexOf(val4);
        int idx5 = doms[5].indexOf(val5);
        try {
            return !bdd.id().andWith(
                domBdds[0].ithVar(idx0).andWith(
                domBdds[1].ithVar(idx1).andWith(
                domBdds[2].ithVar(idx2).andWith(
                domBdds[3].ithVar(idx3).andWith(
                domBdds[4].ithVar(idx4).andWith(
                domBdds[5].ithVar(idx5))))))).isZero();
        } catch (BDDException ex) {
            checkRange(idx0, 0);
            checkRange(idx1, 1);
            checkRange(idx2, 2);
            checkRange(idx3, 3);
            checkRange(idx4, 4);
            checkRange(idx5, 5);
            throw new RuntimeException(ex);
        }
    }
    public <T0,T1,T2,T3,T4,T5> HextIterable<T0,T1,T2,T3,T4,T5> getAry6ValTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new HextIterable<T0,T1,T2,T3,T4,T5>(bdd, null);
    }
    public IntHextIterable getAry6IntTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new IntHextIterable(bdd, null);
    }

    /*
     * AryNRel operations.
     */
    public void add(Object[] vals) {
        if (bdd == null)
            throw new RuntimeException("");
        throw new UnsupportedOperationException();
    }
    public void add(int[] idxs) {
        if (bdd == null)
            throw new RuntimeException("");
        throw new UnsupportedOperationException();
    }
    public boolean contains(Object[] vals) {
        if (bdd == null)
            throw new RuntimeException("");
        throw new UnsupportedOperationException();
    }
    public AryNIterable getAryNValTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new AryNIterable(bdd, null);
    }
    public IntAryNIterable getAryNIntTuples() {
        if (bdd == null)
            throw new RuntimeException("");
        return new IntAryNIterable(bdd, null);
    }

    public void print() {
        print(System.out);
    }
    public void print(PrintStream out) {
        AryNIterable tuples = getAryNValTuples();
        for (Object[] tuple : tuples) {
            for (Object elem : tuple)
                out.print(elem + " ");
            out.println();
        }
    }
}
