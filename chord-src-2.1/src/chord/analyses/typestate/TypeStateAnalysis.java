package chord.analyses.typestate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Class.jq_Class;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.EntryOrExitBasicBlock;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.THROW_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CICGAnalysis;
import chord.analyses.alias.CIObj;
import chord.analyses.alias.CIPAAnalysis;
import chord.analyses.alias.ICICG;
import chord.analyses.alloc.DomH;
import chord.analyses.type.DomT;
import chord.analyses.method.DomM;
import chord.program.Loc;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Messages;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.rhs.RHSAnalysis;
import chord.util.ArraySet;
import chord.util.Utils;
import chord.util.tuple.object.Pair;
import chord.project.Config;
import chord.program.Program;

/**
 * System properties:
 * 1. File specifying type-state spec:
 *    chord.typestate.specfile (default value: [chord.work.dir]/typestatespec.txt)
 * 2. Max number of instance fields in any access path in any must set tracked
 *    chord.typestate.maxdepth (default value: 6)
 * 3. Alloc sites to exclude from queries
 *    chord.check.exclude (default value: JDK libraries)
 */
@Chord(name = "typestate-java")
public class TypeStateAnalysis extends RHSAnalysis<Edge, Edge> {
    protected static boolean DEBUG = false;
    protected TypeStateSpec sp;
    protected CIPAAnalysis cipa;
    protected ICICG cicg;
    protected Map<jq_Method, Set<jq_Field>> methodToModFields;
    protected Set<Quad> trackedSites;
    protected MyQuadVisitor qv = new MyQuadVisitor();
    protected jq_Method threadStartMethod;
    public static int maxDepth;
    protected String cipaName, cicgName;
    public static TypeState startState, errorState;
    private boolean isInit;

    // subclasses can override
    public TypeStateSpec getTypeStateSpec() {
        String specFile = System.getProperty("chord.typestate.specfile", Config.workRel2Abs("typestatespec.txt"));
        TypeStateSpec tss = TypeStateParser.parse(specFile);
        if (tss == null)
            throw new RuntimeException("Problem while parsing state spec file: " + specFile);
        return tss;
    }

    @Override
    public void init() {
        // XXX: do not compute anything here which needs to be re-computed on each call to run() below.

        if (isInit) return;
        isInit = true;

        threadStartMethod = Program.g().getThreadStartMethod();
        sp = getTypeStateSpec();
        startState = sp.getStartState();
        errorState = sp.getErrorState();
        
        maxDepth = Integer.getInteger("chord.typestate.maxdepth", 6);
        assert (maxDepth >= 0);

        cipaName = System.getProperty("chord.typestate.cipa", "cipa-java");
        cipa = (CIPAAnalysis) ClassicProject.g().getTask(cipaName);
        ClassicProject.g().runTask(cipa);

        cicgName = System.getProperty("chord.typestate.cicg", "cicg-java");
        CICGAnalysis cicgAnalysis = (CICGAnalysis) ClassicProject.g().getTask(cicgName);
        ClassicProject.g().runTask(cicgAnalysis);
        cicg = cicgAnalysis.getCallGraph();
        
        super.init();

        // build map methodToModFields
        {
            ProgramRel relModMF = (ProgramRel) ClassicProject.g().getTrgt("modMF");
            ClassicProject.g().runTask("modMF-dlog");
            relModMF.load();
            methodToModFields = new HashMap<jq_Method, Set<jq_Field>>();
            Iterable<Pair<jq_Method, jq_Field>> tuples = relModMF.getAry2ValTuples();
            for (Pair<jq_Method, jq_Field> p : tuples) {
                jq_Method m = p.val0;
                Set<jq_Field> modFields = methodToModFields.get(m);
                if (modFields == null) {
                    modFields = new HashSet<jq_Field>();
                    methodToModFields.put(m, modFields);
                }
                modFields.add(p.val1);
            }
            relModMF.close();
        }

        // build set trackedSites
        {
            Set<jq_Type> trackedTypes = new HashSet<jq_Type>();
            jq_Type trackedType = jq_Type.parseType(sp.getType());
            trackedTypes.add(trackedType);
            ProgramRel relSub = (ProgramRel) ClassicProject.g().getTrgt("sub");
            ClassicProject.g().runTask(relSub);
            relSub.load();
            DomT domT = (DomT) ClassicProject.g().getTrgt("T");
            for (jq_Type type : domT) {
                if (relSub.contains(type, trackedType))
                    trackedTypes.add(type);
            }
            relSub.close();

            ProgramRel relCheckExcludedT = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedT");
            ClassicProject.g().runTask(relCheckExcludedT);
            relCheckExcludedT.load();
            trackedSites = new HashSet<Quad>();
            DomH domH = (DomH) ClassicProject.g().getTrgt("H");
            ClassicProject.g().runTask(domH);
            int numH = domH.getLastA() + 1;
            for (int hIdx = 1; hIdx < numH; hIdx++) {
                Quad q = (Quad) domH.get(hIdx);
                if (q.getOperator() instanceof New && trackedTypes.contains(New.getType(q).getType())) {
                    jq_Class c = q.getMethod().getDeclaringClass();
                    if (!relCheckExcludedT.contains(c)) {
                        trackedSites.add(q);
                    }
                }
            }
            relCheckExcludedT.close();
        }
    }

