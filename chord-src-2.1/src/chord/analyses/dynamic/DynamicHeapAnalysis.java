package chord.analyses.dynamic;

import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.List;

import chord.analyses.field.DomF;
import chord.instr.InstrScheme;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.DynamicAnalysis;

/**
 * Dynamic heap analysis.
 *
 * Relevant system properties:
 * - chord.dyn.strong.updates=[true|false] (default=true)
 * - chord.dyn.smash.aryelems=[true|false] (default=false)
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "dyn-heap-java"
)
public class DynamicHeapAnalysis extends DynamicAnalysis {
    protected boolean doStrongUpdates = System.getProperty("chord.dyn.strong.updates", "true").equals("true");
     // smashArrayElems is true => use field 0 in domF instead of array indices in maps OtoFOlistFwd/OtoFOlistInv
    protected boolean smashArrayElems = System.getProperty("chord.dyn.smash.aryelems", "false").equals("true");
    // Map from each object to a list of each instance field of ref type along with the pointed object.
    // Fields with null value are not stored.
     // Invariant: OtoFOlistFwd(o) contains (f,o1) and (f,o2) => o1==o2
    protected TIntObjectHashMap<List<FldObj>> O2FOlist;
    // Inverse of the above map
    // maintained only if keepInv() is true
    protected TIntObjectHashMap<List<FldObj>> O2FOlistInv;
    // map from each object to the index in domH of its alloc site
    // maintained only if keepO2H() is true
    protected TIntIntHashMap O2H;
    protected InstrScheme instrScheme;
    protected int numF;

    public boolean keepInv() { return false; }
    public boolean keepO2H() { return false; }
    
    @Override
    public InstrScheme getInstrScheme() {
        instrScheme = new InstrScheme();
        instrScheme.setPutfieldReferenceEvent(false, false, true, true, true);
        instrScheme.setAstoreReferenceEvent(false, false, true, true, true);
        if (keepO2H()) {
            instrScheme.setBefNewEvent(true, false, true);
            instrScheme.setNewArrayEvent(true, false, true);
        }
        return instrScheme;
    }

    @Override
    public void initAllPasses() {
        if (smashArrayElems)
            assert (!doStrongUpdates);
        O2FOlist = new TIntObjectHashMap<List<FldObj>>();
        if (keepInv()) O2FOlistInv = new TIntObjectHashMap<List<FldObj>>();
        if (keepO2H()) O2H = new TIntIntHashMap();
        numF = ((DomF) ClassicProject.g().runTask("F")).size();
    }

    @Override
    public void initPass() {
        O2FOlist.clear();
        if (keepInv()) O2FOlistInv.clear();
        if (keepO2H()) O2H.clear();
    }
    
    /*****************************************************************/
    // Routines for handling instrumentation events
    /*****************************************************************/

    @Override
    public void processPutfieldReference(int e, int t, int b, int f, int o) {
        if (b != 0 && f >= 0) processHeapWr(b, f, o);
    }

    @Override
    public void processAstoreReference(int e, int t, int b, int i, int o) {
        if (b != 0 && i >= 0) processHeapWr(b, smashArrayElems ? 0 : i + numF, o);
    }


    @Override
    public void processBefNew(int h, int t, int o) {
        processNew(h, o);
    }

    @Override
    public void processNewArray(int h, int t, int o) {
        processNew(h, o);
    }

    /*****************************************************************/
    // Auxiliary routines
    /*****************************************************************/

    protected void processNew(int h, int o) {
        if (o != 0 && h >= 0)
            O2H.put(o, h);
    }

    // assumes b != 0 && f >= 0
    protected void processHeapWr(int b, int f, int r) {
        if (r == 0) {
            if (!doStrongUpdates)
                return;
            // this is a strong update; so remove field f if it is there
            List<FldObj> fwd = O2FOlist.get(b);
            if (fwd == null)
                return;
            int n = fwd.size();
            for (int i = 0; i < n; i++) {
                FldObj fo = fwd.get(i);
                if (fo.f == f) {
                    if (keepInv()) removeInv(fo.o, f, b);
                    fwd.remove(i);
                    break;
                }
            }
            return;
        }
        List<FldObj> fwd = O2FOlist.get(b);
        boolean added = false;
        if (fwd == null) {
            fwd = new ArrayList<FldObj>();
            O2FOlist.put(b, fwd);
        } else if (doStrongUpdates) {
            int n = fwd.size();
            for (int i = 0; i < n; i++) {
                FldObj fo = fwd.get(i);
                if (fo.f == f) {
                    if (keepInv()) removeInv(fo.o, f, b);
                    fo.o = r;
                    added = true;
                    break;
                }
            }
        } else {
            // do not add to fwd if already there;
            // since fwd is a list as opposed to a set, this
            // check must be done explicitly
            int n = fwd.size();
            for (int i = 0; i < n; i++) {
                FldObj fo = fwd.get(i);
                if (fo.f == f && fo.o == r) {
                    added = true;
                    break;
                }
            }
        }
        if (!added)
            fwd.add(new FldObj(f, r));
        if (keepInv()) {
            List<FldObj> inv = O2FOlistInv.get(r);
            if (inv == null) {
                inv = new ArrayList<FldObj>();
                O2FOlistInv.put(r, inv);
            }
            boolean found = false;
            int n = inv.size();
            for (int i = 0; i < n; i++) {
                FldObj fo = inv.get(i);
                if (fo.f == f && fo.o == b) {
                    found = true;
                    break;
                }
            }
            if (!found)
                inv.add(new FldObj(f, b));
        }
    }

    private void removeInv(int rOld, int f, int b) {
        List<FldObj> inv = O2FOlistInv.get(rOld);
        assert (inv != null);
        int n = inv.size();
        for (int i = 0; i < n; i++) {
            FldObj fo = inv.get(i);
            if (fo.f == f && fo.o == b) {
                inv.remove(i);
                return;
            }
        }
        assert (false);
    }
}
