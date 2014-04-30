package chord.analyses.deadlock;

import java.io.PrintWriter;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;

import chord.project.Config;
import chord.program.Program;
import chord.project.ClassicProject;
import chord.project.Chord;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

import chord.util.ArraySet;
import chord.util.graph.IPathVisitor;
import chord.util.graph.ShortestPathBuilder;
import chord.analyses.alias.CIObj;
import chord.analyses.alias.ICICG;
import chord.analyses.thread.ThrSenCICGAnalysis;
import chord.analyses.alias.DomO;
import chord.analyses.alloc.DomH;
import chord.bddbddb.Rel.RelView;
import chord.analyses.thread.DomA;
import chord.analyses.invk.DomI;
import chord.analyses.lock.DomL;
import chord.analyses.method.DomM;
import chord.util.SetUtils;

/**
 * Static deadlock analysis.
 * <p>
 * Outputs relation 'deadlock' containing each tuple (a1,l1,l2,a2,l3,l4) denoting a possible
 * deadlock between abstract thread a1, which acquires a lock at l1 followed by a lock at l2,
 * and abstract thread a2, which acquires a lock at l3 followed by a lock at l4.
 * <p>
 * Recognized system properties:
 * <ul>
 *   <li>chord.deadlock.exclude.escaping (default is false).</li>
 *   <li>chord.deadlock.exclude.parallel (default is false).</li>
 *   <li>chord.deadlock.exclude.nonreent (default is false).</li>
 *   <li>chord.deadlock.exclude.nongrded (default is false).</li>
 *   <li>chord.print.results (default is false).</li>
 * </ul>
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(name="deadlock-java", consumes = { "syncLH" })
public class DeadlockAnalysis extends JavaAnalysis { 
	// Ding: JavaAnalysis: most general template for writing an analysis
    private DomA domA; // DomA? - abstract thread
    private DomH domH; // Object allocation quad.
    private DomI domI; // Method invocation quads.
    private DomL domL; // Domain of all lock acquire points, including monitorenter quads and entry basic blocks of synchronized methods.
    private DomM domM; // Domain of methods
    private ProgramRel relDeadlock;
    private ProgramRel relSyncLH;
    private ICICG thrSenCICG;
    private final Map<jq_Method, Set<jq_Method>> MMmap = new HashMap<jq_Method, Set<jq_Method>>();

    public void run() {
        boolean excludeParallel = Boolean.getBoolean("chord.deadlock.exclude.parallel");
        boolean excludeEscaping = Boolean.getBoolean("chord.deadlock.exclude.escaping");
        boolean excludeNonreent = Boolean.getBoolean("chord.deadlock.exclude.nonreent");
        boolean excludeNongrded = Boolean.getBoolean("chord.deadlock.exclude.nongrded");

        System.out.println("Ding: excludeParallel: " + excludeParallel
        					+ "excludeEscaping: " + excludeEscaping
        					+ "excludeNonreent: " + excludeNonreent
        					+ "excludeNongrded: " + excludeNongrded);
        domA = (DomA) ClassicProject.g().getTrgt("A");
        domH = (DomH) ClassicProject.g().getTrgt("H");
        domI = (DomI) ClassicProject.g().getTrgt("I");
        domL = (DomL) ClassicProject.g().getTrgt("L");
        domM = (DomM) ClassicProject.g().getTrgt("M");
        
        relDeadlock = (ProgramRel) ClassicProject.g().getTrgt("deadlock");
        relSyncLH   = (ProgramRel) ClassicProject.g().getTrgt("syncLH");

        ThrSenCICGAnalysis thrSenCICGAnalysis =
            (ThrSenCICGAnalysis) ClassicProject.g().getTrgt("thrsen-cicg-java");
        ClassicProject.g().runTask(thrSenCICGAnalysis);
        thrSenCICG = thrSenCICGAnalysis.getCallGraph();

        if (excludeParallel) {     
            System.out.println("Ding: deadlock-parallel-exclude-dlog is run");
            ClassicProject.g().runTask("deadlock-parallel-exclude-dlog");
        }
        else {
            System.out.println("Ding: deadlock-parallel-include-dlog is run");
            ClassicProject.g().runTask("deadlock-parallel-include-dlog"); // this is run by default
        }
        
        if (excludeEscaping) {
            System.out.println("Ding: deadlock-escaping-exclude-dlog is run");
            ClassicProject.g().runTask("deadlock-escaping-exclude-dlog");
        }
        else {
            System.out.println("Ding: deadlock-escaping-include-dlog is run");
            ClassicProject.g().runTask("deadlock-escaping-include-dlog"); // this is run by default
        }
        
        if (excludeNonreent) {
            System.out.println("Ding: deadlock-nonreent-exclude-dlog is run");
            ClassicProject.g().runTask("deadlock-nonreent-exclude-dlog");
        }
        else {
            System.out.println("Ding: deadlock-nonreent-include-dlog is run");
            ClassicProject.g().runTask("deadlock-nonreent-include-dlog"); // this is run
        }
        
        if (excludeNongrded) {
            System.out.println("Ding: deadlock-nongrded-exclude-dlog is run");
            ClassicProject.g().runTask("deadlock-nongrded-exclude-dlog");
        }
        else {
            System.out.println("Ding: deadlock-nongrded-include-dlog is run");
            ClassicProject.g().runTask("deadlock-nongrded-include-dlog"); // this is run
        }
        ClassicProject.g().runTask("deadlock-dlog");

        if (Config.printResults)
            printResults();
    }

    private CIObj getPointsTo(int lIdx) {
        RelView view = relSyncLH.getView();
        view.selectAndDelete(0, lIdx);
        Iterable<Object> objs = view.getAry1ValTuples();
        Set<Quad> pts = SetUtils.newSet(view.size());
        for (Object o : objs)
            pts.add((Quad) o);
        view.free();
        return new CIObj(pts);
    }
    
    private void printResults() {
        final DomO domO = new DomO();
        domO.setName("O");
        
        PrintWriter out;

        relDeadlock.load();
        relSyncLH.load();

        out = OutDirUtils.newPrintWriter("deadlocklist.xml");
        out.println("<deadlocklist>");
        for (Object[] tuple : relDeadlock.getAryNValTuples()) {
            jq_Method t1Val = (jq_Method) tuple[0];
            Inst l1Val = (Inst) tuple[1];
            Inst l2Val = (Inst) tuple[2];
            jq_Method t2Val = (jq_Method) tuple[3];
            Inst l3Val = (Inst) tuple[4];
            Inst l4Val = (Inst) tuple[5];
            int l1 = domL.indexOf(l1Val);
            int l2 = domL.indexOf(l2Val);
            int l3 = domL.indexOf(l3Val);
            int l4 = domL.indexOf(l4Val);
            // require l1,l2 <= l3,l4 and if not switch
            if (l1 > l3 || (l1 == l3 && l2 > l4)) {
                {
                    int tmp;
                    tmp = l1; l1 = l3; l3 = tmp;
                    tmp = l2; l2 = l4; l4 = tmp;
                }
                {
                    Inst tmp;
                    tmp = l1Val; l1Val = l3Val; l3Val = tmp;
                    tmp = l2Val; l2Val = l4Val; l4Val = tmp;
                }
                {
                    jq_Method tmp;
                    tmp = t1Val; t1Val = t2Val; t2Val = tmp;
                }
            }
            int t1 = domA.indexOf(t1Val);
            int t2 = domA.indexOf(t2Val);
            int t1m = domM.indexOf(t1Val);
            int t2m = domM.indexOf(t2Val);
            jq_Method m1Val = l1Val.getMethod();
            jq_Method m2Val = l2Val.getMethod();
            jq_Method m3Val = l3Val.getMethod();
            jq_Method m4Val = l4Val.getMethod();
            int m1 = domM.indexOf(m1Val);
            int m2 = domM.indexOf(m2Val);
            int m3 = domM.indexOf(m3Val);
            int m4 = domM.indexOf(m4Val);
            CIObj o1Val = getPointsTo(l1);
            CIObj o2Val = getPointsTo(l2);
            CIObj o3Val = getPointsTo(l3);
            CIObj o4Val = getPointsTo(l4);
            int o1 = domO.getOrAdd(o1Val);
            int o2 = domO.getOrAdd(o2Val);
            int o3 = domO.getOrAdd(o3Val);
            int o4 = domO.getOrAdd(o4Val);
            addToMMmap(t1Val, m1Val);
            addToMMmap(t2Val, m3Val);
            addToMMmap(m1Val, m2Val);
            addToMMmap(m3Val, m4Val);
            out.println("<deadlock " +
                "group=\"" + l1 + "_" + l2 + "_" + l3 + "_" + l4 + "\" " +
                "T1id=\"A" + t1 + "\" T2id=\"A" + t2 + "\" " +
                "M1id=\"M" + m1 + "\" L1id=\"L" + l1 + "\" O1id=\"O" + o1 + "\" " +
                "M2id=\"M" + m2 + "\" L2id=\"L" + l2 + "\" O2id=\"O" + o2 + "\" " +
                "M3id=\"M" + m3 + "\" L3id=\"L" + l3 + "\" O3id=\"O" + o3 + "\" " +
                "M4id=\"M" + m4 + "\" L4id=\"L" + l4 + "\" O4id=\"O" + o4 + "\"/>");
        }
        relDeadlock.close();
        relSyncLH.close();
        out.println("</deadlocklist>");
        out.close();        
        
        IPathVisitor<jq_Method> visitor = new IPathVisitor<jq_Method>() {
            public String visit(jq_Method srcM, jq_Method dstM) {
                Set<Quad> insts = thrSenCICG.getLabels(srcM, dstM);
                for (Quad inst : insts) {
                    return "<elem Iid=\"I" + domI.indexOf(inst) + "\"/>";
                }
                return "";
            }
        };

        out = OutDirUtils.newPrintWriter("MMlist.xml");
        out.println("<MMlist>");
        
        for (jq_Method m1 : MMmap.keySet()) {
            int mIdx1 = domM.indexOf(m1);
            Set<jq_Method> mSet = MMmap.get(m1);
            ShortestPathBuilder<jq_Method> builder = new ShortestPathBuilder(thrSenCICG, m1, visitor);
            for (jq_Method m2 : mSet) {
                int mIdx2 = domM.indexOf(m2);
                out.println("<MM M1id=\"M" + mIdx1 + "\" M2id=\"M" + mIdx2 + "\">");
                String path = builder.getShortestPathTo(m2);
                out.println("<path>");
                out.println(path);
                out.println("</path>");
                out.println("</MM>");
            }
        }
        out.println("</MMlist>");
        out.close();
        
        domO.saveToXMLFile();
        domA.saveToXMLFile();
        domH.saveToXMLFile();
        domI.saveToXMLFile();
        domM.saveToXMLFile();
        domL.saveToXMLFile();

        OutDirUtils.copyResourceByName("web/style.css");
        OutDirUtils.copyResourceByName("chord/analyses/method/Mlist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/method/M.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/lock/Llist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/alloc/Hlist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/alloc/H.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/invk/Ilist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/invk/I.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/thread/Alist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/thread/A.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/alias/Olist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/alias/O.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/deadlock/web/results.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/deadlock/web/results.xml");
        OutDirUtils.copyResourceByName("chord/analyses/deadlock/web/group.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/deadlock/web/paths.xsl");

        OutDirUtils.runSaxon("results.xml", "group.xsl");
        OutDirUtils.runSaxon("results.xml", "paths.xsl");

        Program.g().HTMLizeJavaSrcFiles();
    }

    private void addToMMmap(jq_Method m1, jq_Method m2) {
        Set<jq_Method> s = MMmap.get(m1);
        if (s == null) {
            s = new ArraySet<jq_Method>();
            MMmap.put(m1, s);
        }
        s.add(m2);
    }
}