    @Override
    public void run() {
        init();
        runPass();
        if (DEBUG) print();
        done();
    }

    @Override
    public ICICG getCallGraph() {
        return cicg;
    }

    /*
     * For each reachable method 'm' adds the following path edges:
     * 1. <null, null, null>
     * 2. for each tracked alloc site 'h' in the body of 'm': <null, h, null>
     */
    @Override
    public Set<Pair<Loc, Edge>> getInitPathEdges() {
        Set<Pair<Loc, Edge>> initPEs = new ArraySet<Pair<Loc, Edge>>();
        Map<jq_Method, Loc> methToEntry = new HashMap<jq_Method, Loc>();
        for (jq_Method m : cicg.getNodes()) {
            EntryOrExitBasicBlock bb = m.getCFG().entry();
            Loc loc = new Loc(bb, -1);
            methToEntry.put(m, loc);
            Pair<Loc, Edge> pair = new Pair<Loc, Edge>(loc, Edge.NULL);
            if (DEBUG) System.out.println("getInitPathEdges: Added " + pair);
            initPEs.add(pair);
        }
        for (Quad q : trackedSites) {
            Edge edge = new Edge(null, null, EdgeKind.ALLOC, q);
            jq_Method m = q.getMethod();
            Loc loc = methToEntry.get(m);
            if (loc == null) {
                // ignore allocs in methods unreachable from 0cfa call graph
                continue;
            }
            Pair<Loc, Edge> pair = new Pair<Loc, Edge>(loc, edge);
            if (DEBUG) System.out.println("getInitPathEdges: Added " + pair);
            initPEs.add(pair);
        }
        if (DEBUG){
            System.out.println("===== ENTER ALL QUERIES");
            for (Pair<Loc, Edge> pair : initPEs) {
                System.out.println(pair);
            }
            System.out.println("===== LEAVE ALL QUERIES");
        }
        return initPEs;
    }

