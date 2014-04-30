package chord.program;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;

import chord.program.Program;
import chord.project.analyses.JavaAnalysis;
import chord.project.Chord;
import chord.project.Config;

import joeq.Class.jq_Class;
import joeq.Class.jq_Array;
import joeq.Class.jq_Field;
import joeq.Class.jq_InstanceField;
import joeq.Class.jq_Member;
import joeq.Class.jq_Method;
import joeq.Class.jq_NameAndDesc;
import joeq.Class.jq_Reference;
import joeq.Class.jq_StaticField;
import joeq.Class.jq_Type;
import joeq.Class.jq_Reference.jq_NullType;
import joeq.Compiler.BytecodeAnalysis.BytecodeVisitor;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ExceptionHandler;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operand.BasicBlockTableOperand;
import joeq.Compiler.Quad.Operand.Const4Operand;
import joeq.Compiler.Quad.Operand.Const8Operand;
import joeq.Compiler.Quad.Operand.ConstOperand;
import joeq.Compiler.Quad.Operand.IntValueTableOperand;
import joeq.Compiler.Quad.Operand.MethodOperand;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.TargetOperand;
import joeq.Compiler.Quad.Operand.TypeOperand;
import joeq.Compiler.Quad.Operator.ALength;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Binary;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Goto;
import joeq.Compiler.Quad.Operator.InstanceOf;
import joeq.Compiler.Quad.Operator.IntIfCmp;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Jsr;
import joeq.Compiler.Quad.Operator.LookupSwitch;
import joeq.Compiler.Quad.Operator.Monitor;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Ret;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.TableSwitch;
import joeq.Compiler.Quad.Operator.Unary;
import joeq.Compiler.Quad.Operator.ALoad.ALOAD_P;
import joeq.Compiler.Quad.Operator.AStore.ASTORE_P;
import joeq.Compiler.Quad.Operator.IntIfCmp.IFCMP_A;
import joeq.Compiler.Quad.Operator.IntIfCmp.IFCMP_I;
import joeq.Compiler.Quad.Operator.MultiNewArray.MULTINEWARRAY;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Operator.Return.RETURN_D;
import joeq.Compiler.Quad.Operator.Return.RETURN_F;
import joeq.Compiler.Quad.Operator.Return.RETURN_I;
import joeq.Compiler.Quad.Operator.Return.RETURN_L;
import joeq.Compiler.Quad.Operator.Return.RETURN_V;
import joeq.Compiler.Quad.Operator.Return.THROW_A;
import joeq.Compiler.Quad.Operator.Special.GET_EXCEPTION;
import joeq.Compiler.Quad.QuadVisitor.EmptyVisitor;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.util.IndexSet;

public class QuadToJasmin {
    // include Quads as comments in .j files
    private static final boolean IncludeQuad = true;
    private static final boolean IncludeLineNum = true;
    private static final boolean PrintBBInOut = false;
    // Soot version of Jasmin requires field name be enclosed by quotation marks 
    // (e.g .field "field1")
    private static final boolean UseJasminSootVersion = true;
    
    private static final JasminQuadVisitor visitor = new JasminQuadVisitor();
    private static PrintStream out;
    
    // map from register to var table index
    private static HashMap<Register, Integer> Register2VariableTableIdx = new HashMap<Register, Integer>();
    
    private static List<String> methodBody = new LinkedList<String>();
    
    /**
     * Output Jasmin code to the output stream
     * @param jasminCode
     */
    private static void put(String jasminCode) {
        out.println(jasminCode);
    }
    
    /**
     * Append a Jasmin instruction to the body of current method 
     * @param jasminInstCode string representation of Jasmin code
     */
    private static void putInst(String jasminInstCode) {
        methodBody.add(jasminInstCode);
    }

    /**
     * Filter out classes which we don't want to translate 
     * @param className
     * @return true if we want to filter out this class
     */
    private static boolean filterOut(String className) {
        for (String s : Config.checkExcludeAry) {
            if (className.startsWith(s)) return true;
        }
        
        return false;
    }

    /**
     * Create a output file for a class
     * @param c class that is being translated
     */
    private static void prepareOutputStream(jq_Class c) {

        File outFile = new File(Config.outDirName + "/" +c.getName().replace(".", "/")+".j");
        outFile.getParentFile().mkdirs();

        try {
            if (out != null) out.close();
            out = new PrintStream(outFile);
        } catch (FileNotFoundException e) {
            System.out.println("## path : " + outFile.getAbsolutePath());
            e.printStackTrace();
            System.exit(-1);
        }
    }    

    /**
     * Return access modifier string of this member
     * @param member
     * @return String representation of the access modifier of this member
     */
    private static String getAccessModifier(jq_Member member) {
        String ret = "";
        if (member.isPublic()) ret += " public";
        if (member.isPrivate()) ret += " private";
        if (member.isProtected()) ret += " protected";
        if (member.isFinal()) ret += " final";
        if (member.isStatic()) ret += " static";
        if (member instanceof jq_Field) {
            jq_Field field = (jq_Field)member;
            if (field.isTransient()) ret += " transient";
            if (field.isVolatile()) ret += " volatile";
        } else if (member instanceof jq_Method) {
            jq_Method m = (jq_Method)member;
            if (m.isAbstract()) ret += " abstract";
            if (m.isSynchronized()) ret += " synchronized";
            if (m.isNative()) ret += " native";
        }
        
        return ret;        
    }

    private static String replaceEscapedCharacters(String str) {
        str = str.replaceAll("\\\\", "\\\\\\\\");
        str = str.replaceAll("\\n", "\\\\n");
        str = str.replaceAll("\\t", "\\\\t");
        str = str.replaceAll("\\f", "\\\\f");
        str = str.replaceAll("\\r", "\\\\r");
        str = str.replaceAll("\\'", "\\\\\'");
        str = str.replaceAll("\\\"", "\\\\\\\"");
        
        return str;
    }
    
