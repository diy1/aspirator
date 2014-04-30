package chord.instr;

import gnu.trove.map.hash.TIntObjectHashMap;
import javassist.*;
import javassist.expr.*;

import chord.runtime.EventHandler;
import chord.project.Messages;
import chord.analyses.alloc.DomH;
import chord.analyses.basicblock.DomB;
import chord.analyses.field.DomF;
import chord.analyses.heapacc.DomE;
import chord.analyses.invk.DomI;
import chord.analyses.lock.DomL;
import chord.analyses.lock.DomR;
import chord.analyses.method.DomM;
import chord.analyses.point.DomP;
import chord.instr.InstrScheme.EventFormat;
import chord.program.Program;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramDom;
import chord.util.IndexMap;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;

import java.util.Map;

/**
 * Bytecode instrumentor for instrumenting a variety of common events.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Instrumentor extends BasicInstrumentor {
    private static final String INSTR_SCHEME_UNDEFINED =
        "ERROR: Instrumentor: expected value for option '" + InstrScheme.INSTR_SCHEME_FILE_KEY + "'.";
    private static final String NOT_IN_DOMAIN =
        "WARN: Instrumentor: Domain '%s' does not contain '%s'";
    private static final String CANNOT_INSTRUMENT_METHOD =
        "WARN: Instrumentor: Skipping instrumenting method '%s'; reason follows";
    private static final String CLASS_NOT_FOUND =
        "WARN: Instrumentor: Skipping instrumenting class '%s'; deemed unreachable by program scope builder";
    private static final String METHOD_NOT_FOUND =
        "WARN: Instrumentor: Skipping instrumenting method '%s'; deemed unreachable by program scope builder";
    private static final String METHOD_BYTECODE_NOT_FOUND =
        "WARN: Instrumentor: Skipping instrumenting method '%s'; its bytecode does not exist";
    private static final String DUPLICATE_IN_DOMAIN =
         "ERROR: Instrumentor: Map for domain '%s' already contains '%s'";
    private static final String NO_BCI_IN_BASIC_BLOCK =
        "WARN: Instrumentor: Could not find bytecode index of first instruction in basic block '%s' of method '%s'";
    private static final String THROWABLE_CLASS_NOT_FOUND =
        "ERROR: Instrumentor: Could not find class java.lang.Throwable";

    protected final Program program;
    protected final InstrScheme scheme;
    protected final String eventHandlerClassName;

    protected final jq_Method mainMethod;

    ///// Events to be instrumented

    protected final EventFormat enterMainMethodEvent;
    protected final EventFormat enterMethodEvent;
    protected final EventFormat leaveMethodEvent;
    protected final boolean genBasicBlockEvent;
    protected final boolean genQuadEvent;

    protected final EventFormat befMethodCallEvent;
    protected final EventFormat aftMethodCallEvent;
    protected final EventFormat befNewEvent;
    protected final EventFormat aftNewEvent;
    protected final EventFormat newArrayEvent;

    protected final EventFormat getstaticPrimitiveEvent;
    protected final EventFormat getstaticReferenceEvent;
    protected final EventFormat putstaticPrimitiveEvent;
    protected final EventFormat putstaticReferenceEvent;

    protected final EventFormat getfieldPrimitiveEvent;
    protected final EventFormat getfieldReferenceEvent;
    protected final EventFormat putfieldPrimitiveEvent;
    protected final EventFormat putfieldReferenceEvent;

    protected final EventFormat aloadPrimitiveEvent;
    protected final EventFormat aloadReferenceEvent;
    protected final EventFormat astorePrimitiveEvent;
    protected final EventFormat astoreReferenceEvent;

    protected final EventFormat threadStartEvent;
    protected final EventFormat threadJoinEvent;
    protected final EventFormat acquireLockEvent;
    protected final EventFormat releaseLockEvent;
    protected final EventFormat waitEvent;
    protected final EventFormat notifyAnyEvent;
    protected final EventFormat notifyAllEvent;

    ///// Event call strings

    protected final String enterMainMethodEventCall;
    protected final String enterMethodEventCall;
    protected final String leaveMethodEventCall;
    protected final String basicBlockEventCall;
    protected final String quadEventCall;

    protected final String befMethodCallEventCall;
    protected final String aftMethodCallEventCall;
    protected final String befNewEventCall;
    protected final String aftNewEventCall;
    protected final String newArrayEventCall;

    protected final String getstaticPriEventCall;
    protected final String putstaticPriEventCall;
    protected final String getstaticRefEcentCall;
    protected final String putstaticRefEventCall;

    protected final String getfieldPriEventCall;
    protected final String putfieldPriEventCall;
    protected final String getfieldReference;
    protected final String putfieldRefEventCall;

    protected final String aloadPriEventCall;
    protected final String aloadRefEventCall;
    protected final String astorePriEventCall;
    protected final String astoreRefEventCall;

    protected final String threadStartEventCall;
    protected final String threadJoinEventCall;
    protected final String acquireLockEventCall;
    protected final String releaseLockEventCall;
    protected final String waitEventCall;
    protected final String notifyAnyEventCall;
    protected final String notifyAllEventCall;

    protected DomF domF;
    protected DomM domM;
    protected DomH domH;
    protected DomE domE;
    protected DomI domI;
    protected DomL domL;
    protected DomR domR;
    protected DomP domP;
    protected DomB domB;

    protected IndexMap<String> Fmap;
    protected IndexMap<String> Mmap;
    protected IndexMap<String> Hmap;
    protected IndexMap<String> Emap;
    protected IndexMap<String> Imap;
    protected IndexMap<String> Lmap;
    protected IndexMap<String> Rmap;
    protected IndexMap<String> Pmap;
    protected IndexMap<String> Bmap;

    private CtClass exType;

    protected TIntObjectHashMap<String> bciToInstrMap = new TIntObjectHashMap<String>();
    protected jq_Class currClass;
    protected jq_Method currMethod;
    protected String currSign;

    public DomF getDomF() { return domF; }
    public DomM getDomM() { return domM; }
    public DomH getDomH() { return domH; }
    public DomE getDomE() { return domE; }
    public DomI getDomI() { return domI; }
    public DomL getDomL() { return domL; }
    public DomR getDomR() { return domR; }
    public DomP getDomP() { return domP; }
    public DomB getDomB() { return domB; }

    public IndexMap<String> getFmap() { return Fmap; }
    public IndexMap<String> getMmap() { return Mmap; }
    public IndexMap<String> getHmap() { return Hmap; }
    public IndexMap<String> getEmap() { return Emap; }
    public IndexMap<String> getImap() { return Imap; }
    public IndexMap<String> getLmap() { return Lmap; }
    public IndexMap<String> getRmap() { return Rmap; }
    public IndexMap<String> getPmap() { return Pmap; }
    public IndexMap<String> getBmap() { return Bmap; }

    private static InstrScheme loadInstrScheme(Map<String, String> argsMap) {
        String s = argsMap.get(InstrScheme.INSTR_SCHEME_FILE_KEY);
        if (s == null)
            Messages.fatal(INSTR_SCHEME_UNDEFINED);
        return InstrScheme.load(s);
    }

    // doesn't matter if the class name is in '.' or '/' separated form
    private static String getEventHandlerClassName(Map<String, String> argsMap) {
        String s = argsMap.get(BasicInstrumentor.EVENT_HANDLER_CLASS_KEY);
        if (s == null)
            s = EventHandler.class.getName();
        try {
            Class.forName(s);
        } catch (ClassNotFoundException ex) {
            Messages.fatal(ex);
        }
        return s;
    }

    public InstrScheme getInstrScheme() {
        return scheme;
    }

    public Instrumentor(Map<String, String> argsMap) {
        this(argsMap, loadInstrScheme(argsMap), getEventHandlerClassName(argsMap));
    }

    /**
     * Initializes the instrumentor.
     * 
     * @param    argsMap    Arguments passed to the online (load-time)
     *            instrumentation agent.  This instrumentor is offline if
     *            argsMap    is null and online otherwise.
     * @param    scheme    Scheme specifying the kind and format of events
     *            to generate during the execution of the instrumented
     *            program. 
     */
    private Instrumentor(Map<String, String> argsMap, InstrScheme _scheme,
            String _eventHandlerClassName) {
        super(argsMap);
        program = Program.g();
        this.scheme = _scheme;
        this.eventHandlerClassName = _eventHandlerClassName.replace('/', '.') + ".";

        mainMethod = program.getMainMethod();
        enterMainMethodEvent = scheme.getEvent(InstrScheme.ENTER_MAIN_METHOD);
        enterMethodEvent = scheme.getEvent(InstrScheme.ENTER_METHOD);
        leaveMethodEvent = scheme.getEvent(InstrScheme.LEAVE_METHOD);
        genBasicBlockEvent = scheme.hasBasicBlockEvent();
        genQuadEvent = scheme.hasQuadEvent();

        befMethodCallEvent = scheme.getEvent(InstrScheme.BEF_METHOD_CALL);
        aftMethodCallEvent = scheme.getEvent(InstrScheme.AFT_METHOD_CALL);
        befNewEvent = scheme.getEvent(InstrScheme.BEF_NEW);
        aftNewEvent = scheme.getEvent(InstrScheme.AFT_NEW);
        newArrayEvent = scheme.getEvent(InstrScheme.NEWARRAY);

        getstaticPrimitiveEvent = scheme.getEvent(InstrScheme.GETSTATIC_PRIMITIVE);
        getstaticReferenceEvent = scheme.getEvent(InstrScheme.GETSTATIC_REFERENCE);
        putstaticPrimitiveEvent = scheme.getEvent(InstrScheme.PUTSTATIC_PRIMITIVE);
        putstaticReferenceEvent = scheme.getEvent(InstrScheme.PUTSTATIC_REFERENCE);

        getfieldPrimitiveEvent = scheme.getEvent(InstrScheme.GETFIELD_PRIMITIVE);
        getfieldReferenceEvent = scheme.getEvent(InstrScheme.GETFIELD_REFERENCE);
        putfieldPrimitiveEvent = scheme.getEvent(InstrScheme.PUTFIELD_PRIMITIVE);
        putfieldReferenceEvent = scheme.getEvent(InstrScheme.PUTFIELD_REFERENCE);

        aloadPrimitiveEvent = scheme.getEvent(InstrScheme.ALOAD_PRIMITIVE);
        aloadReferenceEvent = scheme.getEvent(InstrScheme.ALOAD_REFERENCE);
        astorePrimitiveEvent = scheme.getEvent(InstrScheme.ASTORE_PRIMITIVE);
        astoreReferenceEvent = scheme.getEvent(InstrScheme.ASTORE_REFERENCE);

        threadStartEvent = scheme.getEvent(InstrScheme.THREAD_START);
        threadJoinEvent = scheme.getEvent(InstrScheme.THREAD_JOIN);
        acquireLockEvent = scheme.getEvent(InstrScheme.ACQUIRE_LOCK);
        releaseLockEvent = scheme.getEvent(InstrScheme.RELEASE_LOCK);
        waitEvent = scheme.getEvent(InstrScheme.WAIT);
        notifyAnyEvent = scheme.getEvent(InstrScheme.NOTIFY_ANY);
        notifyAllEvent = scheme.getEvent(InstrScheme.NOTIFY_ALL);

        enterMainMethodEventCall = eventHandlerClassName + "enterMainMethodEvent();";
        enterMethodEventCall = eventHandlerClassName + "enterMethodEvent(";
        leaveMethodEventCall = eventHandlerClassName + "leaveMethodEvent(";
        basicBlockEventCall = eventHandlerClassName + "basicBlockEvent(";
        quadEventCall = eventHandlerClassName + "quadEvent(";

        befMethodCallEventCall = eventHandlerClassName + "befMethodCallEvent(";
        aftMethodCallEventCall = eventHandlerClassName + "aftMethodCallEvent(";
        befNewEventCall = eventHandlerClassName + "befNewEvent(";
        aftNewEventCall = eventHandlerClassName + "aftNewEvent(";
        newArrayEventCall = eventHandlerClassName + "newArrayEvent(";

        getstaticPriEventCall = eventHandlerClassName + "getstaticPrimitiveEvent(";
        putstaticPriEventCall = eventHandlerClassName + "putstaticPrimitiveEvent(";
        getstaticRefEcentCall = eventHandlerClassName + "getstaticReferenceEvent(";
        putstaticRefEventCall = eventHandlerClassName + "putstaticReferenceEvent(";

        getfieldPriEventCall = eventHandlerClassName + "getfieldPrimitiveEvent(";
        putfieldPriEventCall = eventHandlerClassName + "putfieldPrimitiveEvent(";
        getfieldReference = eventHandlerClassName + "getfieldReferenceEvent(";
        putfieldRefEventCall = eventHandlerClassName + "putfieldReferenceEvent(";

        aloadPriEventCall = eventHandlerClassName + "aloadPrimitiveEvent(";
        aloadRefEventCall = eventHandlerClassName + "aloadReferenceEvent(";
        astorePriEventCall = eventHandlerClassName + "astorePrimitiveEvent(";
        astoreRefEventCall = eventHandlerClassName + "astoreReferenceEvent(";

        threadStartEventCall = eventHandlerClassName + "threadStartEvent(";
        threadJoinEventCall = eventHandlerClassName + "threadJoinEvent(";
        acquireLockEventCall = eventHandlerClassName + "acquireLockEvent(";
        releaseLockEventCall = eventHandlerClassName + "releaseLockEvent(";
        waitEventCall = eventHandlerClassName + "waitEvent(";
        notifyAnyEventCall = eventHandlerClassName + "notifyAnyEvent(";
        notifyAllEventCall = eventHandlerClassName + "notifyAllEvent(";

        if (scheme.needsFmap()) {
            domF = (DomF) ClassicProject.g().getTrgt("F");
            ClassicProject.g().runTask(domF);
            Fmap = getUniqueStringMap(domF);
        }
        if (scheme.needsMmap()) {
            domM = (DomM) ClassicProject.g().getTrgt("M");
            ClassicProject.g().runTask(domM);
            Mmap = getUniqueStringMap(domM);
        }
        if (scheme.needsHmap()) {
            domH = (DomH) ClassicProject.g().getTrgt("H");
            ClassicProject.g().runTask(domH);
            Hmap = getUniqueStringMap(domH);
        }
        if (scheme.needsEmap()) {
            domE = (DomE) ClassicProject.g().getTrgt("E");
            ClassicProject.g().runTask(domE);
            Emap = getUniqueStringMap(domE);
        }
        if (scheme.needsImap()) {
            domI = (DomI) ClassicProject.g().getTrgt("I");
            ClassicProject.g().runTask(domI);
            Imap = getUniqueStringMap(domI);
        }
        if (scheme.needsLmap()) {
            domL = (DomL) ClassicProject.g().getTrgt("L");
            ClassicProject.g().runTask(domL);
            Lmap = getUniqueStringMap(domL);
        }
        if (scheme.needsRmap()) {
            domR = (DomR) ClassicProject.g().getTrgt("R");
            ClassicProject.g().runTask(domR);
            Rmap = getUniqueStringMap(domR);
        }
        if (scheme.needsPmap()) {
            domP = (DomP) ClassicProject.g().getTrgt("P");
            ClassicProject.g().runTask(domP);
            Pmap = getUniqueStringMap(domP);
        }
        if (scheme.needsBmap()) {
            domB = (DomB) ClassicProject.g().getTrgt("B");
            ClassicProject.g().runTask(domB);
            Bmap = getUniqueStringMap(domB);
        }
        if (leaveMethodEvent.present() || releaseLockEvent.present()) {
            try {
                exType = pool.get("java.lang.Throwable");
            } catch (NotFoundException ex) {
                Messages.fatal(THROWABLE_CLASS_NOT_FOUND);
            }
        }
    }

    protected int getBCI(BasicBlock b, jq_Method m) {
        int n = b.size();
        for (int i = 0; i < n; i++) {
            Quad q = b.getQuad(i);
            int bci = q.getBCI();
            if (bci != -1)
                return bci;
        }
        Messages.log(NO_BCI_IN_BASIC_BLOCK, b, m);
        return -1;
    }

    // order must be tail -> head -> rest
    protected void attachInstrToBCIAft(String str, int bci) {
        String s = bciToInstrMap.get(bci);
        bciToInstrMap.put(bci, (s == null) ? str : s + str);
    }

    protected void attachInstrToBCIBef(String str, int bci) {
        String s = bciToInstrMap.get(bci);
        bciToInstrMap.put(bci, (s == null) ? str : str + s);
    }

    protected <T> IndexMap<String> getUniqueStringMap(ProgramDom<T> dom) {
        IndexMap<String> map = new IndexMap<String>(dom.size());
        for (int i = 0; i < dom.size(); i++) {
            String s = dom.toUniqueString(dom.get(i));
            if (map.contains(s))
                Messages.fatal(DUPLICATE_IN_DOMAIN, dom.getName(), s);
            map.getOrAdd(s);
        }
        return map;
    }

    @Override
    public CtClass edit(CtClass clazz) throws CannotCompileException {
        String cName = clazz.getName();
        currClass = (jq_Class) program.getClass(cName);
        if (currClass == null) {
            if (verbose >= 2) Messages.log(CLASS_NOT_FOUND, cName);
            return null;
        }
        return super.edit(clazz);
    }

    @Override
    public void edit(CtBehavior method) throws CannotCompileException {
        int mods = method.getModifiers();
        if (Modifier.isNative(mods) || Modifier.isAbstract(mods))
            return;
        String mName;
        if (method instanceof CtConstructor)
            mName = ((CtConstructor) method).isClassInitializer() ? "<clinit>" : "<init>";
        else
            mName = method.getName();
        String mDesc = method.getSignature();
        String cName = currClass.getName();
        currSign = mName + ":" + mDesc + "@" + cName;
        currMethod = program.getMethod(currSign);
        if (currMethod == null) {
            if (verbose >= 2) Messages.log(METHOD_NOT_FOUND, currSign);
            return;
        }
        int mId;
        if (Mmap != null) {
            mId = Mmap.indexOf(currSign);
            if (mId == -1 && verbose >= 2)
                Messages.log(NOT_IN_DOMAIN, getDomainName(Mmap), currSign);
        } else
            mId = -1;
        if (genQuadEvent || genBasicBlockEvent) {
            Map<Quad, Integer> bcMap;
            try{
                bcMap = currMethod.getBCMap();
            } catch (RuntimeException ex) {
                if (verbose >= 2) Messages.log(CANNOT_INSTRUMENT_METHOD, currSign);
                ex.printStackTrace();
                return;
            }
            if (bcMap == null) {
                if (verbose >= 2) Messages.log(METHOD_BYTECODE_NOT_FOUND, currSign);
                return;
            }
            ControlFlowGraph cfg = currMethod.getCFG();
            bciToInstrMap.clear();
            if (genQuadEvent || genBasicBlockEvent) {
                for (BasicBlock bb : cfg.reversePostOrder()) {
                    if (bb.isEntry() || bb.isExit())
                        continue;
                    if (genBasicBlockEvent) {
                        int bId = domB.indexOf(bb);
                        if (bId == -1)
                            throw new CannotCompileException("Cannot find index of basic block " + bb + " in domain B");
                        int bci = getBCI(bb, currMethod);
                        if (bci == -1)
                            throw new CannotCompileException("Cannot find index of basic block " + bb + " in bytecode");
                        String instr = basicBlockEventCall + bId + ");";
                        attachInstrToBCIAft(instr, bci);
                    }
                    if (genQuadEvent) {
                        int n = bb.size();
                        for (int i = 0; i < n; i++) {
                            Quad q = bb.getQuad(i);
                            if (isRelevant(q)) {
                                int pId = domP.indexOf(q);
                                if (pId == -1)
                                    throw new CannotCompileException("Cannot find index of quad " + q + " in domain P");
                                int bci = q.getBCI();
                                if (bci == -1)
                                    throw new CannotCompileException("Cannot find index of quad " + q + " in bytecode");
                                String instr = quadEventCall + pId + ");";
                                attachInstrToBCIAft(instr, bci);
                            }
                        }
                    }
                }
            }
        }
        super.edit(method);
        // NOTE: do not move insertBefore or insertAfter or addCatch
        // calls to a method to before bytecode instrumentation, else
        // bytecode instrumentation offsets could get messed up 
        String enterStr = "";
        String leaveStr = "";
        if (Modifier.isSynchronized(mods) &&
                (acquireLockEvent.present() || releaseLockEvent.present())) {
            String syncExpr;
            if (Modifier.isStatic(mods))
                syncExpr = cName + ".class";
            else
                syncExpr = "$0";
            if (acquireLockEvent.present()) {
                int lId = acquireLockEvent.hasLoc() ? set(Lmap, -1) : EventHandler.MISSING_FIELD_VAL;
                enterStr += acquireLockEventCall + lId + "," + syncExpr + ");";
            }
            if (releaseLockEvent.present()) {
                int rId = releaseLockEvent.hasLoc() ? set(Rmap, -2) : EventHandler.MISSING_FIELD_VAL;
                leaveStr += releaseLockEventCall + rId + "," + syncExpr + ");";
            }
        }
        if (enterMethodEvent.present()) {
            int nId = enterMethodEvent.hasLoc() ? mId : EventHandler.MISSING_FIELD_VAL;
            enterStr = enterMethodEventCall + nId + ");" + enterStr;
        }
        if (currMethod == mainMethod && enterMainMethodEvent.present()) {
            enterStr = enterMainMethodEventCall + enterStr;
        }
        if (leaveMethodEvent.present()) {
            int nId = leaveMethodEvent.hasLoc() ? mId : EventHandler.MISSING_FIELD_VAL;
            leaveStr = leaveStr + leaveMethodEventCall + nId + ");";
        }
        if (!enterStr.equals("")) {
            method.insertBefore("{" + enterStr + "}");
        }
        if (!leaveStr.equals("")) {
            method.insertAfter("{" + leaveStr + "}");
            String eventCall = "{" + leaveStr + "throw($e);" + "}";
            method.addCatch(eventCall, exType);
        }
    }

    public static boolean isRelevant(Quad q) {
        Operator op = q.getOperator();
        return
            op instanceof Operator.Getfield ||
            op instanceof Operator.Invoke ||
            op instanceof Operator.Putfield ||
            op instanceof Operator.New ||
            op instanceof Operator.ALoad ||
            op instanceof Operator.AStore ||
            op instanceof Operator.Return ||
            op instanceof Operator.Getstatic ||
            op instanceof Operator.Putstatic ||
            op instanceof Operator.NewArray ||
            op instanceof Operator.Monitor;
    }

    protected int set(IndexMap<String> map, Expr e) {
        return set(map, e.indexOfOriginalBytecode());
    }

    protected String getDomainName(IndexMap<String> map) {
        if (map == Fmap) return "F";
        if (map == Emap) return "E";
        if (map == Mmap) return "M";
        if (map == Hmap) return "H";
        if (map == Imap) return "I";
        if (map == Lmap) return "L";
        if (map == Rmap) return "R";
        if (map == Pmap) return "P";
        if (map == Bmap) return "B";
        assert (false);
        return null;
    }

    protected int set(IndexMap<String> map, int bci) {
        String s = bci + "!" + currSign;
        int id = map.indexOf(s);
        if (id == -1) {
            if (verbose >= 2) Messages.log(NOT_IN_DOMAIN, getDomainName(map), s);
            id = EventHandler.UNKNOWN_FIELD_VAL;
        }
        return id;
    }

    protected int getFid(CtField field) {
        String fName = field.getName();
        String fDesc = field.getSignature();
        String cName = field.getDeclaringClass().getName();
        String s = fName + ":" + fDesc + "@" + cName;
        int id = Fmap.indexOf(s);
        if (id == -1) {
            if (verbose >= 2) Messages.log(NOT_IN_DOMAIN, getDomainName(Fmap), s);
            id = EventHandler.UNKNOWN_FIELD_VAL;
        }
        return id;
    }

    @Override
    public String insertBefore(int pos) {
        String s = bciToInstrMap.get(pos);
        // s may be null in which case this method won't
        // add any instrumentation
        if (s != null)
            s = "{ " + s + " }";
        return s;
    }

    @Override
    public void edit(NewExpr e) throws CannotCompileException {
        if (befNewEvent.present() || aftNewEvent.present()) {
            int hId = befNewEvent.hasLoc() || aftNewEvent.hasLoc() ? set(Hmap, e) : EventHandler.MISSING_FIELD_VAL;
            String befInstr = "", aftInstr = "";
            if (befNewEvent.present()) {
                befInstr = befNewEventCall + hId + ");";
            }
            if (aftNewEvent.present()) {
                String o = aftNewEvent.hasObj() ? "$_" : "null";
                aftInstr = aftNewEventCall + hId + "," + o + ");";
            }
            e.replace("{ " + befInstr + " $_ = $proceed($$); " + aftInstr + " }");
        }
    }

    @Override
    public void edit(NewArray e) throws CannotCompileException {
        if (newArrayEvent.present()) {
            int hId = newArrayEvent.hasLoc() ? set(Hmap, e) : EventHandler.MISSING_FIELD_VAL;
            String o = newArrayEvent.hasObj() ? "$_" : "null";
            String instr = newArrayEventCall + hId + "," + o + ");";
            e.replace("{ $_ = $proceed($$); " + instr + " }");
        }
    }

    @Override
    public void edit(FieldAccess e) throws CannotCompileException {
        boolean isStatic = e.isStatic();
        CtField field;
        CtClass type;
        try {
            field = e.getField();
            type = field.getType();
        } catch (NotFoundException ex) {
            throw new CannotCompileException(ex);
        }
        boolean isPrim = type.isPrimitive();
        boolean isWr = e.isWriter();
        String instr;
        if (isStatic) {
            if (!scheme.hasStaticEvent())
                return;
            if (isWr) {
                instr = isPrim ? putstaticPrimitive(e, field) : putstaticReference(e, field);
            } else {
                instr = isPrim ? getstaticPrimitive(e, field) : getstaticReference(e, field);
            }
        } else {
            if (!scheme.hasFieldEvent())
                return;
            if (isWr) {
                instr = isPrim ? putfieldPrimitive(e, field) : putfieldReference(e, field);
            } else {
                instr = isPrim ? getfieldPrimitive(e, field) : getfieldReference(e, field);
            }
        }
        if (instr != null)
            e.replace(instr);
    }

    @Override
    public void edit(ArrayAccess e) throws CannotCompileException {
        if (scheme.hasArrayEvent()) {
            boolean isWr = e.isWriter();
            boolean isPrim = e.getElemType().isPrimitive();
            String instr;
            if (isWr) {
                instr = isPrim ? astorePrimitive(e) : astoreReference(e);
            } else {
                instr = isPrim ? aloadPrimitive(e) : aloadReference(e);
            }
            if (instr != null)
                e.replace(instr);
        }
    }

    @Override
    public void edit(MonitorEnter e) throws CannotCompileException {
        if (acquireLockEvent.present()) {
            int lId = acquireLockEvent.hasLoc() ? set(Lmap, e) : EventHandler.MISSING_FIELD_VAL;
            String o = acquireLockEvent.hasObj() ? "$0" : "null";
            String instr = acquireLockEventCall + lId + "," + o + ");";
            e.replace("{ $proceed(); " + instr + " }");
        }
    }

    @Override
    public void edit(MonitorExit e) throws CannotCompileException {
        if (releaseLockEvent.present()) {
            int rId = releaseLockEvent.hasLoc() ? set(Rmap, e) : EventHandler.MISSING_FIELD_VAL;
            String o = releaseLockEvent.hasObj() ? "$0" : "null";
            String instr = releaseLockEventCall + rId + "," + o + ");";
            e.replace("{ " + instr + " $proceed(); }");
        }
    }

    @Override
    public void edit(ConstructorCall e) throws CannotCompileException {
        CtConstructor m;
        try {
            m = e.getConstructor();
        } catch (NotFoundException ex) {
            throw new CannotCompileException(ex);
        } 
        edit(e, m);
    }

    @Override
    public void edit(MethodCall e) throws CannotCompileException {
        CtMethod m;
        try {
            m = e.getMethod();
        } catch (NotFoundException ex) {
            throw new CannotCompileException(ex);
        }
        edit(e, m);
    }

    private void edit(MethodCall e, CtBehavior m) throws CannotCompileException {
        String befInstr = "";
        String aftInstr = "";
        // Part 1: add METHOD_CALL event if present
        if (befMethodCallEvent.present() || aftMethodCallEvent.present()) {
            int iId = befMethodCallEvent.hasLoc() || aftMethodCallEvent.hasLoc() ?  set(Imap, e) : EventHandler.MISSING_FIELD_VAL;
            String o = befMethodCallEvent.hasObj() || aftMethodCallEvent.hasObj() ?  "$0" : "null";
            if (befMethodCallEvent.present())
                befInstr += befMethodCallEventCall + iId + "," + o + ");";
            if (aftMethodCallEvent.present())
                aftInstr += aftMethodCallEventCall + iId + "," + o + ");";
        }
        // Part 2: add THREAD_START, THREAD_JOIN, WAIT, NOTIFY_ANY, NOTIFY_ALL events
        // if present and applicable
        String instr = processThreadRelatedCall(e, m);
        if (instr != null)
            befInstr += instr;
        if (befInstr.equals("") && aftInstr.equals(""))
            return;
        // NOTE: the following must be executed only if at least befInstr or aftInstr is non-null.
        //  Otherwise, all call sites in the program will be replaced, and this can cause null
        // pointer exceptions in certain cases (i.e. $_ = $proceed($$) does not seem to be safe
        // usage for all call sites).
        // Hack: check if the target method declares exceptions it might throw and don't put
        // try...catch around the call site if it does.  This is because IBM J9 JVM does not like
        // try...catch blocks around call sites that call methods it wants to inline, and it does
        // not seem to inline methods that may throw exceptions.  This is a hack because the
        // target method may throw an undeclared exception like RuntimeException, which will
        // cause aftInstr to be bypassed.
        try {
            if (!aftInstr.equals("") && m.getExceptionTypes().length != 0) {
                e.replace("{ " + befInstr + " try { $_ = $proceed($$); } " +
                    "catch (java.lang.Throwable ex) { " + aftInstr + "; throw ex; }; " + aftInstr + " }");
            } else {
                e.replace("{ " + befInstr + " $_ = $proceed($$); " + aftInstr + " }");
            }
        } catch (NotFoundException ex) {
            throw new CannotCompileException(ex);
        }
    }

    protected String getstaticPrimitive(FieldAccess e, CtField f) {
        if (getstaticPrimitiveEvent.present()) {
            int eId = getstaticPrimitiveEvent.hasLoc() ? set(Emap, e) : EventHandler.MISSING_FIELD_VAL;
            String b;
            if (getstaticPrimitiveEvent.hasBaseObj()) {
                String cName = f.getDeclaringClass().getName();
                b = cName + ".class";
            } else
                b = "null";
            int fId = getstaticPrimitiveEvent.hasFld() ? getFid(f) : EventHandler.MISSING_FIELD_VAL;
            return "{ $_ = $proceed($$); " + getstaticPriEventCall + eId + "," + b + "," + fId + "); }";
        }
        return null;
    }

    protected String getstaticReference(FieldAccess e, CtField f) {
        if (getstaticReferenceEvent.present()) {
            int eId = getstaticReferenceEvent.hasLoc() ? set(Emap, e) : EventHandler.MISSING_FIELD_VAL;
            String b;
            if (getstaticReferenceEvent.hasBaseObj()) {
                String cName = f.getDeclaringClass().getName();
                b = cName + ".class";
            } else
                b = "null";
            int fId = getstaticReferenceEvent.hasFld() ? getFid(f) : EventHandler.MISSING_FIELD_VAL;
            String o = getstaticReferenceEvent.hasObj() ? "$_" : "null";
            return "{ $_ = $proceed($$); " + getstaticRefEcentCall + eId + "," + b + "," + fId + "," + o + "); }";
        }
        return null;
    }

    protected String putstaticPrimitive(FieldAccess e, CtField f) {
        if (putstaticPrimitiveEvent.present()) {
            int eId = putstaticPrimitiveEvent.hasLoc() ? set(Emap, e) : EventHandler.MISSING_FIELD_VAL;
            String b;
            if (putstaticPrimitiveEvent.hasBaseObj()) {
                String cName = f.getDeclaringClass().getName();
                b = cName + ".class";
            } else
                b = "null";
            int fId = putstaticPrimitiveEvent.hasFld() ? getFid(f) : EventHandler.MISSING_FIELD_VAL;
            return "{ $proceed($$); " + putstaticPriEventCall + eId + "," + b + "," + fId + "); }";
        }
        return null;
    }

    protected String putstaticReference(FieldAccess e, CtField f) {
        if (putstaticReferenceEvent.present()) {
            int eId = putstaticReferenceEvent.hasLoc() ? set(Emap, e) : EventHandler.MISSING_FIELD_VAL;
            String b;
            if (putstaticReferenceEvent.hasBaseObj()) {
                String cName = f.getDeclaringClass().getName();
                b = cName + ".class";
            } else
                b = "null";
            int fId = putstaticReferenceEvent.hasFld() ? getFid(f) : EventHandler.MISSING_FIELD_VAL;
            String o = putstaticReferenceEvent.hasObj() ? "$1" : "null";
            return "{ $proceed($$); " + putstaticRefEventCall + eId + "," + b + "," + fId + "," + o + "); }";
        }
        return null;
    }

    protected String getfieldPrimitive(FieldAccess e, CtField f) {
        if (getfieldPrimitiveEvent.present()) {
            int eId = getfieldPrimitiveEvent.hasLoc() ? set(Emap, e) : EventHandler.MISSING_FIELD_VAL;
            String b = getfieldPrimitiveEvent.hasBaseObj() ? "$0" : "null";
            int fId = getfieldPrimitiveEvent.hasFld() ? getFid(f) : EventHandler.MISSING_FIELD_VAL;
            return "{ $_ = $proceed($$); " + getfieldPriEventCall + eId + "," + b + "," + fId + "); }"; 
        }
        return null;
    }

    protected String getfieldReference(FieldAccess e, CtField f) {
        if (getfieldReferenceEvent.present()) {
            int eId = getfieldReferenceEvent.hasLoc() ? set(Emap, e) : EventHandler.MISSING_FIELD_VAL;
            String b = getfieldReferenceEvent.hasBaseObj() ? "$0" : "null";
            int fId = getfieldReferenceEvent.hasFld() ? getFid(f) : EventHandler.MISSING_FIELD_VAL;
            String o = getfieldReferenceEvent.hasObj() ? "$_" : "null";
            return "{ $_ = $proceed($$); " + getfieldReference + eId + "," + b + "," + fId + "," + o + "); }"; 
        }
        return null;
    }

    protected String putfieldPrimitive(FieldAccess e, CtField f) {
        if (putfieldPrimitiveEvent.present()) {
            int eId = putfieldPrimitiveEvent.hasLoc() ? set(Emap, e) : EventHandler.MISSING_FIELD_VAL;
            String b = putfieldPrimitiveEvent.hasBaseObj() ? "$0" : "null";
            int fId = putfieldPrimitiveEvent.hasFld() ? getFid(f) : EventHandler.MISSING_FIELD_VAL;
            return "{ $proceed($$); " + putfieldPriEventCall + eId + "," + b + "," + fId + "); }"; 
        }
        return null;
    }

    protected String putfieldReference(FieldAccess e, CtField f) {
        if (putfieldReferenceEvent.present()) {
            int eId = putfieldReferenceEvent.hasLoc() ? set(Emap, e) : EventHandler.MISSING_FIELD_VAL;
            String b = putfieldReferenceEvent.hasBaseObj() ? "$0" : "null";
            int fId = putfieldReferenceEvent.hasFld() ? getFid(f) : EventHandler.MISSING_FIELD_VAL;
            String o = putfieldReferenceEvent.hasObj() ? "$1" : "null";
            return "{ $proceed($$); " + putfieldRefEventCall + eId + "," + b + "," + fId + "," + o + "); }"; 
        }
        return null;
    }

    protected String aloadPrimitive(ArrayAccess e) {
        if (aloadPrimitiveEvent.present()) {
            int eId = aloadPrimitiveEvent.hasLoc() ? set(Emap, e) : EventHandler.MISSING_FIELD_VAL;
            String b = aloadPrimitiveEvent.hasBaseObj() ? "$0" : "null";
            String i = aloadPrimitiveEvent.hasIdx() ? "$1" : "-1";
            return "{ $_ = $proceed($$); " + aloadPriEventCall + eId + "," + b + "," + i + "); }"; 
        }
        return null;
    }

    protected String aloadReference(ArrayAccess e) {
        if (aloadReferenceEvent.present()) {
            int eId = aloadReferenceEvent.hasLoc() ? set(Emap, e) : EventHandler.MISSING_FIELD_VAL;
            String b = aloadReferenceEvent.hasBaseObj() ? "$0" : "null";
            String i = aloadReferenceEvent.hasIdx() ? "$1" : "-1";
            String o = aloadReferenceEvent.hasObj() ? "$_" : "null";
            return "{ $_ = $proceed($$); " + aloadRefEventCall + eId + "," + b + "," + i + "," + o + "); }"; 
        }
        return null;
    }

    protected String astorePrimitive(ArrayAccess e) {
        if (astorePrimitiveEvent.present()) {
            int eId = astorePrimitiveEvent.hasLoc() ? set(Emap, e) : EventHandler.MISSING_FIELD_VAL;
            String b = astorePrimitiveEvent.hasBaseObj() ? "$0" : "null";
            String i = astorePrimitiveEvent.hasIdx() ? "$1" : "-1";
            return "{ $proceed($$); " + astorePriEventCall + eId + "," + b + "," + i + "); }"; 
        }
        return null;
    }

    protected String astoreReference(ArrayAccess e) {
        if (astoreReferenceEvent.present()) {
            int eId = astoreReferenceEvent.hasLoc() ? set(Emap, e) : EventHandler.MISSING_FIELD_VAL;
            String b = astoreReferenceEvent.hasBaseObj() ? "$0" : "null";
            String i = astoreReferenceEvent.hasIdx() ? "$1" : "-1";
            String o = astoreReferenceEvent.hasObj() ? "$2" : "null";
            return "{ $proceed($$); " + astoreRefEventCall + eId + "," + b + "," + i + "," + o + "); }"; 
        }
        return null;
    }

    protected String processThreadRelatedCall(MethodCall e, CtBehavior m) {
        String instr = null;
        String cName = m.getDeclaringClass().getName();
        if (cName.equals("java.lang.Object")) {
            String mName = m.getName();
            String mDesc = m.getSignature();
            if (mName.equals("wait") && mDesc.equals("()V")) {
                if (waitEvent.present()) {
                    int iId = waitEvent.hasLoc() ? set(Imap, e) : EventHandler.MISSING_FIELD_VAL;
                    String o = waitEvent.hasObj() ? "$0" : "null";
                    instr = waitEventCall + iId + "," + o + ");";
                }
            } else if (mName.equals("notifyAll") && mDesc.equals("()V")) {
                if (notifyAllEvent.present()) {
                    int iId = notifyAllEvent.hasLoc() ? set(Imap, e) : EventHandler.MISSING_FIELD_VAL;
                    String o = notifyAllEvent.hasObj() ? "$0" : "null";
                    instr = notifyAllEventCall + iId + "," + o + ");";
                }
            } else if (mName.equals("notify") && mDesc.equals("()V")) {
                if (notifyAnyEvent.present()) {
                    int iId = notifyAnyEvent.hasLoc() ? set(Imap, e) : EventHandler.MISSING_FIELD_VAL;
                    String o = notifyAnyEvent.hasObj() ? "$0" : "null";
                    instr = notifyAnyEventCall + iId + "," + o + ");";
                }
            }
        } else if (cName.equals("java.lang.Thread")) {
            String mName = m.getName();
            String mDesc = m.getSignature();
            if (mName.equals("start") && mDesc.equals("()V")) {
                if (threadStartEvent.present()) {
                    int iId = threadStartEvent.hasLoc() ? set(Imap, e) : EventHandler.MISSING_FIELD_VAL;
                    String o = threadStartEvent.hasObj() ? "$0" : "null";
                    instr = threadStartEventCall + iId + "," + o + ");";
                }
            } else if (mName.equals("join") && mDesc.equals("()V")) {
                if (threadJoinEvent.present()) {
                    int iId = threadJoinEvent.hasLoc() ? set(Imap, e) : EventHandler.MISSING_FIELD_VAL;
                    String o = threadJoinEvent.hasObj() ? "$0" : "null";
                    instr = threadJoinEventCall + iId + "," + o + ");";
                }
            }
        } else if (cName.startsWith("java.util.concurrent.locks.") && cName.endsWith("ConditionObject")) {
            String mName = m.getName();
            String mDesc = m.getSignature();
            if (mName.equals("await") && mDesc.equals("()V")) {
                if (waitEvent.present()) {
                    int iId = waitEvent.hasLoc() ? set(Imap, e) : EventHandler.MISSING_FIELD_VAL;
                    String o = waitEvent.hasObj() ? "$0" : "null";
                    instr = waitEventCall + iId + "," + o + ");";
                }
            } else if (mName.equals("signalAll") && mDesc.equals("()V")) {
                if (notifyAllEvent.present()) {
                    int iId = notifyAllEvent.hasLoc() ? set(Imap, e) : EventHandler.MISSING_FIELD_VAL;
                    String o = notifyAllEvent.hasObj() ? "$0" : "null";
                    instr = notifyAllEventCall + iId + "," + o + ");";
                }
            } else if (mName.equals("signal") && mDesc.equals("()V")) {
                if (notifyAnyEvent.present()) {
                    int iId = notifyAnyEvent.hasLoc() ? set(Imap, e) : EventHandler.MISSING_FIELD_VAL;
                    String o = notifyAnyEvent.hasObj() ? "$0" : "null";
                    instr = notifyAnyEventCall + iId + "," + o + ");";
                }
            }
        }
        return instr;
    }
}