    /*
     * If incoming path edge 'pe' is of the form <null, null, null> or <null, h, null>,
     * or if the target method is threadSart, then do nothing: return null edge.
     *
     * If incoming path edge 'pe' is of the form <null, h, AS> or <AS', h, AS> then
     * create and return new path edge in callee of the form <AS1, h, AS2> where
     * AS1 and AS2 are as follows:
     *   type-state of AS1 = type-state of AS
     *   type-state of AS2 = type-state of AS if this method is non-interesting, and the
     *   appropriate transitioned state otherwise.
     *   must-set of AS1 = must-set of AS2 = subset of must-set of AS consisting of two
     *   kinds of access paths: those of the form v.* where v is an actual argument (now
     *   replaced by the corresponding formal argument), and those of the form g.* where
     *   g is a static field.
     */
    @Override
    public Edge getInitPathEdge(Quad q, jq_Method m, Edge pe) {
        if (DEBUG) System.out.println("ENTER getInitPathEdge: q=" + q + " m=" + m + " pe=" + pe);
        if (pe == Edge.NULL || (pe.type == EdgeKind.ALLOC && pe.dstNode == null) || m == threadStartMethod){
            if (DEBUG) System.out.println("LEAVE getInitPathEdge: " + Edge.NULL);
            return Edge.NULL;
        }
        AbstractState oldDst = pe.dstNode;
        assert (oldDst != null);
        ArraySet<AccessPath> oldMS = oldDst.ms;
        ArraySet<AccessPath> newMS = new ArraySet<AccessPath>();
        // Build newMS in two steps
        // Step 1: for each r1.* where r1 is an actual arg of q, add r2.* where r2 is
        // the corresponding formal arg of m
        ParamListOperand args = Invoke.getParamList(q);
        RegisterFactory rf = m.getCFG().getRegisterFactory();
        boolean isthis = false;
        for (int i = 0; i < args.length(); i++) {
            Register actualReg = args.get(i).getRegister();
            Register formalReg = rf.get(i);
            for (int j = -1; (j = Helper.getIndexInAP(oldMS, actualReg, j)) >= 0;) {
                AccessPath oldAP = oldMS.get(j);
                AccessPath newAP = new RegisterAccessPath(formalReg, oldAP.fields);
                newMS.add(newAP);
                if (i == 0) isthis = true;
            }
        }
        // Step 2: add all g.*
        Helper.addAllGlobalAccessPath(newMS, oldMS);

        // Do typestate change depending on whether the method is interesting or not
        jq_Method tgtMethod = Invoke.getMethod(q).getMethod();
        TypeState newTS = oldDst.ts;
        if (sp.isMethodOfInterest(tgtMethod)) {
            if (isthis) {
                newTS = sp.getTargetState(tgtMethod.getName(), oldDst.ts);
                if (DEBUG) System.out.println("State Transition to: " + newTS);
            } else if (Helper.mayPointsTo(args.get(0).getRegister(), pe.h, cipa)) {
                newTS = errorState;
                if (DEBUG) System.out.println("State Transition to (mayPointTo): " + newTS);
            } else{
                newTS = oldDst.ts;
            }
        } else {
            if (DEBUG) {
                System.out.println("Not doing State Transition for: " + tgtMethod.getName() +
                    " and this is: " + (isthis ? "true" : "false"));
            }
        }

        AbstractState newSrc = new AbstractState(oldDst.ts, newMS);
        AbstractState newDst = new AbstractState(newTS, newMS);
        Edge newEdge = new Edge(newSrc, newDst, EdgeKind.FULL, pe.h);
        if (DEBUG) System.out.println("LEAVE getInitPathEdge: " + newEdge);
        return newEdge;
    }

    @Override
    public Edge getMiscPathEdge(Quad q, Edge pe) {
        if (DEBUG) System.out.println("ENTER getMiscPathEdge: q=" + q + " pe=" + pe);
        if (pe == Edge.NULL) return pe;
        qv.istate = pe.dstNode;
        qv.ostate = pe.dstNode;
        qv.h = pe.h;
        // may modify only qv.ostate
        q.accept(qv);
        // XXX: DO NOT REUSE incoming PE (merge does strong updates)
        Edge newEdge = new Edge(pe.srcNode, qv.ostate, pe.type, pe.h);
        if (DEBUG) System.out.println("LEAVE getMiscPathEdge: ret=" + newEdge);
        return newEdge;
    }