    public void run() {
        Program program = Program.g();
        IndexSet<jq_Reference> classes = program.getClasses();        
        for (jq_Reference r : classes) {
            if (r instanceof jq_Array)
                continue;

            System.out.println("\n#### class : " + r);
            jq_Class c = (jq_Class) r;
            String cname = c.getName();
            // Filter out classes
            if (filterOut(cname))
                continue;

            // create an output file and the associated output stream
            prepareOutputStream(c);
                        
            // output "source" attribute
            String fileName = c.getSourceFile().toString();

            if (fileName != null) {
                int idx = fileName.lastIndexOf("/");
                if (idx >=0 ) {
                    fileName = fileName.substring(idx+1);
                }
                put(".source " + fileName);
            }

            // output class name
            if (c.isPublic()) cname = "public " + cname;
            if (c.isFinal()) cname = "final " + cname;        
            if (c.isAbstract()) cname = "abstract " + cname;
            String type = null;
            if (c.isClassType()) {
                type = ".class";
                if (c.isInterface()) {
                    type = ".interface";
                }
            }
            put(type + " " + cname.replace(".", "/"));

            // output "superclass" attribute
            jq_Class super_c = c.getSuperclass();
            if (super_c != null) {
                put(".super " + super_c.getName().replace(".", "/"));                                                
            }            
            // output "implements" attribute
            for (jq_Class jqif : c.getDeclaredInterfaces()) {
                put(".implements " + jqif.getName().replace(".", "/"));
            }
            put("");

            // output static fields
            for (jq_StaticField jqsf : c.getDeclaredStaticFields()) {
                String name = jqsf.getName().toString();
                if (UseJasminSootVersion) {
                    name = "\"" + name + "\"";
                }
                String attrName = ".field";
                String access = getAccessModifier(jqsf);                
                String typeDesc = jqsf.getType().getDesc().toString();                
                String attr = attrName + access + " "+ name + " " + typeDesc;
                Object o = jqsf.getConstantValue();
                if (o != null) {
                    if (o instanceof String) {
                        String s = (String)o;
                        attr += " = \"" + replaceEscapedCharacters(s) + "\"";
                    } else if (o instanceof Float) {
                        // handling special constants
                        if ( ((Float)o).floatValue() == Float.POSITIVE_INFINITY ) {
                            attr += " = +FloatInfinity";
                        } else if ( ((Float)o).floatValue() == Float.NEGATIVE_INFINITY ) {
                            attr += " = -FloatInfinity";
                        } else if ( ((Float)o).isNaN() ) {
                            attr += " = +FloatNaN";
                        }                        
                    } else if (o instanceof Double) {
                        // handling special constants
                        if ( ((Double)o).doubleValue() == Double.POSITIVE_INFINITY ) {
                            attr += " = +DoubleInfinity";
                        } else if ( ((Double)o).doubleValue() == Double.NEGATIVE_INFINITY ) {
                            attr += " = -DoubleInfinity";
                        } else if ( ((Double)o).isNaN() ) {
                            attr += " = +DoubleNaN";
                        }                        
                    } else {
                        attr += " = " + o;
                    }                                        
                }
                put(attr);
            }
            
            // output non-static fields
            for (jq_InstanceField jqif : c.getDeclaredInstanceFields()) {
                String name = jqif.getName().toString();
                if (UseJasminSootVersion) {
                    name = "\"" + name + "\"";
                }
                String attrName = ".field";
                String access = getAccessModifier(jqif);                
                String typeDesc = jqif.getType().getDesc().toString();                
                String attr = attrName + access + " "+ name + " " + typeDesc;                
                put(attr);
            }
            put("");            

            // process static methods
            for (jq_Method m : c.getDeclaredStaticMethods()) {
                processMethod(m);
            }
            
            // process non-static methods
            for (jq_Method m : c.getDeclaredInstanceMethods()) {
                processMethod(m);
            }
        }

        out.flush();
        out.close();
    }

    private static BasicBlock current_BB;

    // map from basic block id to set of Quads to be added
    private static HashMap<Integer, Set<Quad>> PhiOperandsMap = new HashMap<Integer, Set<Quad>>();

    /**
     * Create a Quad which stores src to dst, associate it with bb.
     * The created Quad will be added to the bb to handle a variable (dst)
     * whose value (src) depends on its predecessor basic block (bb).
     * @param bb
     * @param src
     * @param dst
     */
    static void addPhiOperands(BasicBlock bb, RegisterOperand src, RegisterOperand dst) {
        
        Set<Quad> s;
        if (PhiOperandsMap.containsKey(bb.getID())) {
            s = PhiOperandsMap.get(bb.getID());
        } else {
            s = new HashSet<Quad>();
            PhiOperandsMap.put(bb.getID(), s);
        }
        jq_Method m = src.getQuad().getMethod();
        if (src != null) {            
            jq_Type type = src.getType();
            if ( type == null) type = dst.getType();
            Quad q = Operator.Move.create(-1, bb, dst.getRegister(), src.getRegister(), type);
            s.add(q);
        } else {
            // in this case, the register is never defined along
            // this particular control flow path into the basic
            // block.
        }                        
    }
    
    /**
     * check if this Quad is conditional branch operation
     * @param d Quad to check
     * @return true if it is conditional branch, false otherwise
     */
    static private boolean isCondBranch(Quad d) {
        Operator operator = d.getOperator();
        if (operator instanceof Operator.Return
                || operator instanceof Operator.Goto
                || operator instanceof Operator.Jsr
                || operator instanceof Operator.Ret) {
            return false;
        }
        return true;        
    }
    
    /**
     * Performs DFS-like search for the exit of method or conditional branch 
     * @param visitedBBSet : set of IDes of basic blocks visited 
     * @param currentBB : currently visiting basic block  
     * @return true if it can reach neither the exit of method nor a conditional
     *  branch, false otherwise
     */
    private static boolean destineToCycle(HashSet<Integer> visitedBBSet, BasicBlock currentBB) {
        if (visitedBBSet.contains(currentBB.getID())) return true;
        
        if (currentBB.isExit()) return false;
        if (currentBB.size() > 0 && isCondBranch(currentBB.getLastQuad())) return false;
        
        visitedBBSet.add(currentBB.getID());
        List<BasicBlock> successors = currentBB.getSuccessors();
        if (successors.size() == 1) {
            return destineToCycle(visitedBBSet, successors.get(0));    
        }
        
        for (int i=0; i < successors.size(); i++) {
            BasicBlock child = successors.get(i);
            if (visitedBBSet.contains(child.getID())) continue;
            if (!destineToCycle((HashSet<Integer>) visitedBBSet.clone(),child)) return false;
        }
        
        return true;
    }
    
