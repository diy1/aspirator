package chord.project.analyses;

import java.util.List;
import java.io.File;

import chord.project.ClassicProject;
import chord.bddbddb.Rel;
import chord.bddbddb.RelSign;
import chord.program.visitors.IClassVisitor;
import chord.project.Config;
import chord.project.ICtrlCollection;
import chord.project.IDataCollection;
import chord.project.IStepCollection;
import chord.project.ModernProject;
import chord.project.VisitorHandler;
import chord.util.Utils;
import chord.project.Messages;
import chord.project.ITask;

import CnCHJ.api.ItemCollection;

/**
 * Generic implementation of a program relation (a specialized kind of Java task).
 * <p>
 * A program relation is a relation over one or more program domains.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class ProgramRel extends Rel implements ITask {
    private static final String SKIP_TUPLE =
        "WARN: Skipping a tuple from relation '%s' as element '%s' was not found in domain '%s'.";
    protected Object[] consumes;
    @Override
    public void run() {
        zero();
        init();
        fill();
        save();
    }
    @Override
    public void run(Object ctrl, IStepCollection sc) {
        ModernProject p = ModernProject.g();
        Object[] allConsumes = p.runPrologue(ctrl, sc);
        RelSign sign = p.getSign(name);
        assert (sign != null);
        int numUniqDoms = sign.getDomKinds().length;
        ProgramDom[] uniqDoms = new ProgramDom[numUniqDoms];
        for (int i = 0; i < numUniqDoms; i++) {
            uniqDoms[i] = (ProgramDom) allConsumes[i];
        }
        int n = allConsumes.length - numUniqDoms;
        consumes = new Object[n];
        for (int i = 0; i < n; i++) {
            consumes[i] = allConsumes[numUniqDoms + i];
        }
        String[] domNames = sign.getDomNames();
        int m = domNames.length;
        ProgramDom[] doms = new ProgramDom[m];
        for (int i = 0; i < m; i++) {
            String domName = Utils.trimNumSuffix(domNames[i]);
            for (ProgramDom dom : uniqDoms) {
                if (dom.getName().equals(domName)) {
                    doms[i] = dom;
                    break;
                }
            }
            assert (doms[i] != null);
        }
        setSign(sign);
        setDoms(doms);
        run();
        p.runEpilogue(ctrl, sc, new Object[] { this }, null);
    }
    public void init() { }
    public void save() {
        if (Config.verbose >= 1)
            System.out.println("SAVING rel " + name + " size: " + size());
        super.save(Config.bddbddbWorkDirName);
        if (Config.classic)
            ClassicProject.g().setTrgtDone(this);
    }
    public void load() {
        super.load(Config.bddbddbWorkDirName);
    }
    public void fill() {
        if (this instanceof IClassVisitor) {
            VisitorHandler vh = new VisitorHandler(this);
            vh.visitProgram();
        } else {
            throw new RuntimeException("Relation '" + name +
                "' must override method fill().");
        }
    }
    public void print() {
        super.print(Config.outDirName);
    }
    public String toString() {
        return name;
    }
    public void skip(Object elem, ProgramDom dom) {
        Messages.log(SKIP_TUPLE, getClass().getName(), elem, dom.getClass().getName());
    }
}
