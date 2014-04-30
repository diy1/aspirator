package chord.analyses.escape.hybrid.full;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.File;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.EntryOrExitBasicBlock;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.THROW_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CICGAnalysis;
import chord.analyses.alias.ICICG;
import chord.analyses.alloc.DomH;
import chord.analyses.escape.ThrEscException;
import chord.analyses.field.DomF;
import chord.analyses.heapacc.DomE;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.var.DomV;
import chord.bddbddb.Rel.IntPairIterable;
import chord.program.Loc;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.Messages;
import chord.project.OutDirUtils;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.rhs.RHSAnalysis;
import chord.project.analyses.rhs.TimeoutException;
import chord.project.analyses.rhs.IEdge;
import chord.project.analyses.rhs.MergeKind;
import chord.project.analyses.rhs.IWrappedPE;
import chord.project.analyses.rhs.BackTraceIterator;
import chord.project.analyses.rhs.TraceKind;
import chord.util.ArraySet;
import chord.util.Timer;
import chord.util.tuple.integer.IntPair;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;

/**
 * Static thread-escape analysis.
 * 
 * Consumes relation locEH.
 * Produces files shape_fullEscE.txt, shape_fullLocE.txt, and results.html.
 * 
 * Relevant system properties:
 * - chord.escape.optimize = [true|false] (default = true)
 * - chord.escape.html = [true|false] (default = false)
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(name = "full-thresc-java", consumes = { "locEH" })
public class ThreadEscapeFullAnalysis extends RHSAnalysis<Edge, Edge> {
    private static final ArraySet<FldObj> emptyHeap = new ArraySet<FldObj>(0);
    private static final Obj[] emptyRetEnv = new Obj[] { Obj.EMTY };

    private boolean optimizeSumms;
    private boolean useBOTH;

    private static DomM domM;
    private static DomI domI;
    private static DomV domV;
    private static DomF domF;
    private static DomH domH;
    private static DomE domE;
    private int varId[];
    private TObjectIntHashMap<jq_Method> methToNumVars = new TObjectIntHashMap<jq_Method>();
    private TObjectIntHashMap<jq_Method> methToFstVar = new TObjectIntHashMap<jq_Method>();
    private ICICG cicg;

    private Set<Quad> allEscEs = new HashSet<Quad>();
    private Set<Quad> allLocEs = new HashSet<Quad>();
    private Set<Quad> currHs;
    private Set<Quad> currLocEs = new HashSet<Quad>();
    private Set<Quad> currEscEs = new HashSet<Quad>();
    private MyQuadVisitor qv = new MyQuadVisitor();
    private EscQuadVisitor eqv = new EscQuadVisitor();
    private jq_Method mainMethod;
    private jq_Method threadStartMethod;

    @Override
    public void run() {
        super.init();

        // continue configuring the analysis
        optimizeSumms = System.getProperty("chord.escape.optimize", "true").equals("true");
        String bothStr = System.getProperty("chord.escape.both", "true");
        if (bothStr.equals("true"))
            useBOTH = true;
        else if (bothStr.equals("false"))
            useBOTH = false;
        else
            Messages.fatal("Unknown value for property chord.escape.both: " + bothStr);
        if (!useBOTH && mergeKind == MergeKind.LOSSY)
            Messages.fatal("Cannot use chord.escape.both=false and chord.rhs.merge=lossy.");
        System.out.println("chord.escape.optimize=" + optimizeSumms);
        System.out.println("chord.escape.both=" + bothStr);
        // finished configuring the analysis

        Program program = Program.g();
        mainMethod = program.getMainMethod();
        threadStartMethod = program.getThreadStartMethod();
        domI = (DomI) ClassicProject.g().getTrgt("I");
        ClassicProject.g().runTask(domI);
        domM = (DomM) ClassicProject.g().getTrgt("M");
        ClassicProject.g().runTask(domM);
        domV = (DomV) ClassicProject.g().getTrgt("V");
        ClassicProject.g().runTask(domV);
        domF = (DomF) ClassicProject.g().getTrgt("F");
        ClassicProject.g().runTask(domF);
        domH = (DomH) ClassicProject.g().getTrgt("H");
        ClassicProject.g().runTask(domH);
        domE = (DomE) ClassicProject.g().getTrgt("E");
        ClassicProject.g().runTask(domE);

        Map<Quad, ArraySet<Quad>> e2hsMap = new HashMap<Quad, ArraySet<Quad>>();
        ProgramRel relLocEH = (ProgramRel) ClassicProject.g().getTrgt("locEH");
        relLocEH.load();
        IntPairIterable tuples = relLocEH.getAry2IntTuples();
        for (IntPair tuple : tuples) {
            int eIdx = tuple.idx0;
            int hIdx = tuple.idx1;
            Quad e = (Quad) domE.get(eIdx);
            Quad h = (Quad) domH.get(hIdx);
            ArraySet<Quad> hs = e2hsMap.get(e);
            if (hs == null) {
                hs = new ArraySet<Quad>();
                e2hsMap.put(e, hs);
            }
            hs.add(h);
        }
        relLocEH.close();

        Map<Set<Quad>, Set<Quad>> hs2esMap = new HashMap<Set<Quad>, Set<Quad>>();
        for (Map.Entry<Quad, ArraySet<Quad>> entry : e2hsMap.entrySet()) {
            Quad e = entry.getKey();
            Set<Quad> hs = entry.getValue();
            Set<Quad> es = hs2esMap.get(hs);
            if (es == null) {
                es = new ArraySet<Quad>();
                hs2esMap.put(hs, es);
            }
            es.add(e);
        }

        int numV = domV.size();
        varId = new int[numV];
        for (int vIdx = 0; vIdx < numV;) {
            Register v = domV.get(vIdx);
            jq_Method m = domV.getMethod(v);
            int n = m.getLiveRefVars().size();
            methToNumVars.put(m, n);
            methToFstVar.put(m, vIdx);
            // System.out.println("Method: " + m);
            for (int i = 0; i < n; i++) {
                varId[vIdx + i] = i;
            //    System.out.println("\t" + domV.get(vIdx + i));
            }
            vIdx += n;
        }

        boolean HTMLize = Boolean.getBoolean("chord.escape.html");

        String html = "";
        int pass = 0;
        for (Map.Entry<Set<Quad>, Set<Quad>> entry : hs2esMap.entrySet()) {
            currHs = entry.getKey();
            currLocEs = entry.getValue();
            currEscEs.clear();
            boolean timeOut = false;
            System.out.println("**************");
            System.out.println("currEs:");
            for (Quad q : currLocEs) {
                int x = domE.indexOf(q);
                System.out.println("\t" + q.toVerboseStr() + " " + x);
            }
            System.out.println("currHs:");
            for (Quad q : currHs)
                System.out.println("\t" + q.toVerboseStr());
            Timer timer = new Timer("thresc-shape-timer");
            timer.init();
            try {
                runPass();
            } catch (TimeoutException ex) {
                for (Quad q : currLocEs)
                    currEscEs.add(q);
                currLocEs.clear();
                timeOut = true;
            } catch (ThrEscException ex) {
                // do nothing
            }
            for (Quad q : currLocEs)
                System.out.println("LOC: " + q.toVerboseStr());
            // html += "LOC: " + q.getID() + ": " + toHTMLStr(pass,
            // q.getMethod()) + "<br>";
            for (Quad q : currEscEs)
                System.out.println("ESC: " + q.toVerboseStr());
            // html += "ESC: " + q.getID() + ": " + toHTMLStr(pass,
            // q.getMethod()) + "<br>";
            allLocEs.addAll(currLocEs);
            allEscEs.addAll(currEscEs);
            // printSummaries();
            if (HTMLize)
                printEdges(pass);
            if (traceKind != TraceKind.NONE && !timeOut) {
                for (Quad q : currEscEs) {
                    IWrappedPE<Edge, Edge> initWPE = getEscEdge(q);
                    printEscTrace(initWPE);
                }
            }
            timer.done();
            System.out.println(timer.getInclusiveTimeStr());
            pass++;
        }

        if (HTMLize) {
            PrintWriter w = OutDirUtils.newPrintWriter("main.html");
            w.println("<html><head></head><body>" + html + "</body></html>");
            w.close();
        }

        super.done();

        PrintWriter out;
        out = OutDirUtils.newPrintWriter("shape_fullEscE.txt");
        for (Quad e : allEscEs)
            out.println(e.toVerboseStr());
        out.close();
        out = OutDirUtils.newPrintWriter("shape_fullLocE.txt");
        for (Quad e : allLocEs)
            out.println(e.toVerboseStr());
        out.close();

        if (Config.printResults) {
            domE.saveToXMLFile();
            domH.saveToXMLFile();
            domM.saveToXMLFile();

            Map<Quad, String> map = new HashMap<Quad, String>();
            ProgramRel relEscE = (ProgramRel) ClassicProject.g().getTrgt("dynEscE");
            relEscE.load();
            Iterable<Quad> escEtuples = relEscE.getAry1ValTuples();
            for (Quad e : escEtuples)
                map.put(e, "<pathEsc Eid=\"E" + domE.indexOf(e) + "\"/>");
            relEscE.close();

            for (Quad e : allEscEs) {
                ArraySet<Quad> hs = e2hsMap.get(e);
                map.put(e, "<fullEsc Eid=\"E" + domE.indexOf(e) + "\" Hids=\"" + getXML(hs) + "\"/>");
            }
            for (Quad e : allLocEs) {
                ArraySet<Quad> hs = e2hsMap.get(e);
                map.put(e, "<fullLoc Eid=\"E" + domE.indexOf(e) + "\" Hids=\"" + getXML(hs) + "\"/>");
            }

            out = OutDirUtils.newPrintWriter("escapelist.xml");
            out.println("<escapelist>");
            for (int e = 0; e < domE.size(); e++) {
                String s = map.get(domE.get(e));
                if (s != null)
                    out.println(s);
            }
            out.println("</escapelist>");
            out.close();

            OutDirUtils.copyResourceByName("web/style.css");
            OutDirUtils.copyResourceByName("chord/analyses/method/Mlist.dtd");
            OutDirUtils.copyResourceByName("chord/analyses/heapacc/Elist.dtd");
            OutDirUtils.copyResourceByName("chord/analyses/heapacc/E.xsl");
            OutDirUtils.copyResourceByName("chord/analyses/alloc/Hlist.dtd");
            OutDirUtils.copyResourceByName("chord/analyses/alloc/H.xsl");
            OutDirUtils.copyResourceByName("chord/analyses/escape/web/results.xml");
            OutDirUtils.copyResourceByName("chord/analyses/escape/web/results.dtd");
            OutDirUtils.copyResourceByName("chord/analyses/escape/web/results.xsl");
            OutDirUtils.runSaxon("results.xml", "results.xsl");
            Program.g().HTMLizeJavaSrcFiles();
        }
    }

    private void printEscTrace(IWrappedPE<Edge, Edge> wpe) {
        BackTraceIterator<Edge, Edge> iter = this.getBackTraceIterator(wpe);
        File dir = new File(Config.outDirName, "traces");
        if (!dir.exists())
            dir.mkdir();
        PrintWriter w = OutDirUtils.newPrintWriter("traces" + "/" + domE.indexOf(wpe.getInst()) + ".html");
        w.println("<html><head></head><body>");
        while (iter.hasNext()) {
            String trioStr = toString((IWrappedPE) iter.next());
            w.println(trioStr);
        }
        w.println("</body></html>");
        w.close();
    }

    private String toString(IWrappedPE<Edge, Edge> wpe) {
        Inst inst = wpe.getInst();
        jq_Method m = inst.getMethod();
        String s = inst.toVerboseStr().replace("<", "&lt;").replace(">", "&gt;");
        s += "\n<pre>" + toString(wpe.getPE(), m) +
            // " [len = " + wpe.pathLength + ", slp = " + wpe.slPathLength + "]" +
            "</pre>";
        return s;
    }

    class EscQuadVisitor extends QuadVisitor.EmptyVisitor {
        public IWrappedPE<Edge, Edge> escEdge = null;
        @Override
        public void visitAStore(Quad obj) {
            findEscEdge(obj, AStore.getBase(obj));
        }
        @Override
        public void visitALoad(Quad obj) {
            findEscEdge(obj, ALoad.getBase(obj));
        }
        @Override
        public void visitGetfield(Quad obj) {
            findEscEdge(obj, Getfield.getBase(obj));
        }
        @Override
        public void visitPutfield(Quad obj) {
            findEscEdge(obj, Putfield.getBase(obj));
        }
        private void findEscEdge(Quad q, Operand bx) {
            if (!(bx instanceof RegisterOperand))
                throw new RuntimeException("Register operand expected!");
            RegisterOperand bo = (RegisterOperand) bx;
            int bIdx = getIdx(bo);
            Set<Edge> peSet = pathEdges.get(q);
            for (Edge pe : peSet) {
                Obj pts = pe.dstNode.env[bIdx];
                if (pts == Obj.ONLY_ESC || pts == Obj.BOTH) {
                    Pair<Inst, Edge> pair = new Pair<Inst, Edge>(q, pe);
                    escEdge = wpeMap.get(pair);
                    assert (escEdge != null);
                    return;
                }
            }
        }
    }

    private IWrappedPE<Edge, Edge> getEscEdge(Quad q) {
        eqv.escEdge = null;
        q.accept(eqv);
        return eqv.escEdge;
    }

    private String toHTMLStr(jq_Method m) {
        String s = m.toString().replace("<", "&lt;").replace(">", "&gt;");
        return "<a href=\"" + domM.indexOf(m) + ".html\">" + s + "</a>";
    }

    private String toHTMLStr(int pass, jq_Method m) {
        String s = m.toString().replace("<", "&lt;").replace(">", "&gt;");
        return "<a href=\"" + pass + "/" + domM.indexOf(m) + ".html\">" + s + "</a>";
    }

    private void printEdges(int pass) {
        File dir = new File(Config.outDirName, Integer.toString(pass));
        dir.mkdir();
        for (jq_Method m : summEdges.keySet()) {
            PrintWriter w = OutDirUtils.newPrintWriter(pass + "/" + domM.indexOf(m) + ".html");
            w.println("<html><head></head><body>");
            w.println("Method: " + toHTMLStr(m) + "<br>");
            w.println("<br>Callers:<br>");
            for (Quad q : cicg.getCallers(m)) {
                jq_Method m2 = q.getMethod();
                w.println(q.getID() + ": " + toHTMLStr(m2) + "<br>");
            }
            w.println("<br>Summary Edges:");
            Set<Edge> seSet = summEdges.get(m);
            ControlFlowGraph cfg = m.getCFG();
            w.println("<pre>");
            w.println("Register Factory: " + cfg.getRegisterFactory());
            for (BasicBlock bb : cfg.reversePostOrder()) {
                Iterator<BasicBlock> bbi;
                String s = bb.toString() + "(in: ";
                bbi = bb.getPredecessors().iterator();
                if (!bbi.hasNext())
                    s += "none";
                else {
                    s += bbi.next();
                    while (bbi.hasNext())
                        s += ", " + bbi.next();
                }
                s += ", out: ";
                bbi = bb.getSuccessors().iterator();
                if (!bbi.hasNext())
                    s += "none";
                else {
                    s += bbi.next();
                    while (bbi.hasNext())
                        s += ", " + bbi.next();
                }
                s += ")";
                for (Quad q : bb.getQuads()) {
                    s += "\n" + q;
                    if (q.getOperator() instanceof Invoke) {
                        Set<jq_Method> tgts = cicg.getTargets(q);
                        for (jq_Method m2 : tgts)
                            s += "\n" + toHTMLStr(m2);
                    }
                }
                w.println(s);
            }
            w.println("</pre>");
            for (BasicBlock bb : m.getCFG().reversePostOrder()) {
                if (bb.isEntry() || bb.isExit()) {
                    w.println(bb.isEntry() ? "ENTRY:" : "EXIT:");
                    Set<Edge> peSet = pathEdges.get((EntryOrExitBasicBlock) bb);
                } else {
                    for (Quad q : bb) {
                        w.println(q.getID() + ":");
                        Set<Edge> peSet = pathEdges.get(q);
                    }
                }
            }
            w.println("</body>");
            w.close();
        }
    }

    private String toString(Edge pe, jq_Method m) {
        SrcNode s = pe.srcNode;
        DstNode d = pe.dstNode;
        Obj[] se = s.env;
        Obj[] de = d.env;
        ArraySet<FldObj> sh = s.heap;
        ArraySet<FldObj> dh = d.heap;
        int fstVidx = methToFstVar.get(m);
        int n = se.length;
        String seStr = toString(se, fstVidx);
        String shStr = toString(sh);
        String deStr = toString(de, fstVidx);
        String dhStr = toString(dh);
        return "(" + seStr + "," + shStr + ") -> (" + deStr + "," + dhStr + ")";
    }

    private String getXML(ArraySet<Quad> hs) {
        String hIds = "";
        if (hs != null) {
            int n = hs.size();
            for (int i = 0; true; i++) {
                Quad h = hs.get(i);
                hIds += "H" + domH.indexOf(h);
                if (i == n - 1)
                    break;
                hIds += " ";
            }
        }
        return hIds;
    }

    @Override
    public ICICG getCallGraph() {
        if (cicg == null) {
            CICGAnalysis cicgAnalysis = (CICGAnalysis) ClassicProject.g().getTrgt("cicg-java");
            ClassicProject.g().runTask(cicgAnalysis);
            cicg = cicgAnalysis.getCallGraph();
        }
        return cicg;
    }

    // m is either the main method or the thread root method
    private Edge getRootPathEdge(jq_Method m) {
        assert (m == mainMethod || m == threadStartMethod || m.getName().toString().equals("<clinit>"));
        int n = methToNumVars.get(m);
        Obj[] env = new Obj[n];
        for (int i = 0; i < n; i++)
            env[i] = Obj.EMTY;
        if (m == threadStartMethod) {
            // arg of start method of java.lang.Thread escapes
            env[0] = Obj.ONLY_ESC;
        }
        SrcNode srcNode = new SrcNode(env, emptyHeap);
        DstNode dstNode = new DstNode(env, emptyHeap, false, false);
        Edge pe = new Edge(srcNode, dstNode);
        return pe;
    }

    @Override
    public Set<Pair<Loc, Edge>> getInitPathEdges() {
        Set<jq_Method> roots = cicg.getRoots();
        Set<Pair<Loc, Edge>> initPEs = new ArraySet<Pair<Loc, Edge>>(roots.size());
        for (jq_Method m : roots) {
            Edge pe = getRootPathEdge(m);
            EntryOrExitBasicBlock bb = m.getCFG().entry();
            Loc loc = new Loc(bb, -1);
            Pair<Loc, Edge> pair = new Pair<Loc, Edge>(loc, pe);
            initPEs.add(pair);
        }
        return initPEs;
    }

    @Override
    public Edge getInitPathEdge(Quad q, jq_Method m2, Edge pe) {
        Edge pe2;
        if (m2 == threadStartMethod) {
            // ignore pe
            pe2 = getRootPathEdge(m2);
        } else {
            DstNode dstNode = pe.dstNode;
            Obj[] dstEnv = dstNode.env;
            ParamListOperand args = Invoke.getParamList(q);
            int numArgs = args.length();
            int numVars = methToNumVars.get(m2);
            Obj[] env = new Obj[numVars];
            int z = 0;
            boolean allEsc = optimizeSumms ? true : false;
            for (int i = 0; i < numArgs; i++) {
                RegisterOperand ao = args.get(i);
                if (ao.getType().isReferenceType()) {
                    int aIdx = getIdx(ao);
                    Obj aPts = dstEnv[aIdx];
                    if (aPts != Obj.EMTY && aPts != Obj.ONLY_ESC)
                        allEsc = false;
                    env[z++] = aPts;
                }
            }
            while (z < numVars)
                env[z++] = Obj.EMTY;
            ArraySet<FldObj> dstHeap2 = allEsc ? emptyHeap : dstNode.heap;
            SrcNode srcNode2 = new SrcNode(env, dstHeap2);
            DstNode dstNode2 = new DstNode(env, dstHeap2, false, false);
            pe2 = new Edge(srcNode2, dstNode2);
        }
        return pe2;
    }

    @Override
    public Edge getMiscPathEdge(Quad q, Edge pe) {
        DstNode dstNode = pe.dstNode;
        qv.iDstNode = dstNode;
        qv.oDstNode = dstNode;
        q.accept(qv);
        DstNode dstNode2 = qv.oDstNode;
        return new Edge(pe.srcNode, dstNode2);
    }

    private Edge getForkPathEdge(Quad q, Edge pe) {
        DstNode dstNode = pe.dstNode;
        Obj[] iEnv = dstNode.env;
        RegisterOperand ao = Invoke.getParam(q, 0);
        int aIdx = getIdx(ao);
        Obj aPts = iEnv[aIdx];
        DstNode dstNode2;
        if (aPts == Obj.ONLY_ESC || aPts == Obj.EMTY)
            dstNode2 = dstNode;
        else
            dstNode2 = reset(dstNode);
        Edge pe2 = new Edge(pe.srcNode, dstNode2);
        return pe2;
    }

    @Override
    public Edge getSummaryEdge(jq_Method m, Edge pe) {
        Edge se;
        DstNode dstNode = pe.dstNode;
        if (dstNode.isRetn)
            se = new Edge(pe.srcNode, dstNode);
        else {
            dstNode = new DstNode(emptyRetEnv, dstNode.heap, dstNode.isKill, true);
            se = new Edge(pe.srcNode, dstNode);
        }
        return se;
    }

    @Override
    public Edge getPECopy(Edge pe) {
        return new Edge(pe.srcNode, pe.dstNode);
    }

    @Override
    public Edge getSECopy(Edge pe) {
        return new Edge(pe.srcNode, pe.dstNode);
    }

    @Override
    public Edge getInvkPathEdge(Quad q, Edge clrPE, jq_Method m, Edge tgtSE) {
        if (m == threadStartMethod) {
            // ignore tgtSE
            return getForkPathEdge(q, clrPE);
        }
        DstNode clrDstNode = clrPE.dstNode;
        SrcNode tgtSrcNode = tgtSE.srcNode;
        Obj[] clrDstEnv = clrDstNode.env;
        Obj[] tgtSrcEnv = tgtSrcNode.env;
        ParamListOperand args = Invoke.getParamList(q);
        int numArgs = args.length();
        boolean allEsc = optimizeSumms ? true : false;
        for (int i = 0, fIdx = 0; i < numArgs; i++) {
            RegisterOperand ao = args.get(i);
            if (ao.getType().isReferenceType()) {
                int aIdx = getIdx(ao);
                Obj aPts = clrDstEnv[aIdx];
                Obj fPts = tgtSrcEnv[fIdx];
                if (aPts != fPts)
                    return null;
                if (aPts != Obj.EMTY && aPts != Obj.ONLY_ESC)
                    allEsc = false;
                fIdx++;
            }
        }
        ArraySet<FldObj> clrDstHeap = clrDstNode.heap;
        ArraySet<FldObj> tgtSrcHeap = tgtSrcNode.heap;
        DstNode tgtRetNode = tgtSE.dstNode;
        ArraySet<FldObj> tgtRetHeap = tgtRetNode.heap;
        ArraySet<FldObj> clrDstHeap2;
        if (allEsc) {
            assert (tgtSrcHeap == emptyHeap);
            clrDstHeap2 = new ArraySet<FldObj>(clrDstHeap);
            clrDstHeap2.addAll(tgtRetHeap);
        } else {
            if (!clrDstHeap.equals(tgtSrcHeap))
                return null;
            clrDstHeap2 = tgtRetHeap;
        }
        int n = clrDstEnv.length;
        Obj[] clrDstEnv2 = new Obj[n];
        RegisterOperand ro = Invoke.getDest(q);
        int rIdx = -1;
        if (ro != null && ro.getType().isReferenceType()) {
            rIdx = getIdx(ro);
            clrDstEnv2[rIdx] = tgtRetNode.env[0];
        }
        boolean isKill = tgtRetNode.isKill;
        if (allEsc || !isKill) {
            for (int i = 0; i < n; i++) {
                if (i != rIdx) {
                    clrDstEnv2[i] = clrDstEnv[i];
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                if (i != rIdx) {
                    if (clrDstEnv[i] == Obj.EMTY)
                        clrDstEnv2[i] = Obj.EMTY;
                    else
                        clrDstEnv2[i] = Obj.ONLY_ESC;
                }
            }
        }
        DstNode clrDstNode2 = new DstNode(clrDstEnv2, clrDstHeap2, isKill
                || clrDstNode.isKill, false);
        return new Edge(clrPE.srcNode, clrDstNode2);
    }

    public static void setDomF(DomF dom){
        domF = dom;
    }
    
    class MyQuadVisitor extends QuadVisitor.EmptyVisitor {
        DstNode iDstNode;
        DstNode oDstNode;

        @Override
        public void visitReturn(Quad q) {
            Obj[] oEnv = null;
            if (!(q.getOperator() instanceof THROW_A)) {
                Operand rx = Return.getSrc(q);
                if (rx instanceof RegisterOperand) {
                    RegisterOperand ro = (RegisterOperand) rx;
                    if (ro.getType().isReferenceType()) {
                        int rIdx = getIdx(ro);
                        Obj rPts = iDstNode.env[rIdx];
                        oEnv = new Obj[] { rPts };
                    }
                }
            }
            if (oEnv == null)
                oEnv = emptyRetEnv;
            oDstNode = new DstNode(oEnv, iDstNode.heap, iDstNode.isKill, true);
        }

        @Override
        public void visitCheckCast(Quad q) {
            visitMove(q);
        }

        @Override
        public void visitMove(Quad q) {
            RegisterOperand lo = Move.getDest(q);
            jq_Type t = lo.getType();
            if (!t.isReferenceType())
                return;
            Obj[] iEnv = iDstNode.env;
            int lIdx = getIdx(lo);
            Obj ilPts = iEnv[lIdx];
            Operand rx = Move.getSrc(q);
            Obj olPts;
            if (rx instanceof RegisterOperand) {
                RegisterOperand ro = (RegisterOperand) rx;
                int rIdx = getIdx(ro);
                olPts = iEnv[rIdx];
            } else
                olPts = Obj.EMTY;
            if (olPts == ilPts)
                return;
            Obj[] oEnv = copy(iEnv);
            oEnv[lIdx] = olPts;
            oDstNode = new DstNode(oEnv, iDstNode.heap, iDstNode.isKill, false);
        }

        @Override
        public void visitPhi(Quad q) {
            RegisterOperand lo = Phi.getDest(q);
            jq_Type t = lo.getType();
            if (t == null || !t.isReferenceType())
                return;
            Obj[] iEnv = iDstNode.env;
            ParamListOperand ros = Phi.getSrcs(q);
            int n = ros.length();
            Obj olPts = Obj.EMTY;
            for (int i = 0; i < n; i++) {
                RegisterOperand ro = ros.get(i);
                if (ro != null) {
                    int rIdx = getIdx(ro);
                    Obj rPts = iEnv[rIdx];
                    olPts = getObj(olPts, rPts);
                    if (!useBOTH && olPts == Obj.BOTH) {
                        oDstNode = reset(iDstNode);
                        return;
                    }
                }
            }
            int lIdx = getIdx(lo);
            Obj ilPts = iEnv[lIdx];
            if (olPts == ilPts)
                return;
            Obj[] oEnv = copy(iEnv);
            oEnv[lIdx] = olPts;
            oDstNode = new DstNode(oEnv, iDstNode.heap, iDstNode.isKill, false);
        }

        @Override
        public void visitALoad(Quad q) {
            if (currLocEs.contains(q))
                check(q, ALoad.getBase(q));
            Operator op = q.getOperator();
            if (!((ALoad) op).getType().isReferenceType())
                return;
            Obj[] iEnv = iDstNode.env;
            ArraySet<FldObj> iHeap = iDstNode.heap;
            RegisterOperand bo = (RegisterOperand) ALoad.getBase(q);
            int bIdx = getIdx(bo);
            Obj bPts = iEnv[bIdx];
            RegisterOperand lo = ALoad.getDest(q);
            int lIdx = getIdx(lo);
            Obj ilPts = iEnv[lIdx];
            Obj olPts = getPtsFromHeap(bPts, null, iHeap);
            if (olPts == ilPts)
                return;
            Obj[] oEnv = copy(iEnv);
            oEnv[lIdx] = olPts;
            oDstNode = new DstNode(oEnv, iHeap, iDstNode.isKill, false);
        }

        @Override
        public void visitGetfield(Quad q) {
            if (currLocEs.contains(q))
                check(q, Getfield.getBase(q));
            jq_Field f = Getfield.getField(q).getField();
            if (!f.getType().isReferenceType())
                return;
            Obj[] iEnv = iDstNode.env;
            ArraySet<FldObj> iHeap = iDstNode.heap;
            RegisterOperand lo = Getfield.getDest(q);
            int lIdx = getIdx(lo);
            Obj ilPts = iEnv[lIdx];
            Operand bx = Getfield.getBase(q);
            Obj olPts;
            if (bx instanceof RegisterOperand) {
                RegisterOperand bo = (RegisterOperand) bx;
                int bIdx = getIdx(bo);
                Obj bPts = iEnv[bIdx];
                olPts = getPtsFromHeap(bPts, f, iHeap);
            } else
                olPts = Obj.EMTY;
            if (olPts == ilPts)
                return;
            Obj[] oEnv = copy(iEnv);
            oEnv[lIdx] = olPts;
            oDstNode = new DstNode(oEnv, iHeap, iDstNode.isKill, false);
        }

        @Override
        public void visitAStore(Quad q) {
            if (currLocEs.contains(q))
                check(q, AStore.getBase(q));
            Operator op = q.getOperator();
            if (!((AStore) op).getType().isReferenceType())
                return;
            Operand rx = AStore.getValue(q);
            if (!(rx instanceof RegisterOperand))
                return;
            RegisterOperand bo = (RegisterOperand) AStore.getBase(q);
            RegisterOperand ro = (RegisterOperand) rx;
            Obj[] iEnv = iDstNode.env;
            int rIdx = getIdx(ro);
            Obj rPts = iEnv[rIdx];
            if (rPts == Obj.EMTY)
                return;
            int bIdx = getIdx(bo);
            Obj bPts = iEnv[bIdx];
            if (bPts == Obj.EMTY)
                return;
            processWrite(bPts, rPts, null);
        }

        @Override
        public void visitPutfield(Quad q) {
            if (currLocEs.contains(q))
                check(q, Putfield.getBase(q));
            jq_Field f = Putfield.getField(q).getField();
            if (!f.getType().isReferenceType())
                return;
            Operand rx = Putfield.getSrc(q);
            if (!(rx instanceof RegisterOperand))
                return;
            Operand bx = Putfield.getBase(q);
            if (!(bx instanceof RegisterOperand))
                return;
            Obj[] iEnv = iDstNode.env;
            RegisterOperand ro = (RegisterOperand) rx;
            int rIdx = getIdx(ro);
            Obj rPts = iEnv[rIdx];
            if (rPts == Obj.EMTY)
                return;
            RegisterOperand bo = (RegisterOperand) bx;
            int bIdx = getIdx(bo);
            Obj bPts = iEnv[bIdx];
            if (bPts == Obj.EMTY)
                return;
            processWrite(bPts, rPts, f);
        }

        private void processWrite(Obj bPts, Obj rPts, jq_Field f) {
            if ((bPts == Obj.ONLY_ESC || bPts == Obj.BOTH)
                    && (rPts == Obj.ONLY_LOC || rPts == Obj.BOTH)) {
                oDstNode = reset(iDstNode);
                return;
            }
            if (bPts == Obj.ONLY_LOC || bPts == Obj.BOTH) {
                ArraySet<FldObj> iHeap = iDstNode.heap;
                int n = iHeap.size();
                ArraySet<FldObj> oHeap = null;
                for (int i = 0; i < n; i++) {
                    FldObj fo = iHeap.get(i);
                    if (fo.f == f) {
                        boolean isLoc = fo.isLoc;
                        boolean isEsc = fo.isEsc;
                        Obj pts1 = getObj(isLoc, isEsc);
                        Obj pts2 = getObj(pts1, rPts);
                        boolean isLoc2 = isLoc(pts2);
                        boolean isEsc2 = isEsc(pts2);
                        if (isLoc == isLoc2 && isEsc == isEsc2)
                            return;
                        if (!useBOTH && pts2 == Obj.BOTH) {
                            oDstNode = reset(iDstNode);
                            return;
                        }
                        oHeap = new ArraySet<FldObj>(n);
                        for (int j = 0; j < i; j++)
                            oHeap.add(iHeap.get(j));
                        FldObj fo2 = new FldObj(f, isLoc2, isEsc2);
                        oHeap.add(fo2);
                        for (int j = i + 1; j < n; j++)
                            oHeap.add(iHeap.get(j));
                        break;
                    }
                }
                if (oHeap == null) {
                    oHeap = new ArraySet<FldObj>(iHeap);
                    boolean isLoc = isLoc(rPts);
                    boolean isEsc = isEsc(rPts);
                    FldObj fo = new FldObj(f, isLoc, isEsc);
                    oHeap.add(fo);
                }
                oDstNode = new DstNode(iDstNode.env, oHeap, iDstNode.isKill, false);
            }
        }

        @Override
        public void visitPutstatic(Quad q) {
            jq_Field f = Putstatic.getField(q).getField();
            if (!f.getType().isReferenceType())
                return;
            Operand rx = Putstatic.getSrc(q);
            if (!(rx instanceof RegisterOperand))
                return;
            Obj[] iEnv = iDstNode.env;
            RegisterOperand ro = (RegisterOperand) rx;
            int rIdx = getIdx(ro);
            Obj rPts = iEnv[rIdx];
            if (rPts == Obj.ONLY_ESC || rPts == Obj.EMTY)
                return;
            oDstNode = reset(iDstNode);
        }

        @Override
        public void visitGetstatic(Quad q) {
            jq_Field f = Getstatic.getField(q).getField();
            if (!f.getType().isReferenceType())
                return;
            Obj[] iEnv = iDstNode.env;
            RegisterOperand lo = Getstatic.getDest(q);
            int lIdx = getIdx(lo);
            if (iEnv[lIdx] == Obj.ONLY_ESC)
                return;
            Obj[] oEnv = copy(iEnv);
            oEnv[lIdx] = Obj.ONLY_ESC;
            oDstNode = new DstNode(oEnv, iDstNode.heap, iDstNode.isKill, false);
        }

        @Override
        public void visitNew(Quad q) {
            RegisterOperand vo = New.getDest(q);
            processAlloc(q, vo);
        }

        @Override
        public void visitNewArray(Quad q) {
            RegisterOperand vo = NewArray.getDest(q);
            processAlloc(q, vo);
        }

        @Override
        public void visitMultiNewArray(Quad q) {
            RegisterOperand vo = MultiNewArray.getDest(q);
            processAlloc(q, vo);
        }

        private void processAlloc(Quad q, RegisterOperand lo) {
            Obj[] iEnv = iDstNode.env;
            int lIdx = getIdx(lo);
            Obj ilPts = iEnv[lIdx];
            Obj olPts;
            if (!currHs.contains(q)) {
                olPts = Obj.ONLY_ESC;
            } else {
                olPts = Obj.ONLY_LOC;
            }
            if (ilPts == olPts)
                return;
            Obj[] oEnv = copy(iEnv);
            oEnv[lIdx] = olPts;
            oDstNode = new DstNode(oEnv, iDstNode.heap, iDstNode.isKill, false);
        }

        private void check(Quad q, Operand bx) {
            if (!(bx instanceof RegisterOperand))
                return;
            RegisterOperand bo = (RegisterOperand) bx;
            int bIdx = getIdx(bo);
            Obj pts = iDstNode.env[bIdx];
            if (pts == Obj.ONLY_ESC || pts == Obj.BOTH) {
                currLocEs.remove(q);
                currEscEs.add(q);
                if (currLocEs.size() == 0)
                    throw new ThrEscException();
            }
        }
    }

    private static Obj getPtsFromHeap(Obj bPts, jq_Field f, ArraySet<FldObj> heap) {
        if (bPts == Obj.EMTY || bPts == Obj.ONLY_ESC)
            return Obj.ONLY_ESC;  // in newest version of forward transfer functions, N.f = E
        Obj pts = null;
        int n = heap.size();
        for (int i = 0; i < n; i++) {
            FldObj fo = heap.get(i);
            if (fo.f == f) {
                pts = getObj(fo.isLoc, fo.isEsc);
                break;
            }
        }
        if (pts == null) {
            return (bPts == Obj.ONLY_LOC) ? Obj.EMTY : Obj.ONLY_ESC;
        } else {
            return (bPts == Obj.ONLY_LOC) ? pts : getObj(pts, Obj.ONLY_ESC);
        }
    }

    private static DstNode reset(DstNode in) {
        Obj[] iEnv = in.env;
        int n = iEnv.length;
        boolean change = false;
        for (int i = 0; i < n; i++) {
            Obj pts = iEnv[i];
            if (pts != Obj.ONLY_ESC && pts != Obj.EMTY) {
                change = true;
                break;
            }
        }
        Obj[] oEnv;
        if (change) {
            oEnv = new Obj[n];
            for (int i = 0; i < n; i++) {
                Obj pts = iEnv[i];
                oEnv[i] = (pts == Obj.EMTY) ? Obj.EMTY : Obj.ONLY_ESC;
            }
        } else {
            if (in.isKill && in.heap.isEmpty())
                return in;
            oEnv = iEnv;
        }
        return new DstNode(oEnv, emptyHeap, true, false);
    }

    /*****************************************************************
     * Frequently used functions
     *****************************************************************/

    private int getIdx(RegisterOperand ro) {
        Register r = ro.getRegister();
        int vIdx = domV.indexOf(r);
        return varId[vIdx];
    }

    private static Obj[] copy(Obj[] a) {
        int n = a.length;
        Obj[] b = new Obj[n];
        for (int i = 0; i < n; i++)
            b[i] = a[i];
        return b;
    }

    private static Obj getObj(boolean isLoc, boolean isEsc) {
        if (isLoc)
            return isEsc ? Obj.BOTH : Obj.ONLY_LOC;
        else
            return isEsc ? Obj.ONLY_ESC : Obj.EMTY;
    }

    private static Obj getObj(Obj o1, Obj o2) {
        if (o1 == o2)
            return o1;
        if (o1 == Obj.EMTY)
            return o2;
        if (o2 == Obj.EMTY)
            return o1;
        return Obj.BOTH;
    }

    private static boolean isLoc(Obj pts) {
        return pts == Obj.ONLY_LOC || pts == Obj.BOTH;
    }

    private static boolean isEsc(Obj pts) {
        return pts == Obj.ONLY_ESC || pts == Obj.BOTH;
    }

    /*****************************************************************
     * Printing functions
     *****************************************************************/

    public static String toString(Obj[] env) {
        String s = null;
        for (Obj o : env) {
            String x = toString(o);
            s = (s == null) ? x : (s + "," + x);
        }
        if (s == null)
            return "[]";
        return "[" + s + "]";
    }

    public static String toString(Obj o) {
        switch (o) {
        case EMTY:
            return "N";
        case ONLY_LOC:
            return "L";
        case ONLY_ESC:
            return "E";
        case BOTH:
            return "*";
        }
        assert (false);
        return null;
    }

    public static String toString(ArraySet<FldObj> heap) {
        String s = null;
        for (FldObj fo : heap) {
            String o;
            if (fo.isLoc)
                o = fo.isEsc ? "*=" : "L=";
            else
                o = fo.isEsc ? "E=" : "N=";
            int f = domF.indexOf(fo.f);
            String x = o + f;
            s = (s == null) ? x : (s + "," + x);
        }
        if (s == null)
            return "[]";
        return "[" + s + "]";
    }

    public String toString(Obj[] env, int fstVidx) {
        String onlyLoc = "";
        String onlyEsc = "";
        String both = "";
        for (int i = 0; i < env.length; i++) {
            Obj o = env[i];
            switch (o) {
            case EMTY:
                break;
            case ONLY_LOC:
                onlyLoc += domV.get(fstVidx + i) + ",";
                break;
            case ONLY_ESC:
                onlyEsc += domV.get(fstVidx + i) + ",";
                break;
            case BOTH:
                both += domV.get(fstVidx + i) + ",";
                break;
            }
        }
        String s = both.equals("") ? "" : "*="
                + both.substring(0, both.length() - 1);
        if (!onlyLoc.equals("")) {
            String t = "L=" + onlyLoc.substring(0, onlyLoc.length() - 1);
            if (!s.equals(""))
                s += " ";
            s += t;
        }
        if (!onlyEsc.equals("")) {
            String t = "E=" + onlyEsc.substring(0, onlyEsc.length() - 1);
            if (!s.equals(""))
                s += " ";
            s += t;
        }
        return "[" + s + "]";
    }

}