    /**
     * If target method is threadStart, then only matching summary edge tgtSE is the null edge,
     * in which case (a copy of) the incoming path edge clrPE is returned.
     */
    @Override
    public Edge getInvkPathEdge(Quad q, Edge clrPE, jq_Method m, Edge tgtSE) {
        if (DEBUG) System.out.println("ENTER getInvkPathEdge: q=" + q + " clrPE=" + clrPE + " m=" + m + " tgtSE=" + tgtSE);
        // Mayur:
        if (m == threadStartMethod) {
            if (tgtSE == Edge.NULL) return getCopy(clrPE);
            return null;
        }
        switch (clrPE.type) {
        case NULL:
            switch (tgtSE.type) {
            case NULL:
                if (DEBUG) System.out.println(Edge.NULL);
                if (DEBUG) System.out.println("LEAVE getInvkPathEdge: " + Edge.NULL);
                return Edge.NULL;
            case FULL:
                if (DEBUG) System.out.println("LEAVE getInvkPathEdge: null");
                return null;
            case ALLOC:
                if (tgtSE.dstNode == null) {
                    if (DEBUG) System.out.println("LEAVE getInvkPathEdge: null");
                    return null;
                }
            }
            break;
        case ALLOC:
            switch (tgtSE.type) {
            case NULL:
                if (clrPE.dstNode == null) {
                    if (DEBUG) System.out.println("LEAVE getInvkPathEdge: incoming clrPE");
                    return getCopy(clrPE);
                }
                if (DEBUG) System.out.println("LEAVE getInvkPathEdge: null");
                return null;
            case FULL:
                if (clrPE.dstNode == null || clrPE.h != tgtSE.h || clrPE.dstNode.ts != tgtSE.srcNode.ts) {
                    if (DEBUG) System.out.println("LEAVE getInvkPathEdge: null");
                    return null;
                }
                // postpone check for equality of clrPE.dstNode.ms and tgtSE.srcNode.ms
                break;
            case ALLOC:
                if (DEBUG) System.out.println("LEAVE getInvkPathEdge: null");
                return null;
            }
            break;
        case FULL:
            switch (tgtSE.type) {
            case FULL:
                if (clrPE.h != tgtSE.h || clrPE.dstNode.ts != tgtSE.srcNode.ts) {
                    if (DEBUG) System.out.println("LEAVE getInvkPathEdge: null");
                    return null;
                }
                // postpone check for equality of clrPE.dstNode.ms and tgtSE.srcNode.ms
                break;
            default:
                if (DEBUG) System.out.println("LEAVE getInvkPathEdge: null");
                return null;
            }
        }

        // At this point, we have one of the following three cases:
        //     clrPE                   tgtSE             condition
        // ============================================================
        // FULL:<AS1,h,AS2>       FULL:<AS3,h,AS4>     AS2.ts==AS3.ts (need ms equality check below)
        // ALLOC:<null,h,AS1>     FULL:<AS2,h,AS3>     AS1.ts==AS2.ts (need ms equality check below)
        // NULL:<null,null,null>  ALLOC:<null,h,AS>    None (need to generate suitable ms)

        ArraySet<AccessPath> newMS = new ArraySet<AccessPath>();
        ParamListOperand args = Invoke.getParamList(q);
        RegisterFactory rf = m.getCFG().getRegisterFactory();

        if (clrPE.type == EdgeKind.ALLOC || clrPE.type == EdgeKind.FULL) {
            // Compare must sets; they should be equal in order to apply summary
            // Build this must set tmpMS in two steps
            ArraySet<AccessPath> tmpMS = new ArraySet<AccessPath>();

            // Step 1: for each r1.* in caller must set where r1 is an actual arg of q,
            // add r2.* where r2 is the corresponding formal arg
            ArraySet<AccessPath> clrMS = new ArraySet<AccessPath>(clrPE.dstNode.ms);
            for (int i = 0; i < args.length(); i++) {
                Register actualReg = args.get(i).getRegister();
                Register formalReg = rf.get(i);
                for (int j = -1; (j = Helper.getIndexInAP(clrPE.dstNode.ms, actualReg, j)) >= 0;) {
                    AccessPath oldAP = clrPE.dstNode.ms.get(j);
                    AccessPath newAP = new RegisterAccessPath(formalReg, oldAP.fields);
                    tmpMS.add(newAP);
                    clrMS.remove(oldAP);
                }
            }

            // Step 2: add all g.* in caller must set
            Helper.addAllGlobalAccessPath(tmpMS, clrPE.dstNode.ms);
            Helper.removeAllGlobalAccessPaths(clrMS);

            if (!tgtSE.srcNode.ms.equals(tmpMS)) {
                if (DEBUG) System.out.println("LEAVE getInvkPathEdge: null (must sets don't match)");
                return null;
            }

            // At this point we are done with tmpMS but we will still use clrMS.
            // Build final must set newMS in four steps.

            // Step 1: Add all x.* in caller must set where x is neither an actual arg nor a
            // static field, and no instance field in "*" is modified in the callee (as per modMF)
            addFallThroughAccessPaths(q, clrPE, m, tgtSE, newMS, clrMS);

            // Step 2: Add all caller local variables, i.e., paths r without any fields in caller
            // must set that were not added in step 1
            for (Iterator<AccessPath> i = clrPE.dstNode.ms.iterator(); i.hasNext();) {
                AccessPath ap = i.next();
                if (ap instanceof RegisterAccessPath && ap.fields.isEmpty())
                    newMS.add(ap);
            }

            // Step 3: Replace formals with actuals (effectively do reverse of above for loop)
            for (int i = 0; i < args.length(); i++) {
                Register formalReg = rf.get(i);
                Register actualReg = args.get(i).getRegister();
                for (int j = -1; (j = Helper.getIndexInAP(tgtSE.dstNode.ms, formalReg, j)) >= 0;) {
                    AccessPath oldAP = tgtSE.dstNode.ms.get(j);
                    AccessPath newAP = new RegisterAccessPath(actualReg, oldAP.fields);
                    newMS.add(newAP);
                }
            }
            
            // Step 4: Add all g.* and return var; shared below with below else case,
            // where clrPE.type is NULL
        } else {
            // When clrPE.type is NULL and tgtSE.type is ALLOC, always return with suitable mustset

            // Step 1: Add elements to ms accessible via formal args
            for (int i = 0; i < args.length(); i++) {
                Register formalReg = rf.get(i);
                Register actualReg = args.get(i).getRegister();
                for (int j = -1; (j = Helper.getIndexInAP(tgtSE.dstNode.ms, formalReg, j)) >= 0;) {
                    AccessPath oldAP = tgtSE.dstNode.ms.get(j);
                    AccessPath newAP = new RegisterAccessPath(actualReg, oldAP.fields);
                    newMS.add(newAP);
                }
            }
            
            /* Check if any element in tgtSE must set is accessible from caller, either via return var,
             * a global access path, or a formal arg access path
             * 
             * if (newMS.size() == 0 && !tgtSE.dstNode.canReturn && !Helper.hasAnyGlobalAccessPath(tgtSE.dstNode))
             *    return null;
             * else AS.canReturn is true or AS.ms has some global access path or AS.ms has path from method params
             */
            
            // Step 2: Add all g.* and return var; shared below with above then case,
            // where clrPE.type is ALLOC or FULL
        }

        Register tgtRetReg = (Invoke.getDest(q) != null) ? Invoke.getDest(q).getRegister() : null;
        if (tgtSE.dstNode.canReturn && tgtRetReg != null) {
            newMS.add(new RegisterAccessPath(tgtRetReg));
        }
        
        Helper.addAllGlobalAccessPath(newMS, tgtSE.dstNode.ms);
            
        AbstractState newDst = new AbstractState(tgtSE.dstNode.ts, newMS);
        EdgeKind newType = (clrPE.type == EdgeKind.NULL) ? EdgeKind.ALLOC : clrPE.type;
        Edge newEdge = new Edge(clrPE.srcNode, newDst, newType, tgtSE.h);
        if (DEBUG) System.out.println("LEAVE getInvkPathEdge: " + newEdge);
        return newEdge;
    }

