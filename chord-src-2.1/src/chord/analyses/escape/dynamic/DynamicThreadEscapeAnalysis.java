package chord.analyses.escape.dynamic;

import gnu.trove.set.hash.TIntHashSet;

import java.io.PrintWriter;
import java.util.List;

import joeq.Compiler.Quad.Quad;
import chord.analyses.heapacc.DomE;
import chord.instr.InstrScheme;
import chord.project.Chord;
import chord.project.OutDirUtils;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.analyses.dynamic.DynamicHeapAnalysis;
import chord.analyses.dynamic.FldObj;

/**
 * Dynamic thread-escape analysis.
 * 
 * It outputs the following relations and files:
 *
 * - relation accE and file dynamic_accE.txt, containing all instance
 *   field and array element accessing statements that were reached
 *   at least once.
 * - relation escE and file dynamic_escE.txt, containing those
 *   statements in accE that were observed to access thread-shared
 *   data at least once.
 * - relation likelyLocE and file dynamic_locE.txt, containing those
 *   statements in accE that were observed to always access
 *   thread-local data.
 *
 * Relation accE is the disjoint union of relations escE and locE.
 *
 * Recognized system properties:
 * - chord.check.exclude
 * - chord.scope.exclude
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "dyn-thresc-java",
    consumes = { "queryE" },
    produces = { "dynAccE", "dynEscE", "dynLocE" }, 
    namesOfSigns = { "dynAccE", "dynEscE", "dynLocE" },
    signs = { "E0", "E0", "E0" }
)
public class DynamicThreadEscapeAnalysis extends DynamicHeapAnalysis {
    // accesses to be checked; allows excluding certain accesses such as
    // those from JDK library code
    protected boolean[] chkE;
    // visited accesses (subset of chkE)
    protected boolean[] accE;
    // provably thread-escaping accesses (subset of accE)
    protected boolean[] escE;
  
    protected boolean[] badE;
    // set of escaping objects; once an object is put into this set,
    // it remains in it for the rest of the execution
    protected TIntHashSet escO;
    protected DomE domE;

    @Override
    public InstrScheme getInstrScheme() {
        super.getInstrScheme();
        instrScheme.setPutstaticReferenceEvent(false, false, false, false, true);
        instrScheme.setThreadStartEvent(false, false, true);
        instrScheme.setGetfieldPrimitiveEvent(true, false, true, false);
        instrScheme.setPutfieldPrimitiveEvent(true, false, true, false);
        instrScheme.setAloadPrimitiveEvent(true, false, true, false);
        instrScheme.setAstorePrimitiveEvent(true, false, true, false);
        instrScheme.setGetfieldReferenceEvent(true, false, true, false, false);
        instrScheme.setPutfieldReferenceEvent(true, false, true, true, true);
        instrScheme.setAloadReferenceEvent(true, false, true, false, false);
        instrScheme.setAstoreReferenceEvent(true, false, true, true, true);
        return instrScheme;
    }

    @Override
    public void initAllPasses() {
        super.initAllPasses();
        escO = new TIntHashSet();
        domE = (DomE) ClassicProject.g().getTrgt("E");
        ClassicProject.g().runTask(domE);
        int numE = domE.size();
        chkE = new boolean[numE];
        ProgramRel relQueryE = (ProgramRel) ClassicProject.g().getTrgt("queryE");
        relQueryE.load();
        Iterable<Quad> tuples = relQueryE.getAry1ValTuples();
        for (Quad q : tuples) {
            int e = domE.indexOf(q);
            chkE[e] = true;
        }
        relQueryE.close();
        accE = new boolean[numE];
        escE = new boolean[numE];
    }

    @Override
    public void initPass() {
        super.initPass();
        escO.clear();
    }

    @Override
    public void donePass() {
        System.out.println("***** STATS *****");
        int numAccE = 0;
        int numEscE = 0;
        for (int i = 0; i < domE.size(); i++) {
            if (accE[i]) {
                numAccE++;
                if (escE[i])
                    numEscE++;
            }
        }
        System.out.println("numAccE: " + numAccE);
        System.out.println("numEscE: " + numEscE);
    }

    @Override
    public void doneAllPasses() {
        ProgramRel  accErel = (ProgramRel) ClassicProject.g().getTrgt("dynAccE");
        ProgramRel  escErel = (ProgramRel) ClassicProject.g().getTrgt("dynEscE");
        ProgramRel  locErel = (ProgramRel) ClassicProject.g().getTrgt("dynLocE");
        PrintWriter accEout = OutDirUtils.newPrintWriter("dynamic_accE.txt");
        PrintWriter escEout = OutDirUtils.newPrintWriter("dynamic_escE.txt");
        PrintWriter locEout = OutDirUtils.newPrintWriter("dynamic_locE.txt");
        accErel.zero();
        escErel.zero();
        locErel.zero();
        for (int i = 0; i < domE.size(); i++) {
            if (accE[i]) {
                String s = domE.get(i).toVerboseStr();
                accEout.println(s);
                accErel.add(i);
                if (escE[i]) {
                    escEout.println(s);
                    escErel.add(i);
                } else {
                    locEout.println(s);
                    locErel.add(i);
                }
            }
        }
        accErel.save();
        escErel.save();
        locErel.save();
        accEout.close();
        escEout.close();
        locEout.close();
    }

    @Override
    public void processGetfieldPrimitive(int e, int t, int b, int f) { 
        if (e >= 0 && b != 0) processHeapRd(e, b);
    }

    @Override
    public void processAloadPrimitive(int e, int t, int b, int i) { 
        if (e >= 0 && b != 0) processHeapRd(e, b);
    }

    @Override
    public void processGetfieldReference(int e, int t, int b, int f, int o) { 
        if (e >= 0 && b != 0) processHeapRd(e, b);
    }

    @Override
    public void processAloadReference(int e, int t, int b, int i, int o) { 
        if (e >= 0 && b != 0) processHeapRd(e, b);
    }

    @Override
    public void processPutfieldPrimitive(int e, int t, int b, int f) {
        if (e >= 0 && b != 0) processHeapRd(e, b);
    }

    @Override
    public void processAstorePrimitive(int e, int t, int b, int i) {
        if (e >= 0 && b != 0) processHeapRd(e, b);
    }

    @Override
    public void processPutfieldReference(int e, int t, int b, int f, int o) {
        if (e >= 0 && b != 0 && f >= 0) processHeapWr(e, b, f, o);
    }

    @Override
    public void processAstoreReference(int e, int t, int b, int i, int o) {
        if (e >= 0 && b != 0 && i >= 0) processHeapWr(e, b, smashArrayElems ? 0 : i + numF, o);
    }

    @Override
    public void processPutstaticReference(int e, int t, int b, int f, int o) { 
        if (o != 0) markAndPropEsc(o);
    }

    @Override
    public void processThreadStart(int p, int t, int o) { 
        if (o != 0) markAndPropEsc(o);
    }

    protected void processHeapRd(int e, int b) {
        if (!chkE[e] || escE[e])
            return;
        accE[e] = true;
        if (escO.contains(b))
            escE[e] = true;
    }

    protected void processHeapWr(int e, int b, int f, int r) {
        processHeapRd(e, b);
        super.processHeapWr(b, f, r);
        if (escO.contains(b))
            markAndPropEsc(r);
    }

    protected void markAndPropEsc(int o) {
        if (escO.add(o)) {
            List<FldObj> l = O2FOlist.get(o);
            if (l != null) {
                for (FldObj fo : l)
                    markAndPropEsc(fo.o);
            }
        }
    }
}

