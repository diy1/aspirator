/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 * Licensed under the terms of the New BSD License.
 */
package chord.analyses.escape.hybrid.path;

import java.io.PrintWriter;
import java.util.List;

import joeq.Compiler.Quad.Quad;

import chord.analyses.dynamic.FldObj;
import chord.analyses.escape.ThrEscException;
import chord.analyses.escape.dynamic.DynamicThreadEscapeAnalysis;
import chord.analyses.alloc.DomH;
import chord.project.Chord;
import chord.project.OutDirUtils;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.IntArraySet;

/**
 * Thread-escape path analysis.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "path-thresc-java",
    consumes = { "queryE" },
    produces = { "dynAccE", "dynEscE", "dynLocE", "locEH" }, 
    namesOfSigns = { "dynAccE", "dynEscE", "dynLocE", "locEH" },
    signs = { "E0", "E0", "E0", "E0,H0:E0_H0" }
)
public class ThreadEscapePathAnalysis extends DynamicThreadEscapeAnalysis {
    // accesses to be ignored because something went bad while computing
    // set of "relevant" allocation sites for it (subset of accE)
    private boolean[] badE;
    // map from each access deemed thread-local to set of allocation sites
    // deemed "relevant" to proving it
    private IntArraySet[] EtoLocHset;
    // visited allocation sites
    private boolean[] accH;
     // contains only non-null objects
    private final IntArraySet tmpO = new IntArraySet();
    private final IntArraySet tmpH = new IntArraySet();
    private DomH domH;

    @Override
    public boolean keepO2H() { return true; }
    
    @Override
    public boolean keepInv() { return true; }

    @Override
    public void initAllPasses() {
        super.initAllPasses();
        domH = (DomH) ClassicProject.g().getTrgt("H");
        ClassicProject.g().runTask(domH);
        badE = new boolean[domE.size()];
        EtoLocHset = new IntArraySet[domE.size()];
        accH = new boolean[domH.size()];
    }

    @Override
    public void doneAllPasses() {
        int numAccH = 0;
        for (int h = 0; h < domH.size(); h++) {
            if (accH[h]) numAccH++;
        }
        System.out.println("numAccH: " + numAccH);

        ProgramRel  accErel  = (ProgramRel) ClassicProject.g().getTrgt("dynAccE");
        ProgramRel  escErel  = (ProgramRel) ClassicProject.g().getTrgt("dynEscE");
        ProgramRel  locErel  = (ProgramRel) ClassicProject.g().getTrgt("dynLocE");
        ProgramRel  locEHrel = (ProgramRel) ClassicProject.g().getTrgt("locEH");
        PrintWriter accEout  = OutDirUtils.newPrintWriter("shape_pathAccE.txt");
        PrintWriter badEout  = OutDirUtils.newPrintWriter("shape_pathBadE.txt");
        PrintWriter escEout  = OutDirUtils.newPrintWriter("shape_pathEscE.txt");
        PrintWriter locEHout = OutDirUtils.newPrintWriter("shape_pathLocEH.txt");
        accErel.zero();
        escErel.zero();
        locErel.zero();
        locEHrel.zero();
        for (int e = 0; e < domE.size(); e++) {
            if (accE[e]) {
                accErel.add(e);
                String estr = domE.get(e).toVerboseStr();
                accEout.println(estr);
                if (badE[e]) {
                    badEout.println(estr);
                } else if (escE[e]) {
                    escErel.add(e);
                    escEout.println(estr);
                } else {
                    locErel.add(e);
                    locEHout.println(estr);
                    IntArraySet hs = EtoLocHset[e];
                    if (hs != null) {
                        int n = hs.size();
                        for (int i = 0; i < n; i++) {
                            int h = hs.get(i);
                            locEHrel.add(e, h);
                            String hstr = ((Quad) domH.get(h)).toVerboseStr();
                            locEHout.println("#" + hstr);
                        }
                    }
                }
            }
        }
        accEout.close();
        badEout.close();
        escEout.close();
        locEHout.close();
        accErel.save();
        escErel.save();
        locErel.save();
        locEHrel.save();
    }

    @Override
    protected void processNew(int h, int o) {
        super.processNew(h, o);
        if (o != 0 && h >= 0) accH[h] = true;
    }

    // assumes 'b' != 0 and 'tmpO' and 'tmpH' are not needed
    private void computeTC(int b) throws ThrEscException {
        tmpO.clear();
        tmpH.clear();
        int h = O2H.get(b);
        if (h == 0)
            throw new ThrEscException();
        tmpO.add(b);
        tmpH.add(h);
        // Note: tmpO.size() can change in body of below loop!
        for (int i = 0; i < tmpO.size(); i++) {
            int o = tmpO.get(i);
            List<FldObj> foList = O2FOlistInv.get(o);
            if (foList != null) {
                int n = foList.size();
                for (int j = 0; j < n; j++) {
                    FldObj fo = foList.get(j);
                    int o2 = fo.o;
                    if (tmpO.add(o2)) {
                        int h2 = O2H.get(o2);
                        if (h2 == 0)
                            throw new ThrEscException();
                        tmpH.add(h2);
                    }
                }
            }
        }
    }

    @Override
    protected void processHeapRd(int e, int b) {
        if (!chkE[e] || escE[e] || badE[e])
            return;
        accE[e] = true;
        if (escO.contains(b)) {
            escE[e] = true;
            return;
        }
        try {
            computeTC(b);
        } catch (ThrEscException ex) {
            badE[e] = true;
            return;
        }
        IntArraySet hs = EtoLocHset[e];
        if (hs == null) {
            hs = new IntArraySet();
            EtoLocHset[e] = hs;
        }
        int n = tmpH.size();
        for (int i = 0; i < n; i++) {
            int h = tmpH.get(i);
            hs.add(h);
        }
    }
}
