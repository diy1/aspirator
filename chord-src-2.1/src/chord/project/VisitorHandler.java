package chord.project;

import java.util.ArrayList;
import java.util.Collection;

import chord.program.Program;
import chord.program.visitors.IAcqLockInstVisitor;
import chord.program.visitors.IClassVisitor;
import chord.program.visitors.IFieldVisitor;
import chord.program.visitors.IHeapInstVisitor;
import chord.program.visitors.IInstVisitor;
import chord.program.visitors.IInvokeInstVisitor;
import chord.program.visitors.IMethodVisitor;
import chord.program.visitors.IMoveInstVisitor;
import chord.program.visitors.INewInstVisitor;
import chord.program.visitors.IPhiInstVisitor;
import chord.program.visitors.IRelLockInstVisitor;
import chord.program.visitors.IReturnInstVisitor;
import chord.program.visitors.ICastInstVisitor;
import chord.project.ITask;
import chord.util.IndexSet;

import joeq.Class.jq_Reference;
import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.THROW_A;
import joeq.Compiler.Quad.Operator.Monitor.MONITORENTER;
import joeq.Compiler.Quad.Operator.Monitor.MONITOREXIT;

/**
 * Utility for registering and executing a set of tasks
 * as visitors over program representation.
 *
 * @author Mayur Naik (mayur.naik@intel.com)
 */
public class VisitorHandler {
    private final Collection<ITask> tasks;
    private Collection<IClassVisitor> cvs;
    private Collection<IFieldVisitor> fvs;
    private Collection<IMethodVisitor> mvs;
    private Collection<IHeapInstVisitor> hivs;
    private Collection<INewInstVisitor> nivs;
    private Collection<IInvokeInstVisitor> iivs;
    private Collection<IReturnInstVisitor> rivs;
    private Collection<IAcqLockInstVisitor> acqivs;
    private Collection<IRelLockInstVisitor> relivs;
    private Collection<IMoveInstVisitor> mivs;
    private Collection<ICastInstVisitor> civs;
    private Collection<IPhiInstVisitor> pivs;
    private Collection<IInstVisitor> ivs;
    private boolean doCFGs;
    public VisitorHandler(ITask task) {
        tasks = new ArrayList<ITask>(1);
        tasks.add(task);
    }
    public VisitorHandler(Collection<ITask> tasks) {
        this.tasks = tasks;
    }
    private void visitFields(jq_Class c) {
        for (Object o : c.getSortedMembers()) {
            if (o instanceof jq_Field) {
                jq_Field f = (jq_Field) o;
                for (IFieldVisitor fv : fvs)
                    fv.visit(f);
            }
        }
    }
    private void visitMethods(jq_Class c) {
        for (Object o : c.getSortedMembers()) {
            if (o instanceof jq_Method) {
                jq_Method m = (jq_Method) o;
                if (!reachableMethods.contains(m))
                    continue;
                for (IMethodVisitor mv : mvs) {
                    mv.visit(m);
                    if (!doCFGs)
                        continue;
                    if (m.isAbstract())
                        continue;
                    ControlFlowGraph cfg = m.getCFG();
                    visitInsts(cfg);
                }
            }
        }
    }
    private void visitInsts(ControlFlowGraph cfg) {
        for (BasicBlock bb : cfg.reversePostOrder()) {
            for (Quad q : bb.getQuads()) {
                if (ivs != null) {
                    for (IInstVisitor iv : ivs)
                        iv.visit(q);
                }
                Operator op = q.getOperator();
                if (op instanceof Invoke) {
                    if (iivs != null) {
                        for (IInvokeInstVisitor iiv : iivs)
                            iiv.visitInvokeInst(q);
                    }
                } else if (op instanceof ALoad || op instanceof Getfield ||
                        op instanceof Putfield || op instanceof AStore ||
                        op instanceof Getstatic || op instanceof Putstatic) {
                    if (hivs != null) {
                        for (IHeapInstVisitor hiv : hivs)
                            hiv.visitHeapInst(q);
                    }
                } else if (op instanceof New || op instanceof NewArray) {
                    if (nivs != null) {
                        for (INewInstVisitor niv : nivs)
                            niv.visitNewInst(q);
                    }
                } else if (op instanceof Move) {
                    if (mivs != null) {
                        for (IMoveInstVisitor miv : mivs)
                            miv.visitMoveInst(q);
                    }
                } else if (op instanceof CheckCast) {
                    if (civs != null) {
                        for (ICastInstVisitor civ : civs)
                            civ.visitCastInst(q);
                    }
                } else if (op instanceof Phi) {
                    if (pivs != null) {
                        for (IPhiInstVisitor piv : pivs)
                            piv.visitPhiInst(q);
                    }
                } else if (op instanceof Return && !(op instanceof THROW_A)) {
                    if (rivs != null) {
                        for (IReturnInstVisitor riv : rivs)
                            riv.visitReturnInst(q);
                    }
                } else if (op instanceof MONITORENTER) {
                    if (acqivs != null) {
                        for (IAcqLockInstVisitor acqiv : acqivs)
                            acqiv.visitAcqLockInst(q);
                    }
                } else if (op instanceof MONITOREXIT) {
                    if (relivs != null) {
                        for (IRelLockInstVisitor reliv : relivs)
                            reliv.visitRelLockInst(q);
                    }
                }
            }
        }
    }
    private IndexSet<jq_Method> reachableMethods;