    /**
     * Add explicit goto if needed.
     * @param m
     */
    private static void addGotoToBB(jq_Method m) {
        
        // get a list of basic blocks
        ControlFlowGraph cfg = m.getCFG();
        List<BasicBlock> basicBlockList = cfg.reversePostOrder();
        int numBasicBlocks = basicBlockList.size();        
        
        // add goto if the next block in the list is not a fall-through successor
        for (int i=0; i<numBasicBlocks; i++) {
            BasicBlock bb = basicBlockList.get(i);
            if (bb.isEntry() || bb.isExit()) continue;
            
            BasicBlock next_bb = i+1 < numBasicBlocks ? basicBlockList.get(i+1) : null;            
            
            if ( (bb.size() == 0 || isCondBranch(bb.getLastQuad()))
                    && (next_bb == null || bb.getFallthroughSuccessor().getID() != next_bb.getID()) ) {            
                TargetOperand target = null;
                if (bb.size() == 0) {
                    // if this bb is empty for some reason (e.g. all statements are sliced out)
                    List<BasicBlock> successors = bb.getSuccessors();
                    if (successors.size() == 1) {
                        if (next_bb != null && 
                                bb.getFallthroughSuccessor().getID() == next_bb.getID()) {
                            // If next bb in our order is the fall-through successor, do nothing
                            continue;
                        } else {
                            // Otherwise, use the successor as the target of goto 
                            target = new TargetOperand(successors.get(0));
                        }
                    } else {
                        // We need to be careful such that we don't create a cycle.
                        // Pick a successor that can reach either the exit of the
                        // method or conditional branch. 
                        assert successors.size() > 1 : bb;
                        for (int j=0; j < successors.size(); j++) {                            
                            BasicBlock successor = successors.get(j);
                            HashSet<Integer> set = new HashSet<Integer>();
                            set.add(bb.getID());
                            if (!destineToCycle(set, successor)) {
                                target = new TargetOperand(successor);
                                break;
                            }
                        }
                        assert target != null : "All paths end up cycle : " + bb;
                    }
                } else if (isCondBranch(bb.getLastQuad())) {
                    // if conditional branch
                    target = new TargetOperand(bb.getFallthroughSuccessor());
                } else continue;
                assert target != null;
                bb.appendQuad(Operator.Goto.create(-1, bb, Operator.Goto.GOTO.INSTANCE, target));
            }
        }
    }
    
