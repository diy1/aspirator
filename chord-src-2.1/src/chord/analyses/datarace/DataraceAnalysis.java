package chord.analyses.datarace;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.CIObj;
import chord.analyses.alias.DomO;
import chord.analyses.alias.ICICG;
import chord.analyses.thread.ThrSenCICGAnalysis;
import chord.analyses.alloc.DomH;
import chord.analyses.thread.DomA;
import chord.bddbddb.Rel.RelView;
import chord.analyses.field.DomF;
import chord.analyses.heapacc.DomE;
import chord.analyses.invk.DomI;
import chord.analyses.lock.DomL;
import chord.analyses.method.DomM;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.Config;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramDom;
import chord.project.analyses.ProgramRel;
import chord.util.ArraySet;
import chord.util.SetUtils;
import chord.util.graph.IPathVisitor;
import chord.util.graph.ShortestPathBuilder;
import chord.util.tuple.object.Pair;

/**
 * Static datarace analysis.
 * <p>
 * Outputs relation 'datarace' containing each tuple (a1,e1,a2,e2) denoting a possible race between abstract threads
 * a1 and a2 executing accesses e1 and e2, respectively.
 * <p>
 * Recognized system properties:
 * <ul>
 *   <li>chord.datarace.exclude.init (default is true): Suppress checking races on accesses in constructors.</li>
 *   <li>chord.datarace.exclude.eqth (default is true): Suppress checking races between the same abstract thread.</li>
 *   <li>chord.datarace.exclude.escaping (default is false): Suppress the thread-escape analysis stage.</li>
 *   <li>chord.datarace.exclude.parallel (default is false): Suppress the may-happen-in-parallel analysis stage.</li>
 *   <li>chord.datarace.exclude.nongrded (default is false): Suppress the lockset analysis stage.</li>
 *   <li>chord.print.results (default is false): Print race results in HTML.</li>
 * </ul>
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(name="datarace-java")
public class DataraceAnalysis extends JavaAnalysis {
    private DomM domM;
    private DomI domI;
    private DomF domF;
    private DomE domE;
    private DomA domA;
    private DomH domH;
    private DomL domL;
    private ThrSenCICGAnalysis thrSenCICGAnalysis;

    private void init() {
        domM = (DomM) ClassicProject.g().getTrgt("M");
        domI = (DomI) ClassicProject.g().getTrgt("I");
        domF = (DomF) ClassicProject.g().getTrgt("F");
        domE = (DomE) ClassicProject.g().getTrgt("E");
        domA = (DomA) ClassicProject.g().getTrgt("A");
        domH = (DomH) ClassicProject.g().getTrgt("H");
        domL = (DomL) ClassicProject.g().getTrgt("L");
        thrSenCICGAnalysis = (ThrSenCICGAnalysis) ClassicProject.g().getTrgt("thrsen-cicg-java");
    }

    public void run() {
        boolean excludeParallel = Boolean.getBoolean("chord.datarace.exclude.parallel");
        boolean excludeEscaping = Boolean.getBoolean("chord.datarace.exclude.escaping");
        boolean excludeNongrded = Boolean.getBoolean("chord.datarace.exclude.nongrded");

        init();

        if (excludeParallel)
            ClassicProject.g().runTask("datarace-parallel-exclude-dlog");
        else
            ClassicProject.g().runTask("datarace-parallel-include-dlog");
        if (excludeEscaping)
            ClassicProject.g().runTask("datarace-escaping-exclude-dlog");
        else
            ClassicProject.g().runTask("datarace-escaping-include-dlog");
        if (excludeNongrded)
            ClassicProject.g().runTask("datarace-nongrded-exclude-dlog");
        else
            ClassicProject.g().runTask("datarace-nongrded-include-dlog");
        ClassicProject.g().runTask("datarace-dlog");
        
        if (Config.printResults)
            printResults();
    }

    private void printResults() {
        ClassicProject.g().runTask(thrSenCICGAnalysis);
        final ICICG thrSenCICG = thrSenCICGAnalysis.getCallGraph();
        final ProgramDom<Pair<jq_Method, Quad>> domTE = new ProgramDom<Pair<jq_Method, Quad>>();
        domTE.setName("TE");
        final DomO domO = new DomO();
        domO.setName("O");

        PrintWriter out;

        out = OutDirUtils.newPrintWriter("dataracelist.xml");
        out.println("<dataracelist>");
        final ProgramRel relUltimateRace = (ProgramRel) ClassicProject.g().getTrgt("ultimateRace");
        relUltimateRace.load();
        final ProgramRel relRaceEEH = (ProgramRel) ClassicProject.g().getTrgt("raceEEH");
        relRaceEEH.load();
        final Iterable<chord.util.tuple.object.Quad<jq_Method, Quad, jq_Method, Quad>> tuples =
            relUltimateRace.getAry4ValTuples();
        for (chord.util.tuple.object.Quad<jq_Method, Quad, jq_Method, Quad> tuple : tuples) {
            int te1 = domTE.getOrAdd(new Pair<jq_Method, Quad>(tuple.val0, tuple.val1));
            int te2 = domTE.getOrAdd(new Pair<jq_Method, Quad>(tuple.val2, tuple.val3));
            RelView view = relRaceEEH.getView();
            view.selectAndDelete(0, tuple.val1);
            view.selectAndDelete(1, tuple.val3);
            Set<Quad> pts = new ArraySet<Quad>(view.size());
            Iterable<Object> res = view.getAry1ValTuples();
            for (Object o : res)
                pts.add((Quad) o);
            view.free();
            int o = domO.getOrAdd(new CIObj(pts));
            jq_Field fld = tuple.val1.getField();
            int f = domF.indexOf(fld);
            out.println("<datarace Oid=\"O" + o + "\" Fid=\"F" + f + "\" " +
                "TE1id=\"TE" + te1 + "\" "  + "TE2id=\"TE" + te2 + "\"/>");
        }
        relUltimateRace.close();
        relRaceEEH.close();
        out.println("</dataracelist>");
        out.close();

        ClassicProject.g().runTask("LI-dlog");
        ClassicProject.g().runTask("LE-dlog");
        ClassicProject.g().runTask("syncLH-dlog");
        final ProgramRel relLI = (ProgramRel) ClassicProject.g().getTrgt("LI");
        final ProgramRel relLE = (ProgramRel) ClassicProject.g().getTrgt("LE");
        final ProgramRel relSyncLH = (ProgramRel) ClassicProject.g().getTrgt("syncLH");
        relLI.load();
        relLE.load();
        relSyncLH.load();

        final Map<jq_Method, ShortestPathBuilder<jq_Method>> srcNodeToSPB =
            new HashMap<jq_Method, ShortestPathBuilder<jq_Method>>();

        final IPathVisitor<jq_Method> visitor = new IPathVisitor<jq_Method>() {
            public String visit(jq_Method srcM, jq_Method dstM) {
                Set<Quad> insts = thrSenCICG.getLabels(srcM, dstM);
                int mIdx = domM.indexOf(srcM);
                String lockStr = "";
                Quad inst = insts.iterator().next();
                int iIdx = domI.indexOf(inst);
                RelView view = relLI.getView();
                view.selectAndDelete(1, iIdx);
                Iterable<Inst> locks = view.getAry1ValTuples();
                for (Inst lock : locks) {
                    int lIdx = domL.indexOf(lock);
                    RelView view2 = relSyncLH.getView();
                    view2.selectAndDelete(0, lIdx);
                    Iterable<Object> ctxts = view2.getAry1ValTuples();
                    Set<Quad> pts = SetUtils.newSet(view2.size());
                    for (Object o : ctxts)
                        pts.add((Quad) o);
                    int oIdx = domO.getOrAdd(new CIObj(pts));
                    view2.free();
                    lockStr += "<lock Lid=\"L" + lIdx + "\" Mid=\"M" +
                        mIdx + "\" Oid=\"O" + oIdx + "\"/>";
                }
                view.free();
                return lockStr + "<elem Iid=\"I" + iIdx + "\"/>";
            }
        };

        out = OutDirUtils.newPrintWriter("TElist.xml");
        out.println("<TElist>");
        for (Pair<jq_Method, Quad> te : domTE) {
            jq_Method srcM = te.val0;
            Quad heapInst = te.val1;
            int eIdx = domE.indexOf(heapInst);
            out.println("<TE id=\"TE" + domTE.indexOf(te) + "\" " +
                "Tid=\"A" + domA.indexOf(srcM)    + "\" " +
                "Eid=\"E" + eIdx + "\">");
            jq_Method dstM = heapInst.getMethod();
            int mIdx = domM.indexOf(dstM);
            RelView view = relLE.getView();
            view.selectAndDelete(1, eIdx);
            Iterable<Inst> locks = view.getAry1ValTuples();
            for (Inst lock : locks) {
                int lIdx = domL.indexOf(lock);
                RelView view2 = relSyncLH.getView();
                view2.selectAndDelete(0, lIdx);
                Iterable<Object> objs = view2.getAry1ValTuples();
                Set<Quad> pts = SetUtils.newSet(view2.size());
                for (Object o : objs)
                    pts.add((Quad) o);
                int oIdx = domO.getOrAdd(new CIObj(pts));
                view2.free();
                out.println("<lock Lid=\"L" + lIdx + "\" Mid=\"M" +
                    mIdx + "\" Oid=\"O" + oIdx + "\"/>");
            }
            view.free();
            ShortestPathBuilder<jq_Method> spb = srcNodeToSPB.get(srcM);
            if (spb == null) {
                spb = new ShortestPathBuilder<jq_Method>(thrSenCICG, srcM, visitor);
                srcNodeToSPB.put(srcM, spb);
            }
            String path = spb.getShortestPathTo(dstM);
            out.println("<path>");
            out.println(path);
            out.println("</path>");
            out.println("</TE>");
        }
        out.println("</TElist>");
        out.close();

        relLI.close();
        relLE.close();
        relSyncLH.close();

        domO.saveToXMLFile();
        domA.saveToXMLFile();
        domH.saveToXMLFile();
        domI.saveToXMLFile();
        domM.saveToXMLFile();
        domE.saveToXMLFile();
        domF.saveToXMLFile();
        domL.saveToXMLFile();

        OutDirUtils.copyResourceByName("web/style.css");
        OutDirUtils.copyResourceByName("chord/analyses/method/Mlist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/method/M.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/lock/Llist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/alloc/Hlist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/alloc/H.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/invk/Ilist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/invk/I.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/heapacc/Elist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/heapacc/E.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/field/Flist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/field/F.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/thread/Alist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/thread/A.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/alias/Olist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/alias/O.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/datarace/web/results.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/datarace/web/results.xml");
        OutDirUtils.copyResourceByName("chord/analyses/datarace/web/group.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/datarace/web/paths.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/datarace/web/races.xsl");

        OutDirUtils.runSaxon("results.xml", "group.xsl");
        OutDirUtils.runSaxon("results.xml", "paths.xsl");
        OutDirUtils.runSaxon("results.xml", "races.xsl");

        Program.g().HTMLizeJavaSrcFiles();
    }
}
