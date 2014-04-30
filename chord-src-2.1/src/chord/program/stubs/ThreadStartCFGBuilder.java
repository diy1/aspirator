package chord.program.stubs;

import joeq.Compiler.Quad.ICFGBuilder;
import joeq.Class.jq_NameAndDesc;
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
import joeq.Compiler.Quad.Operator.Return.RETURN_V;
import joeq.Compiler.Quad.RegisterFactory.Register;

/**
 * Stub for instance method "void start()" in class java.lang.Thread.

 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class ThreadStartCFGBuilder implements ICFGBuilder {
	@Override
	public ControlFlowGraph run(jq_Method m) {
		jq_Class c = m.getDeclaringClass();
		jq_NameAndDesc ndOfRun = new jq_NameAndDesc("run", "()V");
		jq_Method run = c.getDeclaredInstanceMethod(ndOfRun);
		assert (run != null);
		RegisterFactory rf = new RegisterFactory(0, 1);
		Register r = rf.getOrCreateLocal(0, c);
		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
		RegisterOperand ro = new RegisterOperand(r, c);
		MethodOperand mo = new MethodOperand(run);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 2, null);
		Quad q1 = Invoke.create(0, bb, Invoke.INVOKEVIRTUAL_V.INSTANCE, null, mo, 1);
		Invoke.setParam(q1, 0, ro);
		Quad q2 = Return.create(1, bb, RETURN_V.INSTANCE);
		bb.appendQuad(q1);
		bb.appendQuad(q2);
		BasicBlock entry = cfg.entry();
		BasicBlock exit = cfg.exit();
		bb.addPredecessor(entry);
		bb.addSuccessor(exit);
		entry.addSuccessor(bb);
		exit.addPredecessor(bb);
		m.unsynchronize();
		return cfg;
	}
}
