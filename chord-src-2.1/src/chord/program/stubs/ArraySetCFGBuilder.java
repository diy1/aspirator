package chord.program.stubs;

import joeq.Compiler.Quad.ICFGBuilder;
import joeq.Class.PrimordialClassLoader;
import joeq.Class.jq_Type;
import joeq.Class.jq_Array;
import joeq.Class.jq_Primitive;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.TypeOperand;
import joeq.Compiler.Quad.Operand.IConstOperand;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.AStore.ASTORE_A;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_V;
import joeq.Compiler.Quad.RegisterFactory.Register;

/**
 * Stub for static method "void set(Object array, int index, Object value)"
 * in class java.lang.reflect.Array.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class ArraySetCFGBuilder implements ICFGBuilder {
	@Override
	public ControlFlowGraph run(jq_Method m) {
		RegisterFactory rf = new RegisterFactory(3, 1);
		jq_Type ot = PrimordialClassLoader.JavaLangObject;
		jq_Type at = jq_Array.OBJECT_ARRAY;
		Register r0 = rf.getOrCreateLocal(0, ot);
		Register r1 = rf.getOrCreateLocal(1, jq_Primitive.INT);
		Register r2 = rf.getOrCreateLocal(2, ot);
		Register t0 = rf.getOrCreateStack(0, at);
		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
		RegisterOperand ro0 = new RegisterOperand(r0, ot);
		RegisterOperand ro2 = new RegisterOperand(r2, ot);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 3, null);
		Quad q1 = CheckCast.create(0, bb, CheckCast.CHECKCAST.INSTANCE,
			new RegisterOperand(t0, at), ro0, new TypeOperand(at));
		Quad q2 = AStore.create(1, bb, ASTORE_A.INSTANCE, ro2,
			new RegisterOperand(t0, at), new IConstOperand(0), null);
		Quad q3 = Return.create(2, bb, RETURN_V.INSTANCE);
		bb.appendQuad(q1);
		bb.appendQuad(q2);
		bb.appendQuad(q3);
		BasicBlock entry = cfg.entry();
		BasicBlock exit = cfg.exit();
		bb.addPredecessor(entry);
		bb.addSuccessor(exit);
		entry.addSuccessor(bb);
		exit.addPredecessor(bb);
		return cfg;
	}
}
