package chord.program.stubs;

import joeq.Compiler.Quad.ICFGBuilder;
import joeq.Class.jq_NameAndDesc;
import joeq.Class.PrimordialClassLoader;
import joeq.Class.jq_Type;
import joeq.Class.jq_Method;
import joeq.Class.jq_Class;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.MethodOperand;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.RegisterFactory.Register;

/**
 * Stub for static method "Object doPrivileged(PrivilegedAction action)"
 * in class java.security.AccessController.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class DoPrivileged1CFGBuilder implements ICFGBuilder {
	@Override
	public ControlFlowGraph run(jq_Method m) {
		jq_Class c = (jq_Class) jq_Type.parseType("java.security.PrivilegedAction");
        jq_NameAndDesc ndOfRun = new jq_NameAndDesc("run", "()Ljava/lang/Object;");
        jq_Method run = c.getDeclaredInstanceMethod(ndOfRun);
        assert (run != null);
		RegisterFactory rf = new RegisterFactory(1, 1);
		jq_Type ot = PrimordialClassLoader.JavaLangObject;
		Register r0 = rf.getOrCreateLocal(0, ot);
		Register t0 = rf.getOrCreateStack(0, ot);
		RegisterOperand ro = new RegisterOperand(r0, ot);
		RegisterOperand to = new RegisterOperand(t0, ot);
		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
        MethodOperand mo = new MethodOperand(run);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 2, null);
        Quad q1 = Invoke.create(0, bb, Invoke.INVOKEINTERFACE_V.INSTANCE, null, mo, 1);
        Invoke.setParam(q1, 0, ro);
        Invoke.setDest(q1, to);
        Quad q2 = Return.create(1, bb, RETURN_A.INSTANCE);
        Return.setSrc(q2, to);
		bb.appendQuad(q1);
		bb.appendQuad(q2);
		BasicBlock entry = cfg.entry();
		BasicBlock exit = cfg.exit();
		bb.addPredecessor(entry);
		bb.addSuccessor(exit);
		entry.addSuccessor(bb);
		exit.addPredecessor(bb);
		return cfg;
	}
}
