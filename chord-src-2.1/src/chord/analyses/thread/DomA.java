package chord.analyses.thread;

import joeq.Class.jq_Method;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramDom;
import chord.analyses.method.DomM;

/**
 * Domain of abstract threads.
 * <p>
 * An abstract thread is a triple <tt>(o,c,m)</tt> denoting the thread
 * whose abstract object is 'o' and which starts at method 'm' in
 * abstract context 'c'.
 *
 * @see chord.analyses.thread.ThreadsAnalysis
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class DomA extends ProgramDom<jq_Method> {
    private DomM domM;
    public String toXMLAttrsString(jq_Method m) {
        if (m == null) return "";
        if (domM == null) domM = (DomM) ClassicProject.g().getTrgt("M");
        int mIdx = domM.indexOf(m);
        return "Mid=\"M" + mIdx + "\"";
    }
}