    // Refactored into a method to enable overloading later on
    public void addFallThroughAccessPaths(Quad q, Edge clrPE, jq_Method m, Edge tgtSE, ArraySet<AccessPath> newMS, ArraySet<AccessPath> clrMS) {
        newMS.addAll(clrMS);
        Helper.removeModifiableAccessPaths(methodToModFields.get(m), newMS);
    }
    
    @Override
    public Edge getPECopy(Edge pe) { return getCopy(pe); }

    @Override
    public Edge getSECopy(Edge se) { return getCopy(se); }

    private Edge getCopy(Edge pe) {
        if (DEBUG) System.out.println("Called Copy with: " + pe);
        return (pe == Edge.NULL) ? pe : new Edge(pe.srcNode, pe.dstNode, pe.type, pe.h);
    }

    @Override
    public Edge getSummaryEdge(jq_Method m, Edge pe) {
        if (DEBUG) System.out.println("\nCalled getSummaryEdge: m=" + m + " pe=" + pe);
        return getCopy(pe);
    }

    public class MyQuadVisitor extends QuadVisitor.EmptyVisitor {
        public AbstractState istate;    // immutable, may be null
        public AbstractState ostate;    // mutable, initially ostate == istate
        public Quad h;                    // immutable, non-null
        @Override
        public void visitCheckCast(Quad q) {
            visitMove(q);
        }
        @Override
        public void visitMove(Quad q) {
            if (istate == null) return;        // edge is ALLOC:<null, h, null>
            // edge is ALLOC:<null, h, AS> or FULL:<AS', h, AS>
            Register dstR = Move.getDest(q).getRegister();
            ArraySet<AccessPath> oldMS = istate.ms;
            ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstR);
            if (Move.getSrc(q) instanceof RegisterOperand) {
                Register srcR = ((RegisterOperand) Move.getSrc(q)).getRegister();
                for (int i = -1; (i = Helper.getIndexInAP(oldMS, srcR, i)) >= 0;) {
                    if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                    newMS.add(new RegisterAccessPath(dstR, oldMS.get(i).fields));
                }
            }
            if (newMS != null)
                ostate = new AbstractState(istate.ts, newMS);
        }