    private static void processMethod(jq_Method m) {

        jq_NameAndDesc nd = m.getNameAndDesc();
        String access = getAccessModifier(m);

        put(".method" + access + " "  + nd.getName() + nd.getDesc());
        System.out.println("METHOD " + nd.getName() + nd.getDesc());
        
        if (m.isAbstract()) {
            put(".end method");
            return;
        }
        ControlFlowGraph cfg = m.getCFG();        
        RegisterFactory rf = cfg.getRegisterFactory();
        
        methodBody.clear();                
        PhiOperandsMap.clear();        
        Register2VariableTableIdx.clear();
        int sizeLocalVarTable = 0;
        
        // Assign variable indices to registers used
        for (int i=0; i < rf.size(); i ++) {
            Register reg = rf.get(i);
            jq_Type type = reg.getType();
            Register2VariableTableIdx.put(reg, sizeLocalVarTable);
            // some variable may occupy two slots (e.g. long type)
            sizeLocalVarTable += (type.getReferenceSize() / 4);
        }        
        
        
        // insert a temporary basic block after an entry BB as its fall-through BB
        // (since we can't add a Quad to entry BB)
        {
            // We could do this only if necessary but for now we add this for all entry BBes
            BasicBlock entry = cfg.entry();
            assert entry.getSuccessors().size() == 1;
            BasicBlock newBB = cfg.createBasicBlock(1, 1, 1, null);
            BasicBlock orgSuccessor = entry.getSuccessors().get(0);
            newBB.addSuccessor(orgSuccessor);
            orgSuccessor.removePredecessor(entry);
            orgSuccessor.addPredecessor(newBB);
            newBB.addPredecessor(entry);
            entry.removeSuccessor(0);
            entry.addSuccessor(newBB);
        }
        
        // get a list of basic blocks
        List<BasicBlock> basicBlockList = cfg.reversePostOrder();
        int numBasicBlocks = basicBlockList.size();
        
        // add goto if the next block in the list is not a fall-through successor
        addGotoToBB(m);
        
        // Collect Phi operands info
        for (BasicBlock bb : basicBlockList) {
                for (Quad q : bb.getQuads()) {
                if (q.getOperator() instanceof Phi) {
                    RegisterOperand dst = Phi.getDest(q);
                    BasicBlockTableOperand bbto = Phi.getPreds(q);
                    ParamListOperand plo = Phi.getSrcs(q);            
                    for (int i=0; i < plo.length(); i++) {
                        BasicBlock  srcBB = bbto.get(i);
                        Quad lastQuad = srcBB.getLastQuad();
                        // JSR is handled differently (by JasminQuadVistor.visitPhi())
                        if (lastQuad != null 
                                && lastQuad.getOperator() instanceof Operator.Jsr) {
                            continue;
                        }
                        // if source path is entry, use the temp BB we created instead
                        if (srcBB.isEntry())
                            srcBB = srcBB.getFallthroughSuccessor();
                        
                        addPhiOperands(srcBB, plo.get(i), dst);
                    }
                }
            }
        }

        visitor.init();
        
        numBasicBlocks = basicBlockList.size();

        for (int i=0; i<numBasicBlocks; i++) {
            BasicBlock bb = basicBlockList.get(i);
            current_BB = bb;
                        
            Quad lastQuad = bb.getLastQuad();

            putInst(bb.toString().split("\\s+")[0]+":"); // start of basic block
            
            if (PrintBBInOut) {
                StringBuffer sb = new StringBuffer();
                
                sb.append("\t(in: ");
                Iterator<BasicBlock> bbi = bb.getPredecessors().iterator();
                if (!bbi.hasNext()) sb.append("<none>");
                else {
                    sb.append(bbi.next().toString());
                    while (bbi.hasNext()) {
                        sb.append(", ");
                        sb.append(bbi.next().toString());
                    }
                }
                sb.append(", out: ");
                bbi = bb.getSuccessors().iterator();
                if (!bbi.hasNext()) sb.append("<none>");
                else {
                    sb.append(bbi.next().toString());
                    while (bbi.hasNext()) {
                        sb.append(", ");
                        sb.append(bbi.next().toString());
                    }
                }
                sb.append(')');
                putInst("; " + sb.toString());
                System.out.println(sb.toString());
            }
            
            // If this basic block is a predecessor of a basic block containing Phi operation,
            // insert an appropriate Quad
            if (PhiOperandsMap.containsKey(bb.getID())) {
                int idx = bb.getQuadIndex(lastQuad);
                for (Quad q : PhiOperandsMap.get(bb.getID())) {                    
                    if (idx >=0 && (lastQuad.getOperator() instanceof Operator.Branch
                            || lastQuad.getOperator() instanceof Operator.Return)) {
                        // if the last Quad is a branch, insert it before the branch
                        bb.addQuad(idx, q);
                    } else {
                        // otherwise append it at the end
                        bb.appendQuad(q);
                    }
                }
            }
            
            // Process each quad in this basic block
            for (Quad q : bb.getQuads()) {
                System.out.println(q);
                q.accept(visitor);
            }
            
            putInst(bb.toString().split("\\s+")[0]+"_END:");
        }
        
        put(".limit stack " + visitor.maxStackSize);
        put(".limit locals " + sizeLocalVarTable);
        
        //Sort the local variables by index
        Set<Entry<Register, Integer>> sortedSet = new TreeSet<Entry<Register, Integer>>(
                new Comparator<Entry<Register, Integer>>() {
            public int compare(Entry<Register, Integer> o1,
                    Entry<Register, Integer> o2) {
                return o1.getValue().compareTo(o2.getValue());
            }});
        sortedSet.addAll(Register2VariableTableIdx.entrySet());
        // Print local variable array
        for (Entry<Register, Integer> e : sortedSet) {
            Register reg = e.getKey();
            int idx = e.getValue();
            jq_Type type = visitor.regToTypeMap.containsKey(reg)?visitor.regToTypeMap.get(reg):reg.getType();
            String endLabel = basicBlockList.get(numBasicBlocks-1).toString().split("\\s+")[0] + "_END";
            put(".var " + idx + " is " + reg + " " 
                    + type.getDesc() + " from BB0 to "
                    + endLabel);
        }        
        
        put("");
        
        // output bodies of this method
        for (String s : methodBody) {
            put(s);
        }

        put("");
        // Thrown exceptions
        jq_Class[] thrownExceptionTable = m.getThrownExceptionsTable();
        if (thrownExceptionTable != null) {
            for (jq_Class exception : thrownExceptionTable) {
                put(".throws " + exception.getName().replace(".", "/"));
            }
        }
        
        // Exception Handlers
        for (ExceptionHandler handler : cfg.getExceptionHandlers()) {
            List<BasicBlock> handledBBList = handler.getHandledBasicBlocks();
            for (BasicBlock handledBB : handledBBList) {
                put(".catch " + handler.getExceptionType().getName().replace(".", "/")
                        + " from " + handledBB.toString() + " to " + handledBB.toString() + "_END" + " using " 
                        + handler.getEntry());
            }
        }
        
        put(".end method\n");
    } // end of processMethod()


    static class JasminQuadVisitor extends EmptyVisitor {

        int maxStackSize;
        
        HashMap<Register, jq_Type> regToTypeMap = new HashMap<Register, jq_Type>();

        void init() {
            // A Quad takes at most 4 operands. 
            // Thus, when no optimization applied, setting maxStackSize to 10 
            // should be safe except for method invocation which loads all
            // arguments to the stack. visitInvoke() adjusts maxStackSize
            // appropriately

            maxStackSize = 10;
            regToTypeMap.clear();
        }

        private void putLineNum(Quad q) {
            if (IncludeLineNum) {
                int lineNum = q.getLineNumber();
                if (lineNum > 0) {
                    QuadToJasmin.putInst(".line " + lineNum);
                }
            }
        }

        private void processOperand(Operand op) {
            if (op instanceof RegisterOperand) {
                RegisterOperand regOp = (RegisterOperand)op;                    
                if (regOp.getRegister().getNumber() >= 0 && !(regOp.getType() instanceof jq_NullType)) {
                    regToTypeMap.put(regOp.getRegister(), regOp.getType());
                }
            }                                
        }
        /**
         * Collects type information of the used registers
         */
        public void visitQuad(Quad q) {
            if (IncludeQuad) {
                QuadToJasmin.putInst("; " + q);
            }
            processOperand(q.getOp1());
            processOperand(q.getOp2());
            processOperand(q.getOp3());
            processOperand(q.getOp4());
        }

        public void visitNew(Quad d) {
            putLineNum(d);
            
            QuadToJasmin.putInst("\tnew " + New.getType(d).getType().toString().replace(".", "/"));

            RegisterOperand dst = New.getDest(d);
            QuadToJasmin.putInst(getStoreInstruction(dst));            
        }
        
