package chord.program.reflect;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Class.jq_Reference;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.util.ArraySet;
import chord.util.tuple.object.Pair;

/**
 * Static analysis for resolving reflection.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class StaticReflectResolver {
	protected static final boolean DEBUG = false;
	protected ControlFlowGraph cfg;
	protected int numArgs;
	// sets of all forname/newinst call sites
	// initialized in first iteration
	protected final Set<Quad> forNameSites = new HashSet<Quad>();
	protected final Set<Quad> newInstSites = new HashSet<Quad>();
	// local vars not tracked because they were assigned something
	// that is unanalyzable (e.g., a formal argument, return result of
	// a function call, a static/instance field, etc.)
	protected final Set<Register> abortedVars = new HashSet<Register>();
	protected final Set<Register> trackedVars = new HashSet<Register>();
	protected final Set<Pair<Register, jq_Reference>> resolutions =
		new HashSet<Pair<Register, jq_Reference>>();
	protected final Set<Pair<Quad, jq_Reference>> resolvedClsForNameSites =
		new ArraySet<Pair<Quad, jq_Reference>>();
	protected final Set<Pair<Quad, jq_Reference>> resolvedObjNewInstSites =
		new LinkedHashSet<Pair<Quad, jq_Reference>>();
	protected boolean changed;

	public Set<Pair<Quad, jq_Reference>> getResolvedClsForNameSites() {
		return resolvedClsForNameSites;
	}
	public Set<Pair<Quad, jq_Reference>> getResolvedObjNewInstSites() {
		return resolvedObjNewInstSites;
	}
	public void run(jq_Method m) {
		resolvedClsForNameSites.clear();
		resolvedObjNewInstSites.clear();
		cfg = m.getCFG();
		initForNameAndNewInstSites();
		if (forNameSites.isEmpty())
			return;
		numArgs = m.getParamTypes().length;
		resolveForNameSites();
		if (newInstSites.isEmpty())
			return;
		resolveNewInstSites();
	}
	protected void initForNameAndNewInstSites() {
		forNameSites.clear();
		newInstSites.clear();
		for (BasicBlock bb : cfg.reversePostOrder()) {
			for (Quad q : bb.getQuads()) {
				Operator op = q.getOperator();
				if (op instanceof Invoke) {
					jq_Method n = Invoke.getMethod(q).getMethod();
					String cName = n.getDeclaringClass().getName();
					if (cName.equals("java.lang.Class")) {
						String mName = n.getName().toString();
						if (mName.equals("forName"))
							forNameSites.add(q);
						else if (mName.equals("newInstance"))
							newInstSites.add(q);
					} else if(cName.equals("java.lang.reflect.Constructor")) {
						String mName = n.getName().toString();
						if (mName.equals("newInstance"))
							newInstSites.add(q);
					} else if(cName.equals("java.lang.ClassLoader")) {
						String mName = n.getName().toString();
						if (mName.equals("loadClass"))
							forNameSites.add(q);
					}
				}
			}
		}
		if (DEBUG) {
			if (!forNameSites.isEmpty()) {
				System.out.println("*** FORNAME SITES in method: " + cfg.getMethod());
				for (Quad q : forNameSites)
					System.out.println("\t" + q);
			}
			if (!newInstSites.isEmpty()) {
				System.out.println("*** NEWINST SITES in method: " + cfg.getMethod());
				for (Quad q : newInstSites)
					System.out.println("\t" + q);
			}
		}
	}
	protected void resolveForNameSites() {
		abortedVars.clear();
		initAbortedVars(false);
		trackedVars.clear();
		for (Quad q : forNameSites) {
			Register r = Invoke.getParamList(q).get(0).getRegister();
			trackedVars.add(r);
		}
		resolutions.clear();
		changed = true;
		while (changed) {
			changed = false;
			for (BasicBlock bb : cfg.reversePostOrder()) {
				for (Quad q : bb.getQuads()) {
					Operator op = q.getOperator();
					if (op instanceof Move || op instanceof CheckCast) {
						Register l = Move.getDest(q).getRegister();
						Operand ro = Move.getSrc(q);
						if (ro instanceof RegisterOperand) {
							Register r = ((RegisterOperand) ro).getRegister();
							processCopy(l, r);
						} else if (ro instanceof AConstOperand &&
								trackedVars.contains(l)) {
							Object v = ((AConstOperand) ro).getValue();
							if (v instanceof String) {
								jq_Reference t = (jq_Reference) jq_Type.parseType((String) v);
								if (t != null) {
									Pair<Register, jq_Reference> p =
										new Pair<Register, jq_Reference>(l, t);
										if (resolutions.add(p))
											changed = true;
								}
							}
						}
					} else if (op instanceof Phi)
						processPhi(q);
				}
			}
		}
		for (Quad q : forNameSites) {
			Register v = Invoke.getParamList(q).get(0).getRegister();
			if (!abortedVars.contains(v)) {
				for (Pair<Register, jq_Reference> p : resolutions) {
					if (p.val0 == v) {
						Pair<Quad, jq_Reference> p2 = new Pair<Quad, jq_Reference>(q, p.val1);
						resolvedClsForNameSites.add(p2);
					}
				}
			}
		}
		if (DEBUG) {
			if (!resolvedClsForNameSites.isEmpty()) {
				System.out.println("*** FORNAME RESOLUTIONS in method: " + cfg.getMethod());
				for (Pair<Quad, jq_Reference> p : resolvedClsForNameSites)
					System.out.println("\t" + p);
			}
		}
	}
	protected void resolveNewInstSites() {
		if(DEBUG)
			System.out.println("resolveNewInstSites on " + cfg.getMethod());
		resolutions.clear();
		for (Pair<Quad, jq_Reference> p : resolvedClsForNameSites) {
			Quad q = p.val0;
			RegisterOperand lo = Invoke.getDest(q);
			if (lo != null) {
				Register l = lo.getRegister();
				Pair<Register, jq_Reference> p2 = new Pair<Register, jq_Reference>(l, p.val1);
				resolutions.add(p2);
			}
		}
		abortedVars.clear();
		initAbortedVars(true);
		trackedVars.clear();
		for (Quad q : newInstSites) {
			Register r = Invoke.getParamList(q).get(0).getRegister();
			trackedVars.add(r);
		}
		changed = true;
		while (changed) {
			changed = false;
			for (BasicBlock bb : cfg.reversePostOrder()) {
				for (Quad q : bb.getQuads()) {
					Operator op = q.getOperator();
					if (op instanceof Move || op instanceof CheckCast) {
						Operand ro = Move.getSrc(q);
						if (ro instanceof RegisterOperand) {
							Register l = Move.getDest(q).getRegister();
							Register r = ((RegisterOperand) ro).getRegister();
							processCopy(l, r);

							if(op instanceof CheckCast)
								processCheckCast(l, r, q);
						}
					} else if (op instanceof Phi)
						processPhi(q);
					else if(op instanceof Invoke) {
						processInvoke(q);
					} else if(op instanceof Return) {
						processReturn(q);
					}
				}
			}
		}
		for (Quad q : newInstSites) {
			Register v = Invoke.getParamList(q).get(0).getRegister();
			if (!abortedVars.contains(v)) {
				for (Pair<Register, jq_Reference> p : resolutions) {
					if (p.val0 == v) {
						Pair<Quad, jq_Reference> p2 = new Pair<Quad, jq_Reference>(q, p.val1);
						resolvedObjNewInstSites.add(p2);
					}
				}
			}
		}
		if (DEBUG) {
			if (!resolvedObjNewInstSites.isEmpty()) {
				System.out.println("*** NEWINST RESOLUTIONS in method: " + cfg.getMethod());
				for (Pair<Quad, jq_Reference> p : resolvedObjNewInstSites)
					System.out.println("\t" + p);
			}
		}
	}

	protected void processInvoke(Quad q) {
	}

	protected void processReturn(Quad q) {
	}

	protected void processCheckCast(Register l, Register r, Quad q) {
		//for benefit of subclasses
	}

	protected void initAbortedVars(boolean isNewInst) {
		RegisterFactory rf = cfg.getRegisterFactory();
		for (int i = 0; i < numArgs; i++)
			abortedVars.add(rf.get(i));
		for (BasicBlock bb : cfg.reversePostOrder()) {
			for (Quad q : bb.getQuads()) {
				Operator op = q.getOperator();
				if (op instanceof Move || op instanceof CheckCast || op instanceof Phi)
					continue;
				if (isNewInst && op instanceof Invoke && forNameSites.contains(q)) {
					boolean isResolved = false;
					for (Pair<Quad, jq_Reference> p : resolvedClsForNameSites) {
						if (p.val0 == q) {
							isResolved = true;
							break;
						}
					}
					if (isResolved)
						continue;
				}
				for (RegisterOperand ro : q.getDefinedRegisters()) {
					Register r = ro.getRegister();
					abortedVars.add(r);
				}
			}
		}
	}
	protected void processCopy(Register l, Register r) {
		if (abortedVars.contains(r)) {
			if (abortedVars.add(l))
				changed = true;
		} else if (trackedVars.contains(l)) {
			if (trackedVars.add(r))
				changed = true;
			else {
				Set<jq_Reference> tl = new ArraySet<jq_Reference>();
				for (Pair<Register, jq_Reference> p : resolutions) {
					if (p.val0 == r)
						tl.add(p.val1);
				}
				for (jq_Reference t : tl) {
					Pair<Register, jq_Reference> p =
						new Pair<Register, jq_Reference>(l, t);
						if (resolutions.add(p))
							changed = true;
				}
			}
		}
	}

	protected void processPhi(Quad q) {
		Register l = Phi.getDest(q).getRegister();
		ParamListOperand roList = Phi.getSrcs(q);
		int n = roList.length();
		for (int i = 0; i < n; i++) {
			RegisterOperand ro = roList.get(i);
			if (ro != null) {
				Register r = ro.getRegister();
				processCopy(l, r);
			}
		}
	}
	/**
	 * Used to cue RTA to do a new iteration
	 */
	public boolean needNewIter() {
		return false;
	}

	public void startedNewIter() {}
}
