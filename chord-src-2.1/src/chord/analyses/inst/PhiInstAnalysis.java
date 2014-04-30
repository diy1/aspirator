package chord.analyses.inst;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.analyses.point.DomP;
import chord.analyses.var.DomV;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

/**
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(name = "phi-java",
       consumes = { "P", "V", "Z" },
       produces = { "objPhiSrc!sign=P0,Z0,V0:P0_V0_Z0", "objPhiDst!sign=P0,V0:P0_V0", "objPhiMax!sign=P0,Z0:P0_Z0" }
)
public class PhiInstAnalysis extends JavaAnalysis {
    public void run() {
        DomP domP = (DomP) ClassicProject.g().getTrgt("P");
        DomV domV = (DomV) ClassicProject.g().getTrgt("V");
        ProgramRel relPhiSrc = (ProgramRel) ClassicProject.g().getTrgt("objPhiSrc");
        ProgramRel relPhiDst = (ProgramRel) ClassicProject.g().getTrgt("objPhiDst");
        ProgramRel relPhiMax = (ProgramRel) ClassicProject.g().getTrgt("objPhiMax");
        relPhiSrc.zero();
        relPhiDst.zero();
        relPhiMax.zero();
        for (jq_Method m : Program.g().getMethods()) {
            if (m.isAbstract())
                continue;
            ControlFlowGraph cfg = m.getCFG();
            for (BasicBlock bb : cfg.reversePostOrder()) {
                for (Quad q : bb.getQuads()) {
                    Operator op = q.getOperator();
                    if (!(op instanceof Phi))
                        continue;
                    RegisterOperand lo = Phi.getDest(q);
                    jq_Type t = lo.getType();
                    if (t == null || t.isReferenceType()) {
                        int pId = domP.indexOf(q);
                        Register l = lo.getRegister();
                        ParamListOperand po = Phi.getSrcs(q);
                        int n = po.length();
                        relPhiMax.add(pId, n - 1);
                        int lId = domV.indexOf(l);
                        relPhiDst.add(pId, lId);
                        for (int zId = 0; zId < n; zId++) {
                            RegisterOperand ro = po.get(zId);
                            if (ro != null) {
                                Register r = ro.getRegister();
                                int rId = domV.indexOf(r);
                                relPhiSrc.add(pId, zId, rId);
                            }
                        }
                    }
                }
            }
        }
        relPhiSrc.save();
        relPhiDst.save();
        relPhiMax.save();
    }
}