        public void visitNewArray(Quad d) {
            putLineNum(d);
            Operand sizeOperand = NewArray.getSize(d);
            QuadToJasmin.putInst(getOperandLoadingInst(sizeOperand));
            TypeOperand typeOperand = NewArray.getType(d);
            jq_Type type = ((jq_Array)typeOperand.getType()).getElementType();
            
            if (type.isPrimitiveType()) {
                QuadToJasmin.putInst("\tnewarray " + type.getName());
            } else if (type.isReferenceType()) {
                String typeStr = "";
                if (type.isArrayType()) {
                    typeStr =  type.getJDKDesc().replace(".", "/");
                } else {
                    typeStr = type.getName().replace(".", "/");
                }
                
                QuadToJasmin.putInst("\tanewarray " + typeStr);
            } else {
                assert false : "HANDLE this case: " + d;
            }
            RegisterOperand dst = NewArray.getDest(d);
            QuadToJasmin.putInst(getStoreInstruction(dst));            
        }
        
        public void visitMultiNewArray(Quad d) {
            putLineNum(d);
            
            jq_Type type = MULTINEWARRAY.getType(d).getType();
            ParamListOperand paramListOper = MULTINEWARRAY.getParamList(d);
            for (int i=0; i < paramListOper.length(); i++) {
                QuadToJasmin.putInst(getOperandLoadingInst(paramListOper.get(i)));
            }
            QuadToJasmin.putInst("\tmultianewarray " + type.getJDKDesc().replace(".", "/") + " " + paramListOper.length());
        }

        public void visitMove(Quad d) {
            putLineNum(d);
            QuadToJasmin.putInst(getOperandLoadingInst(Move.getSrc(d))); // load
            RegisterOperand dst = Move.getDest(d);
            QuadToJasmin.putInst(getStoreInstruction(dst));
        }

        public void visitReturn(Quad q) {
            putLineNum(q);
            Operator operator = q.getOperator();

            if (operator instanceof RETURN_V) {
                QuadToJasmin.putInst("\treturn");
                return;                
            }

            QuadToJasmin.putInst(getOperandLoadingInst(Return.getSrc(q)));
            
            if (operator instanceof THROW_A) {
                QuadToJasmin.putInst("\tathrow");
            } else if (operator instanceof RETURN_I) {
                QuadToJasmin.putInst("\tireturn");
            } else if (operator instanceof RETURN_A) {
                QuadToJasmin.putInst("\tareturn");
            } else if (operator instanceof RETURN_F) {
                QuadToJasmin.putInst("\tfreturn");
            } else if (operator instanceof RETURN_D) {
                QuadToJasmin.putInst("\tdreturn");
            } else if (operator instanceof RETURN_L) {
                QuadToJasmin.putInst("\tlreturn");
            } else assert false : "Unknown return type: " + operator;

        }

        private static String getStoreInstruction(RegisterOperand regOper) {
            jq_Type type = regOper.getType();
            String operator = "";
            String typeDesc = type.getDesc()+"";
            if (type.isIntLike()) {
                operator = "istore";
            } else if (typeDesc.startsWith("J")) {
                operator = "lstore";
            } else if (typeDesc.startsWith("F")) {
                operator = "fstore";
            } else if (typeDesc.startsWith("D")) {
                operator = "dstore";
            } else if (typeDesc.startsWith("L") || typeDesc.startsWith("[") ) {
                operator = "astore";
            } else assert false : regOper;            
            
            Integer varTableIdx = Register2VariableTableIdx.get(regOper.getRegister());
            assert varTableIdx != null;
            
            return "\t" + operator + " " + varTableIdx;
        }
        
        private static String getOperandLoadingInst(Operand operand) {
            String inst = "";
            if (operand instanceof RegisterOperand) {
                RegisterOperand regOper = (RegisterOperand)operand;
                jq_Type type = regOper.getType();
                String operator = "";
                String typeDesc = type.getDesc()+"";
                if (type.isIntLike()) {
                    operator = "iload";
                } else if (typeDesc.startsWith("J")) {
                    operator = "lload";
                } else if (typeDesc.startsWith("F")) {
                    operator = "fload";
                } else if (typeDesc.startsWith("D")) {
                    operator = "dload";
                } else if (typeDesc.startsWith("L") || typeDesc.startsWith("[") ) {
                    operator = "aload";
                } else assert false : regOper;            
                
                Integer varTableIdx = Register2VariableTableIdx.get(regOper.getRegister());
                assert varTableIdx != null;

                inst = "\t" + operator + " " + varTableIdx;
                
            } else if (operand instanceof ConstOperand) {
                if (operand instanceof AConstOperand &&
                        ((AConstOperand) operand).getValue() == null) {
                        inst = "\taconst_null";
                } else if (operand instanceof AConstOperand &&
                        ((AConstOperand) operand).getValue() instanceof String) {
                    String value = (String)((AConstOperand) operand).getValue();
                        inst = "\tldc_w \""+ replaceEscapedCharacters(value) + "\"";
                } else if (operand instanceof AConstOperand) {
                    Object val = ((AConstOperand) operand).getValue();
                    assert val instanceof java.lang.Class : operand;
                    System.out.println("class name : " + val.getClass().getName() );
                    String[] tmps = val.toString().split("\\s+");
                    inst = "\tldc_w " + tmps[tmps.length-1].replace(".", "/");
                } else if (operand instanceof Const4Operand) {
                    Object val = ((ConstOperand)operand).getWrapped();
                    if (val instanceof Float) {
                        // handling special constants
                        if ( ((Float)val).floatValue() == Float.POSITIVE_INFINITY ) {
                            val = "+FloatInfinity";
                        } else if ( ((Float)val).floatValue() == Float.NEGATIVE_INFINITY ) {
                            val = "-FloatInfinity";
                        } else if ( ((Float)val).isNaN() ) {
                            val = "+FloatNaN";
                        }                        
                    }
                    inst = "\tldc_w " + val;
                } else if (operand instanceof Const8Operand) {
                    Object val = ((ConstOperand)operand).getWrapped();
                    if (val instanceof Double) {
                        // handling special constants
                        if ( ((Double)val).doubleValue() == Double.POSITIVE_INFINITY ) {
                            val = "+DoubleInfinity";
                        } else if ( ((Double)val).doubleValue() == Double.NEGATIVE_INFINITY ) {
                            val = "-DoubleInfinity";
                        } else if ( ((Double)val).isNaN() ) {
                            val = "+DoubleNaN";
                        }
                    }
                    inst = "\tldc2_w " + val;
                } else assert false : "Unknown Const Type: " + operand;
            } else assert false : "Unknown Operand Type: " + operand;

            return inst;
        }        

