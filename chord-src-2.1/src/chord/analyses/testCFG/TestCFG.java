package chord.analyses.testCFG;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.project.analyses.JavaAnalysis;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import chord.analyses.method.DomM;

import chord.project.ClassicProject;

/**
  * This analysis is to test the CFG APIs. It prints the following information:
  *  1. All the basic blocks;
  *  2. All the quads within each basic blocks;
  *  3. All the local variables;
  *  
  *  @author Ding Yuan
  */
@Chord(
   name = "cfg-test-java",
   consumes = { "M" } // This analysis 
)

public class TestCFG extends JavaAnalysis {
	public void run() {
		// 1. let's get the methods first.
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		int numM = domM.size();

		for (int mIdx = 0; mIdx < numM; mIdx++) {
			jq_Method m = domM.get(mIdx);

			if (m.isAbstract()) {
				System.out.println("Ding: This method is an abstract method... Continuing...");
				continue;
			}

			ControlFlowGraph cfg = m.getCFG();

			String file = m.getDeclaringClass().getSourceFileName(); // file

			System.out.println("Ding: TestCFG analysis: method: " + m.getName()
					     + "@" + file);

			for (BasicBlock bb : cfg.reversePostOrder()) {
				if (bb == null) {
					continue;
				}
				int bbsize = bb.size();
				System.out.println("Basic Block: " + bb + ", size: " + bbsize);
				if (bbsize != 0) {
					System.out.println("  Location: " + bb.getQuad(0).toLocStr()); 
				}
				System.out.println("  The predecessors of this bb are: " + bb.getPredecessors()); 
				System.out.println("  The successors of this bb are: " + bb.getSuccessors()); 
				// System.out.println("  "); 
				for (int qIdx = 0; qIdx < bbsize; qIdx++) {
					Quad q = (Quad) bb.getQuad(qIdx); // the call-site, in quad format
					System.out.println("    quad[" + qIdx + "]: " //+ q.toLocStr() 
							+ q.toVerboseStr()); 
					System.out.print("       -- Used registers: ");
					for (RegisterOperand use : q.getUsedRegisters()) {
						Register r = use.getRegister();
						if (r.isTemp()) {
						  System.out.print("T");
						}
						else {
							System.out.print("R");
						}
						System.out.print(r.getNumber()+"; ");
					}
					System.out.println();
					System.out.println("       -- Defined registers: " + q.getDefinedRegisters());
				}
				System.out.println(""); 
				System.out.println(""); 
			}
			// EntryOrExitBasicBlock entry = cfg.entry();
			// RegisterFactory rf = cfg.getRegisterFactory();
			// for (Object o : rf) {
			// Register v = (Register) o;
			//}
		}
	}
}
