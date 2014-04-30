package chord.analyses.alias;

import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramDom;
import chord.analyses.alloc.DomH;
import chord.analyses.invk.DomI;

/**
 * Domain of abstract contexts.
 * <p>
 * The 0th element in this domain denotes the distinguished abstract context <tt>epsilon</tt>
 * (see {@link chord.analyses.alias.Ctxt}).
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class DomC extends ProgramDom<Ctxt> {
    private DomH domH;
    private DomI domI;
    public Ctxt setCtxt(Quad[] elems) {
        Ctxt cVal = new Ctxt(elems);
        int cIdx = indexOf(cVal);
        if (cIdx != -1)
            return (Ctxt) get(cIdx);
        getOrAdd(cVal);
        return cVal;
    }
    public String toXMLAttrsString(Ctxt cVal) {
        if (domH == null)
            domH = (DomH) ClassicProject.g().getTrgt("H");
        if (domI == null)
            domI = (DomI) ClassicProject.g().getTrgt("I");
        Quad[] elems = cVal.getElems();
        int n = elems.length;
        if (n == 0)
            return "";
        String s = "ids=\"";
        for (int i = 0; i < n; i++) {
            Quad eVal = elems[i];
            Operator op = eVal.getOperator();
            if (op instanceof New || op instanceof NewArray || op instanceof MultiNewArray) {
                int hIdx = domH.indexOf(eVal);
                s += "H" + hIdx;
            } else if (op instanceof Invoke) {
                int iIdx = domI.indexOf(eVal);
                s += "I" + iIdx;
            } else
                assert false;
            if (i < n - 1)
                s += " ";
        }
        return s + "\" ";
    }
}
