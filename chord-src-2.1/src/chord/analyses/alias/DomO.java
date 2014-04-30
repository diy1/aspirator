package chord.analyses.alias;

import java.util.Set;

import chord.project.ClassicProject;
import chord.project.analyses.ProgramDom;
import chord.analyses.alloc.DomH;
import joeq.Compiler.Quad.Quad;

/**
 * Domain of abstract objects.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class DomO extends ProgramDom<CIObj> {
    private DomH domH;
    public String toXMLAttrsString(CIObj oVal) {
        if (domH == null)
            domH = (DomH) ClassicProject.g().getTrgt("H");
        Set<Quad> pts = oVal.pts;
        if (pts.size() == 0)
            return "";
        String s = "Hids=\"";
        for (Quad hVal : pts) {
            int hIdx = domH.indexOf(hVal);
            s += "H" + hIdx + " ";
        }
        s = s.substring(0, s.length() - 1);
        return s + "\"";
    }
}