        public void visitGetfield(Quad d) {
            putLineNum(d);
            RegisterOperand base = (RegisterOperand) Getfield.getBase(d);
            jq_Field field = Getfield.getField(d).getField();
            QuadToJasmin.putInst(getOperandLoadingInst(base));
            QuadToJasmin.putInst("\tgetfield " + field.getDeclaringClass().getName().replace(".", "/") +"/"+ field.getName() + " " + field.getDesc());
            RegisterOperand dst = Getfield.getDest(d);
            QuadToJasmin.putInst(getStoreInstruction(dst));

        }

        public void visitPutfield(Quad d) {
            putLineNum(d);
            RegisterOperand base = (RegisterOperand) Putfield.getBase(d);
            QuadToJasmin.putInst(getOperandLoadingInst(base));
            Operand src = Putfield.getSrc(d);
            QuadToJasmin.putInst(getOperandLoadingInst(src)); // load src
            jq_Field field = Putfield.getField(d).getField();
            QuadToJasmin.putInst("\tputfield " + field.getDeclaringClass().getName().replace(".", "/")+"/"+ field.getName() + " " + field.getDesc());
        }

        public void visitGetstatic(Quad d) {
            putLineNum(d);
            jq_Field field = Getstatic.getField(d).getField();
            QuadToJasmin.putInst("\tgetstatic " + field.getDeclaringClass().getName().replace(".", "/") +"/"+ field.getName() + " " + field.getDesc());
            RegisterOperand dst = Getstatic.getDest(d);
            QuadToJasmin.putInst(getStoreInstruction(dst));
        }

        public void visitPutstatic(Quad d) {
            putLineNum(d);
            Operand src = Putstatic.getSrc(d);
            QuadToJasmin.putInst(getOperandLoadingInst(src)); // load src
            jq_Field field = Putstatic.getField(d).getField();
            QuadToJasmin.putInst("\tputstatic " + field.getDeclaringClass().getName().replace(".", "/")+"/"+ field.getName() + " " + field.getDesc());
        }

        static private String getBinaryOpName(Binary binOp) {
            String[] strs = binOp.toString().split("_");
            assert strs.length == 2;
            
            if (binOp == Binary.CMP_DG.INSTANCE) {
                return "dcmpg";
            } else if (binOp == Binary.CMP_DL.INSTANCE) {
                return "dcmpl";
            } else if (binOp == Binary.CMP_FG.INSTANCE) {
                return "fcmpg";
            } else if (binOp == Binary.CMP_FL.INSTANCE) {
                return "fcmpl";
            } else if (binOp == Binary.CMP_L.INSTANCE) {
                return "lcmp";
            } 
            
            return strs[1].toLowerCase() + strs[0].toLowerCase();
        }


        public void visitBinary(Quad d) {
            putLineNum(d);
            Binary operator = (Binary) d.getOperator();

            QuadToJasmin.putInst(getOperandLoadingInst(Binary.getSrc1(d)));
            QuadToJasmin.putInst(getOperandLoadingInst(Binary.getSrc2(d)));

            String opname = getBinaryOpName(operator);                                    
            QuadToJasmin.putInst("\t" + opname);

            RegisterOperand dst = (RegisterOperand)Binary.getDest(d);        
            QuadToJasmin.putInst(getStoreInstruction(dst));
        }

        public void visitInvoke(Quad d) {
            putLineNum(d);
            MethodOperand mOp = Invoke.getMethod(d);
            Invoke operator = (Invoke) d.getOperator();
            jq_Method m = mOp.getMethod();
            
            String instName = "";
            String suffix = "";
            ParamListOperand paramlist = Invoke.getParamList(d);
            if ( m.isStatic()) {                
                assert operator instanceof Invoke.InvokeStatic;
                instName = "invokestatic";
            } else if ( operator instanceof Invoke.InvokeStatic ) {
                instName = "invokespecial";
            }else if ( m.isAbstract() ) {
                instName = "invokeinterface";
                suffix = " "+paramlist.length();
            } else {                
                instName = "invokevirtual";
            }                         
            
            if (paramlist.length() + 1 > this.maxStackSize) {
                this.maxStackSize = paramlist.length() + 1;
            }
            
            for (int i=0; i < paramlist.length(); i++) {
                RegisterOperand regOpr = paramlist.get(i);
                QuadToJasmin.putInst(getOperandLoadingInst(regOpr));
            }
            String inst = "\t" + instName + " " + m.getDeclaringClass().getName().replace(".", "/") + "/" + m.getName() + m.getDesc();
            
            QuadToJasmin.putInst(inst + suffix);

            RegisterOperand dst = Invoke.getDest(d);
            if (dst != null) {
                QuadToJasmin.putInst(getStoreInstruction(dst));
            }

        }

        public void visitGoto(Quad d) {
            putLineNum(d);
            TargetOperand targetOp = Goto.getTarget(d);
            QuadToJasmin.putInst("\tgoto " + targetOp.toString());
        }        