        @Override
        public void visitPhi(Quad q) {
            if (istate == null) return;        // edge is ALLOC:<null, h, null>
            // edge is ALLOC:<null, h, AS> or FULL:<AS', h, AS>
            Register dstR = Phi.getDest(q).getRegister();
            ArraySet<AccessPath> oldMS = istate.ms;
            ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstR);
            ParamListOperand ros = Phi.getSrcs(q);
            int n = ros.length();
            for (int i = 0; i < n; i++) {
                RegisterOperand ro = ros.get(i);
                if (ro == null) continue;
                Register srcR = ((RegisterOperand) ro).getRegister();
                for (int j = -1; (j = Helper.getIndexInAP(oldMS, srcR, j)) >= 0;) {
                    if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                    newMS.add(new RegisterAccessPath(dstR, oldMS.get(j).fields));
                }
            }
            if (newMS != null)
                ostate = new AbstractState(istate.ts, newMS);
        }

        @Override
        public void visitNew(Quad q) {
            if (istate == null) {
                // edge is ALLOC:<null, h, null>; check if q == h
                if (h == q && trackedSites.contains(q)) {
                    ArraySet<AccessPath> newMS = new ArraySet<AccessPath>(1);
                    Register dstR = New.getDest(q).getRegister();
                    newMS.add(new RegisterAccessPath(dstR));
                    ostate = new AbstractState(sp.getStartState(), newMS);
                }
            } else {
                // edge is ALLOC:<null, h, AS> or FULL:<AS', h, AS>
                Register dstR = New.getDest(q).getRegister();
                ArraySet<AccessPath> newMS = Helper.removeReference(istate.ms, dstR);
                if (newMS != null)
                    ostate = new AbstractState(istate.ts, newMS);
            }
        }

        @Override
        public void visitNewArray(Quad q) {
            if (istate == null) return;
            Register dstR = NewArray.getDest(q).getRegister();
            ArraySet<AccessPath> newMS = Helper.removeReference(istate.ms, dstR);
            if (newMS != null)
                ostate = new AbstractState(istate.ts, newMS);
        }

        @Override
        public void visitMultiNewArray(Quad q) {
            if (istate == null) return;
            Register dstR = MultiNewArray.getDest(q).getRegister();
            ArraySet<AccessPath> newMS = Helper.removeReference(istate.ms, dstR);
            if (newMS != null)
                ostate = new AbstractState(istate.ts, newMS);
        }
        
        @Override
        public void visitALoad(Quad q) {
            if (istate == null) return;
            Register dstR = ALoad.getDest(q).getRegister();
            ArraySet<AccessPath> newMS = Helper.removeReference(istate.ms, dstR);
            if (newMS != null)
                ostate = new AbstractState(istate.ts, newMS);
        }

        @Override
        public void visitGetstatic(Quad q) {
            if (istate == null) return;
            Register dstR = Getstatic.getDest(q).getRegister();
            jq_Field srcF = Getstatic.getField(q).getField();
            ArraySet<AccessPath> oldMS = istate.ms;
            ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstR);
            for (int i = -1; (i = Helper.getIndexInAP(oldMS, srcF, i)) >= 0;) {
                if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                newMS.add(new RegisterAccessPath(dstR, oldMS.get(i).fields));
            }
            if (newMS != null) 
                ostate = new AbstractState(istate.ts, newMS);
        }

        @Override
        public void visitPutstatic(Quad q) {
            if (istate == null) return;
            jq_Field dstF = Putstatic.getField(q).getField();
            ArraySet<AccessPath> oldMS = istate.ms;
            ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstF);
            if (Putstatic.getSrc(q) instanceof RegisterOperand) {
                Register srcR = ((RegisterOperand) Putstatic.getSrc(q)).getRegister();
                for (int i = -1; (i = Helper.getIndexInAP(oldMS, srcR, i)) >= 0;) {
                    if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                    newMS.add(new GlobalAccessPath(dstF, oldMS.get(i).fields));
                }
            }
            if (newMS != null) 
                ostate = new AbstractState(istate.ts, newMS);
        }

        @Override
        public void visitPutfield(Quad q) {
            if (istate == null) return;
            if (!(Putfield.getBase(q) instanceof RegisterOperand)) return;
            Register dstR = ((RegisterOperand) Putfield.getBase(q)).getRegister();
            jq_Field dstF = Putfield.getField(q).getField();
            ArraySet<AccessPath> oldMS = istate.ms;
            ArraySet<AccessPath> newMS = null;
            for (AccessPath ap : oldMS) {
                if (Helper.mayPointsTo(ap, dstR, dstF, cipa)) {
                    if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                    newMS.remove(ap);
                }
            }
            if (Putfield.getSrc(q) instanceof RegisterOperand) {
                Register srcR = ((RegisterOperand) Putfield.getSrc(q)).getRegister();
                for (int i = -1; (i = Helper.getIndexInAP(oldMS, srcR, i)) >= 0;) {
                    AccessPath oldAP = oldMS.get(i);
                    if (oldAP.fields.size() == maxDepth)
                        continue; 
                    List<jq_Field> fields = new ArrayList<jq_Field>();
                    fields.add(dstF);
                    fields.addAll(oldAP.fields);
                    if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                    newMS.add(new RegisterAccessPath(dstR, fields));
                }
            }
            if (newMS != null)
                ostate = new AbstractState(istate.ts, newMS);
        }
        
        @Override
        public void visitGetfield(Quad q) {
            if (istate == null) return;
            Register dstR = Getfield.getDest(q).getRegister();
            ArraySet<AccessPath> oldMS = istate.ms;
            ArraySet<AccessPath> newMS = Helper.removeReference(oldMS, dstR);
            if (Getfield.getBase(q) instanceof RegisterOperand) {
                Register srcR = ((RegisterOperand) Getfield.getBase(q)).getRegister();
                jq_Field srcF = Getfield.getField(q).getField();
                // when stmt is x=y.f, we add x.* if y.f.* is in the must set
                for (int i = -1; (i = Helper.getIndexInAP(oldMS, srcR, srcF, i)) >= 0;) {
                    List<jq_Field> fields = new ArrayList<jq_Field>(oldMS.get(i).fields);
                    fields.remove(0);
                    if (newMS == null) newMS = new ArraySet<AccessPath>(oldMS);
                    newMS.add(new RegisterAccessPath(dstR, fields));
                }
            }
            if (newMS != null)
                ostate = new AbstractState(istate.ts, newMS);
        }

        @Override
        public void visitReturn(Quad q) {
            if (istate == null) return;        // edge is ALLOC:<null, h, null>
            // edge is ALLOC:<null, h, AS> or FULL:<AS', h, AS>
            if (q.getOperator() instanceof THROW_A)
                return;
            if (Return.getSrc(q) instanceof RegisterOperand) {
                Register tgtR = ((RegisterOperand) (Return.getSrc(q))).getRegister();
                if (Helper.getIndexInAP(istate.ms, tgtR) >= 0) {
                    ostate = new AbstractState(istate.ts, istate.ms, true);
                }
            }
        }
    }
}