    public void visitProgram() {
        for (ITask task : tasks) {
            if (task instanceof IClassVisitor) {
                if (cvs == null)
                    cvs = new ArrayList<IClassVisitor>();
                cvs.add((IClassVisitor) task);
            }
            if (task instanceof IFieldVisitor) {
                if (fvs == null)
                    fvs = new ArrayList<IFieldVisitor>();
                fvs.add((IFieldVisitor) task);
            }
            if (task instanceof IMethodVisitor) {
                if (mvs == null)
                    mvs = new ArrayList<IMethodVisitor>();
                mvs.add((IMethodVisitor) task);
            }
            if (task instanceof IInstVisitor) {
                if (ivs == null)
                    ivs = new ArrayList<IInstVisitor>();
                ivs.add((IInstVisitor) task);
            }
            if (task instanceof IHeapInstVisitor) {
                if (hivs == null)
                    hivs = new ArrayList<IHeapInstVisitor>();
                hivs.add((IHeapInstVisitor) task);
            }
            if (task instanceof IInvokeInstVisitor) {
                if (iivs == null)
                    iivs = new ArrayList<IInvokeInstVisitor>();
                iivs.add((IInvokeInstVisitor) task);
            }
            if (task instanceof INewInstVisitor) {
                if (nivs == null)
                    nivs = new ArrayList<INewInstVisitor>();
                nivs.add((INewInstVisitor) task);
            }
            if (task instanceof IMoveInstVisitor) {
                if (mivs == null)
                    mivs = new ArrayList<IMoveInstVisitor>();
                mivs.add((IMoveInstVisitor) task);
            }
            if (task instanceof ICastInstVisitor) {
                if (civs == null)
                    civs = new ArrayList<ICastInstVisitor>();
                civs.add((ICastInstVisitor) task);
            }
            if (task instanceof IPhiInstVisitor) {
                if (pivs == null)
                    pivs = new ArrayList<IPhiInstVisitor>();
                pivs.add((IPhiInstVisitor) task);
            }
            if (task instanceof IReturnInstVisitor) {
                if (rivs == null)
                    rivs = new ArrayList<IReturnInstVisitor>();
                rivs.add((IReturnInstVisitor) task);
            }
            if (task instanceof IAcqLockInstVisitor) {
                if (acqivs == null)
                    acqivs = new ArrayList<IAcqLockInstVisitor>();
                acqivs.add((IAcqLockInstVisitor) task);
            }
            if (task instanceof IRelLockInstVisitor) {
                if (relivs == null)
                    relivs = new ArrayList<IRelLockInstVisitor>();
                relivs.add((IRelLockInstVisitor) task);
            }
        }
        Program program = Program.g();
        reachableMethods = program.getMethods();
        doCFGs = (ivs != null) || (hivs != null) ||
            (iivs != null) || (nivs != null) || (mivs != null) ||
            (civs != null) || (pivs != null) || (rivs != null) ||
            (acqivs != null) || (relivs != null);
        if (cvs != null) {
            IndexSet<jq_Reference> classes = program.getClasses();
            for (jq_Reference r : classes) {
                if (r instanceof jq_Array)
                    continue;
                jq_Class c = (jq_Class) r;
                for (IClassVisitor cv : cvs)
                    cv.visit(c);
                if (fvs != null)
                    visitFields(c);
                if (mvs != null)
                    visitMethods(c);
            }
        }
        
    }
}
