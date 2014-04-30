package chord.analyses.alias;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Field;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.bddbddb.Rel.RelView;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

import chord.util.SetUtils;

/**
 * Context-insensitive points-to analysis.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "cipa-java",
    consumes = { "VH", "FH", "HFH" }
)
public class CIPAAnalysis extends JavaAnalysis {
    private ProgramRel relVH;
    private ProgramRel relFH;
    private ProgramRel relHFH;
    public void run() {
        relVH  = (ProgramRel) ClassicProject.g().getTrgt("VH");
        relFH  = (ProgramRel) ClassicProject.g().getTrgt("FH");
        relHFH = (ProgramRel) ClassicProject.g().getTrgt("HFH");
    }
    /**
     * Provides the abstract object to which a given local variable may point.
     * 
     * @param var A local variable.
     * 
     * @return The abstract object to which the given local variable may point.
     */
    public CIObj pointsTo(Register var) {
        if (!relVH.isOpen())
            relVH.load();
        RelView view = relVH.getView();
        view.selectAndDelete(0, var);
        Iterable<Quad> res = view.getAry1ValTuples();
        Set<Quad> pts = SetUtils.newSet(view.size());
        for (Quad inst : res)
            pts.add(inst);
        view.free();
        return new CIObj(pts);
    }
    /**
     * Provides the abstract object to which a given static field may point.
     * 
     * @param field A static field.
     * 
     * @return The abstract object to which the given static field may point.
     */
    public CIObj pointsTo(jq_Field field) {
        if (!relFH.isOpen())
            relFH.load();
        RelView view = relFH.getView();
        view.selectAndDelete(0, field);
        Iterable<Quad> res = view.getAry1ValTuples();
        Set<Quad> pts = SetUtils.newSet(view.size());
        for (Quad inst : res)
            pts.add(inst);
        view.free();
        return new CIObj(pts);
    }
    /**
     * Provides the abstract object to which a given instance field of a given abstract object may point.
     * 
     * @param obj   An abstract object.
     * @param field An instance field.
     * 
     * @return The abstract object to which the given instance field of the given abstract object may point.
     */
    public CIObj pointsTo(CIObj obj, jq_Field field) {
        if (!relHFH.isOpen())
            relHFH.load();
        Set<Quad> pts = new HashSet<Quad>();
        for (Quad site : obj.pts) {
            RelView view = relHFH.getView();
            view.selectAndDelete(0, site);
            view.selectAndDelete(1, field);
            Iterable<Quad> res = view.getAry1ValTuples();
            for (Quad inst : res)
                pts.add(inst);
            view.free();
        }
        return new CIObj(pts);
    }
    /**
     * Frees relations used by this program analysis if they are in memory.
     * <p>
     * This method must be called after clients are done exercising the interface of this analysis.
     */
    public void free() {
        if (relVH.isOpen())
            relVH.close();
        if (relFH.isOpen())
            relFH.close();
        if (relHFH.isOpen())
            relHFH.close();
    }
}
