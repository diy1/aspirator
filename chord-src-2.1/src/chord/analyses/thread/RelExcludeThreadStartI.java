package chord.analyses.thread;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.util.Utils;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;

@Chord(name="excludeThreadStartI", sign="I0:I0", consumes = { "threadStartI" })
public class RelExcludeThreadStartI extends ProgramRel {
    private static final String[] threadExcludeAry;
    static {
        String threadExcludeStr = System.getProperty("chord.thread.exclude", "sun.,java.");
        threadExcludeAry = Utils.toArray(threadExcludeStr);
    }
    @Override
    public void fill() {
        ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt("threadStartI");
        rel.load();
        Iterable<Quad> tuples = rel.getAry1ValTuples();
        for (Quad q : tuples) {
            String c = q.getMethod().getDeclaringClass().getName();
            for (String c2 : threadExcludeAry) {
                if (c.startsWith(c2)) {
                    add(q);
                    break;
                }
            }
        }
        rel.close();
    }
}