        public void visitIntIfCmp(Quad d) {
            putLineNum(d);
            IntIfCmp iic = (IntIfCmp) d.getOperator();
            if (iic instanceof IFCMP_A) {
                // To handle null comparison case
                Operand src1 = IFCMP_A.getSrc1(d);
                Operand src2 = IFCMP_A.getSrc2(d);
                if (src1 instanceof AConstOperand && 
                        ((AConstOperand)src1).getType() instanceof jq_NullType) {
                    QuadToJasmin.putInst(getOperandLoadingInst(src2));
                    if (IntIfCmp.getCond(d).getCondition() == BytecodeVisitor.CMP_EQ) {
                        QuadToJasmin.putInst("\tifnull " + IntIfCmp.getTarget(d).getTarget());
                    } else if (IntIfCmp.getCond(d).getCondition() == BytecodeVisitor.CMP_NE) {
                        QuadToJasmin.putInst("\tifnonnull " + IntIfCmp.getTarget(d).getTarget());
                    } else assert false : d;
                } else if (src2 instanceof AConstOperand && 
                        ((AConstOperand)src2).getType() instanceof jq_NullType) {
                    QuadToJasmin.putInst(getOperandLoadingInst(src1));
                    if (IntIfCmp.getCond(d).getCondition() == BytecodeVisitor.CMP_EQ) {
                        QuadToJasmin.putInst("\tifnull " + IntIfCmp.getTarget(d).getTarget());
                    } else if (IntIfCmp.getCond(d).getCondition() == BytecodeVisitor.CMP_NE) {
                        QuadToJasmin.putInst("\tifnonnull " + IntIfCmp.getTarget(d).getTarget());
                    } else assert false : d;
                } else {
                    QuadToJasmin.putInst(getOperandLoadingInst(src1));
                    QuadToJasmin.putInst(getOperandLoadingInst(src2));
                    if (IntIfCmp.getCond(d).getCondition() == BytecodeVisitor.CMP_EQ) {
                        QuadToJasmin.putInst("\tif_acmpeq " + IntIfCmp.getTarget(d).getTarget());
                    } else if (IntIfCmp.getCond(d).getCondition() == BytecodeVisitor.CMP_NE) {
                        QuadToJasmin.putInst("\tif_acmpne " + IntIfCmp.getTarget(d).getTarget());
                    } else assert false : d;                    
                }

            } else if (iic instanceof IFCMP_I) {
                Operand src1 = IFCMP_I.getSrc1(d);
                Operand src2 = IFCMP_I.getSrc2(d);
                QuadToJasmin.putInst(getOperandLoadingInst(src1));
                QuadToJasmin.putInst(getOperandLoadingInst(src2));
                byte condition = IntIfCmp.getCond(d).getCondition();
                String instSuffix = "";
                switch(condition) {
                case BytecodeVisitor.CMP_EQ:
                    instSuffix="eq"; break;
                case BytecodeVisitor.CMP_NE:
                    instSuffix="ne"; break;
                case BytecodeVisitor.CMP_LT:
                    instSuffix="lt"; break;
                case BytecodeVisitor.CMP_LE:
                    instSuffix="le"; break;
                case BytecodeVisitor.CMP_GT:
                    instSuffix="gt"; break;
                case BytecodeVisitor.CMP_GE:
                    instSuffix="ge"; break;
                default:
                    assert false : "Unexpected condition " + d;

                }
                QuadToJasmin.putInst("\tif_icmp"+instSuffix+" "+ IntIfCmp.getTarget(d).getTarget());

            } else assert false : "HANDLE THIS CASE " + d;

        }
        
        public void visitSpecial(Quad d) {
            putLineNum(d);
            Operator operator = d.getOperator();
            if (operator instanceof GET_EXCEPTION) {
                RegisterOperand dst = (RegisterOperand) GET_EXCEPTION.getOp1(d);
                QuadToJasmin.putInst(getStoreInstruction(dst));
            } else assert false : d;                
            
        }

        public void visitInstanceOf(Quad d) {
            putLineNum(d);
            QuadToJasmin.putInst(getOperandLoadingInst(InstanceOf.getSrc(d)));
            jq_Type type = InstanceOf.getType(d).getType();
            String typeStr = "";
            if (type.isArrayType()) {
                typeStr =  type.getJDKDesc().replace(".", "/");
            } else {
                typeStr = type.getName().replace(".", "/");
            }
            
            QuadToJasmin.putInst("\tinstanceof "+ typeStr);
            
            RegisterOperand dst = InstanceOf.getDest(d);        
            QuadToJasmin.putInst(getStoreInstruction(dst));            
        }
                
        public void visitALoad(Quad d) {
            putLineNum(d);
            QuadToJasmin.putInst(getOperandLoadingInst(ALoad.getBase(d)));
            QuadToJasmin.putInst(getOperandLoadingInst(ALoad.getIndex(d)));
            ALoad operator = (ALoad)d.getOperator();
            assert !(operator instanceof ALOAD_P) : d;
            String jasminInstOperator = operator.toString().split("_")[1].toLowerCase()+"aload";
            QuadToJasmin.putInst("\t"+jasminInstOperator);
            RegisterOperand dst = ALoad.getDest(d);
            QuadToJasmin.putInst(getStoreInstruction(dst));            
        }
        
        public void visitAStore(Quad d) {
            putLineNum(d);
            QuadToJasmin.putInst(getOperandLoadingInst(AStore.getBase(d)));
            QuadToJasmin.putInst(getOperandLoadingInst(AStore.getIndex(d)));
            QuadToJasmin.putInst(getOperandLoadingInst(AStore.getValue(d)));
            AStore operator = (AStore)d.getOperator();
            assert !(operator instanceof ASTORE_P) : d;
            String jasminInstOperator = operator.toString().split("_")[1].toLowerCase()+"astore";
            QuadToJasmin.putInst("\t"+jasminInstOperator);            
        }
        
        public void visitCheckCast(Quad d) {
            putLineNum(d);
            QuadToJasmin.putInst(getOperandLoadingInst(CheckCast.getSrc(d)));
            jq_Type type = CheckCast.getType(d).getType();
            String typeStr = "";
            if (type.isArrayType()) {
                typeStr =  type.getJDKDesc().replace(".", "/");
            } else {
                typeStr = type.getName().replace(".", "/");
            }
            
            QuadToJasmin.putInst("\tcheckcast " + typeStr);
            
            RegisterOperand dst = CheckCast.getDest(d);
            QuadToJasmin.putInst(getStoreInstruction(dst));    
        }
        
