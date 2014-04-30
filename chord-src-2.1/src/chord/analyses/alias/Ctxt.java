package chord.analyses.alias;

import joeq.Compiler.Quad.Quad;
import java.io.Serializable;

/**
 * Representation of an abstract context of a method.
 * <p>
 * Each abstract context is a possibly empty sequence of the form
 * <tt>[e1,...,en]</tt> where each <tt>ei</tt> is either an object
 * allocation statement or a method invocation statement in
 * decreasing order of significance.
 * <p>
 * The abstract context corresponding to the empty sequence, called
 * <tt>epsilon</tt>, is the lone context of methods that are
 * analyzed context insensitively.  These include the main method,
 * all class initializer methods, and any additional user-specified
 * methods (see {@link chord.analyses.alias.CtxtsAnalysis}).
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Ctxt implements Serializable {
    /**
     * The sequence of statements comprising the abstract context, in decreasing order of significance.
     */
    private final Quad[] elems;
    /**
     * Constructor.
     * 
     * @param elems The sequence of statements comprising this abstract context.
     */
    public Ctxt(Quad[] elems) {
        this.elems = elems;
    }
    /**
     * Provides the sequence of statements comprising this abstract context.
     * 
     * @return The sequence of statements comprising this abstract context.
     */
    public Quad[] getElems() {
        return elems;
    }
    /**
     * Determines whether this abstract context contains a given statement.
     * 
     * @param inst A statement.
     * 
     * @return true iff this abstract context contains the given statement.
     */
    public boolean contains(Quad inst) {
        for (int i = 0; i < elems.length; i++) {
            if (elems[i] == inst)
                return true;
        }
        return false;
    }
  public int count(Quad inst) {
    int n = 0;
        for (int i = 0; i < elems.length; i++) {
            if (elems[i] == inst)
        n++;
        }
    return n;
  }
    public int hashCode() {
        int i = 5381;
        for (Quad inst : elems) {
            int q = inst == null ? 9999 : inst.getID();
            i = ((i << 5) + i) + q; // i*33 + q
        }
        return i;
    }
    public boolean equals(Object o) {
        if (!(o instanceof Ctxt))
            return false;
        Ctxt that = (Ctxt) o;
        Quad[] thisElems = this.elems;
        Quad[] thatElems = that.elems;
        int n = thisElems.length;
        if (thatElems.length != n)
            return false;
        for (int i = 0; i < n; i++) {
            Quad inst = thisElems[i];
            if (inst != thatElems[i])
                return false;
        }
        return true;
    }
    public String toString() {
        String s = "[";
        int n = elems.length;
        for (int i = 0; i < n; i++) {
            Quad q = elems[i];
            s += q == null ? "null" : q.toByteLocStr();
            if (i < n - 1)
                s += ",";
        }
        return s + "]";
    }

  public int length() { return elems.length; }
  public Quad get(int i) { return elems[i]; }
  public Quad head() { return elems[0]; }
  public Quad last() { return elems[elems.length-1]; }
  public Ctxt tail() { return suffix(elems.length-1); }
  public Ctxt prefix(int k) {
    if (k >= elems.length) return this;
    Quad[] newElems = new Quad[k];
    if (k > 0) System.arraycopy(elems, 0, newElems, 0, k);
    return new Ctxt(newElems);
  }
  public Ctxt suffix(int k) {
    if (k >= elems.length) return this;
    Quad[] newElems = new Quad[k];
    if (k > 0) System.arraycopy(elems, elems.length-k, newElems, 0, k);
    return new Ctxt(newElems);
  }

  // Maximize length of returned context is max
  public Ctxt prepend(Quad q) { return prepend(q, Integer.MAX_VALUE); }
  public Ctxt prepend(Quad q, int max) {
    int oldLen = elems.length;
    int newLen = Math.min(max, oldLen+1);
    Quad[] newElems = new Quad[newLen];
    if (newLen > 0) newElems[0] = q;
    if (newLen > 1) System.arraycopy(elems, 0, newElems, 1, newLen-1);
    return new Ctxt(newElems);
  }

  public Ctxt append(Quad q) {
    Quad[] newElems = new Quad[elems.length+1];
    System.arraycopy(elems, 0, newElems, 0, elems.length);
    newElems[newElems.length-1] = q;
    return new Ctxt(newElems);
  }
}