        public void visitMonitor(Quad d) {
            putLineNum(d);
            QuadToJasmin.putInst(getOperandLoadingInst(Monitor.getSrc(d)));
            Monitor monitorOperator = (Monitor)d.getOperator();
            if (monitorOperator instanceof Monitor.MONITORENTER) {
                QuadToJasmin.putInst("\tmonitorenter");
            } else if (monitorOperator instanceof Monitor.MONITOREXIT) {
                QuadToJasmin.putInst("\tmonitorexit");
            } else assert false : d;            
        }
        
        public void visitUnary(Quad d) {
            putLineNum(d);
            Operand src = Unary.getSrc(d);
            QuadToJasmin.putInst(getOperandLoadingInst(src));
            Unary unaryOperator = (Unary) d.getOperator();
            String strUnary = unaryOperator.toString();
            String jasminInstOperator = null;                        
            
            if (strUnary.startsWith("NEG")) {
                jasminInstOperator = strUnary.split("_")[1].toLowerCase() + "neg";
            } else if (strUnary.startsWith("INT_2")) {
                if (unaryOperator instanceof Unary.INT_2BYTE
                        || unaryOperator instanceof Unary.INT_2CHAR
                        || unaryOperator instanceof Unary.INT_2SHORT) {
                    jasminInstOperator = "int2" + strUnary.split("_2")[1].toLowerCase();
                } else {
                    jasminInstOperator = "i2" + strUnary.charAt((strUnary.indexOf('2')+1));
                    jasminInstOperator = jasminInstOperator.toLowerCase();                    
                }                
            } else if (strUnary.startsWith("FLOAT_2")) {
                assert !(unaryOperator instanceof Unary.FLOAT_2INTBITS) : d;
                jasminInstOperator = "f2" + strUnary.charAt((strUnary.indexOf('2')+1));
                jasminInstOperator = jasminInstOperator.toLowerCase();                    
            } else if (strUnary.startsWith("DOUBLE_2")) {
                assert !(unaryOperator instanceof Unary.DOUBLE_2LONGBITS) : d;
                jasminInstOperator = "d2" + strUnary.charAt((strUnary.indexOf('2')+1));
                jasminInstOperator = jasminInstOperator.toLowerCase();                    
            } else if (strUnary.startsWith("LONG_2")) {                 
                jasminInstOperator = "l2" + strUnary.charAt((strUnary.indexOf('2')+1));
                jasminInstOperator = jasminInstOperator.toLowerCase();
            } else assert false : d;
            
            QuadToJasmin.putInst("\t"+jasminInstOperator);
            
            RegisterOperand dst = Unary.getDest(d);
            QuadToJasmin.putInst(getStoreInstruction(dst));
        }
        
        public void visitALength(Quad d) {
            putLineNum(d);
            putLineNum(d);
            
            QuadToJasmin.putInst(getOperandLoadingInst(ALength.getSrc(d)));
            
            QuadToJasmin.putInst("\tarraylength");
            
            RegisterOperand dst = ALength.getDest(d);
            QuadToJasmin.putInst(getStoreInstruction(dst));
        }

        public void visitMemLoad(Quad d) {
            assert false : d;
        }
        
        public void visitMemStore(Quad d) {
            assert false : d;
        }        
        
        public void visitJsr(Quad d) {
            putLineNum(d);
            // jsr instruction pushes the address of the next immediate opcode into stack
            // as its return address. However it is not guaranteed that the next basic
            // block we handle is the one jsr expected. To get around this, we add an extra
            // goto with its target as jsr's successor
            TargetOperand target = Jsr.getTarget(d);
            TargetOperand successor = Jsr.getSuccessor(d);            
            assert current_BB.getLastQuad() == d;            
            QuadToJasmin.putInst("\tjsr " + target.getTarget());
            QuadToJasmin.putInst("\tgoto " + successor.getTarget());
        }
        
        public void visitRet(Quad d) {        
            putLineNum(d);
            RegisterOperand reg = Ret.getTarget(d);
            QuadToJasmin.putInst("\tret " + Register2VariableTableIdx.get(reg.getRegister()));
        }

        public void visitPhi(Quad d) {
            putLineNum(d);
            // If this Phi is associated with JSR operations,
            // save the return address on the top of the stack
            // to the destination register
            BasicBlockTableOperand t = Phi.getPreds(d);
            RegisterOperand dst = Phi.getDest(d);
            assert t.size() > 0 : current_BB.fullDump();
            Quad lastQuad = t.get(0).getLastQuad();
            if ( lastQuad != null && lastQuad.getOperator() == Operator.Jsr.JSR.INSTANCE ) {
                QuadToJasmin.putInst(getStoreInstruction(dst));
            }
            // Other branches are handled by processMethod()
        }
        
        public void visitTableSwitch(Quad d) {
            putLineNum(d);
            Operand src = TableSwitch.getSrc(d);
            QuadToJasmin.putInst(getOperandLoadingInst(src));
            BasicBlockTableOperand targetTable = TableSwitch.getTargetTable(d);
            int low = TableSwitch.getLow(d).getValue();
            int size = targetTable.size();
            QuadToJasmin.putInst("\ttableswitch " + low + " " + (low+size-1));
            for (int i=0; i < size; i++) {
                QuadToJasmin.putInst("\t\t"+targetTable.get(i));                
            }
            QuadToJasmin.putInst("\t\tdefault: "+TableSwitch.getDefault(d).getTarget());                        
        }

        public void visitLookupSwitch(Quad d) {
            putLineNum(d);
            Operand src = LookupSwitch.getSrc(d);
            QuadToJasmin.putInst(getOperandLoadingInst(src));
            QuadToJasmin.putInst("\tlookupswitch");
            IntValueTableOperand valTable = LookupSwitch.getValueTable(d);
            BasicBlockTableOperand targetTable = LookupSwitch.getTargetTable(d);
            int size = valTable.size();
            for (int i=0; i < size; i++) {
                QuadToJasmin.putInst("\t\t"+valTable.get(i)+" : "+targetTable.get(i));                
            }
            QuadToJasmin.putInst("\t\tdefault: "+LookupSwitch.getDefault(d).getTarget());
        }
    }
}
