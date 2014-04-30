package chord.program.reflect;

import java.util.*;

import chord.program.ClassHierarchy;
import chord.program.Program;
import chord.analyses.method.RelScopeExcludedM;
import chord.util.ArraySet;
import chord.util.IndexSet;
import chord.util.tuple.object.Pair;
import joeq.Class.*;
import joeq.Compiler.Quad.*;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.*;
import joeq.Compiler.Quad.Operator.Invoke.*;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class CastBasedStaticReflect extends StaticReflectResolver {

	ClassHierarchy ch;
	IndexSet<jq_Reference> reachableAllocClasses;

	// Set of methods containing a "return v" statement where v is in
	// set newInstVars.
	private Map<jq_Method, Set<Quad>> reflectRetMeths;
	private Map<Register, Set<Quad>> newInstReflSites;
	private Map<jq_Method, Set<jq_Method>> callers;
	Set<jq_Method> staticReflectResolved; 


	public CastBasedStaticReflect(IndexSet<jq_Reference> reachableAllocClasses, Set<jq_Method> staticReflectResolved) {
		System.out.println("CastBasedStaticReflect in use");

		ch =  Program.g().getClassHierarchy();
		newInstReflSites = new HashMap<Register, Set<Quad>>();
		reflectRetMeths = new HashMap<jq_Method, Set<Quad>>();
		callers = new HashMap<jq_Method, Set<jq_Method>>();
		this.reachableAllocClasses = reachableAllocClasses;
		this.staticReflectResolved = staticReflectResolved;
	}
	
	@Override
	public void run(jq_Method m) {
		resolvedClsForNameSites.clear();
		resolvedObjNewInstSites.clear();
		cfg = m.getCFG();
		initForNameAndNewInstSites();
		numArgs = m.getParamTypes().length;

		if (!forNameSites.isEmpty()) {
			resolveForNameSites();
		}
		resolveNewInstSites();
	}


	/*
	 * If a reflective value is cast to something, we mark all the possible alloc sites
	 * as possibly returning a value of the appropriate types
	 */
	@Override
	protected void processCheckCast(Register l, Register r, Quad q) {
		jq_Reference destType = (jq_Reference) CheckCast.getType(q).getType();

		if (newInstReflSites.containsKey(r) && ! RelScopeExcludedM.isOutOfScope(destType.getName())) {  
			if(DEBUG)
				System.out.println("processCast found a newInstVar cast to " + destType + " in " + q.getMethod().getDeclaringClass() + "." + q.getMethod());
			Set<String> subs = ch.getConcreteSubclasses(destType.getName());
			if (subs != null) {
				for (String s : subs) {
					jq_Class concreteClass = (jq_Class) jq_Type.parseType(s);

					if(!RelScopeExcludedM.isOutOfScope(s)) {
						Set<Quad> allocSites = newInstReflSites.get(r);
						if(allocSites != null)
							for(Quad allocSite: allocSites) //where was r allocated?
								resolvedObjNewInstSites.add(new Pair<Quad, jq_Reference>(allocSite, concreteClass));
						else
							System.out.println("WARN: found a reflective site but no actual alloc sites for it. Probably a bug in CastBasedStaticReflectResolver");//violates an internal invariant; probably a bug!
					}
				}
			}
		}	
		//the below lines are just an example of how you can modify the code to log more in the
		//area where you suspect something has gone wrong
//		else if(q.getMethod().getDeclaringClass().toString().startsWith("org.apache.hadoop.fs.FileSystem"))
//			System.err.println("Ignoring cast to " + destType + "  in " + q.getMethod().getDeclaringClass() + "." + q.getMethod() + " -- arg isn't reflective");
	}

	protected void processCopy(Register l, Register r) {
		super.processCopy(l, r);
		Set<Quad> allocSites = newInstReflSites.get(r);
		if(allocSites != null) {
			Set<Quad> l_allocSites = newInstReflSites.get(l);
			if(l_allocSites != null) {
				if(l_allocSites.addAll(allocSites)) {
					changed = true; 
				}
				//don't set changed; we already processed this copy adequately
			} else { //l_allocSites was null, so the copy is changing something.
				changed = true;
				newInstReflSites.put(l, allocSites);
			}
		}  
	}

	/*
	 * On an invoke, need to see if it's a reflection site, and if so
	 * update maps
	 */
	@Override
	protected void processInvoke(Quad q) {
		jq_Method caller = q.getMethod();
		//	System.out.println("CastBasedStaticReflect.processInvoke on " + caller);

		RegisterOperand retOp = Invoke.getDest(q);
		if(retOp == null) {
			return;
		}
		Set<Quad> oldS = newInstReflSites.get(retOp.getRegister());

			//a call to a reflection method
		if(newInstSites.contains(q)) {
			if(oldS == null) {
				ArraySet<Quad> set = new ArraySet<Quad>();
				set.add(q);
				newInstReflSites.put(retOp.getRegister(), set);
				changed = true;
			} else if(oldS.add(q))
				changed = true;
			return;
		}

		/*
		 * Apply summary model, figure out if call might return a reflective value
		 */
		jq_Method summarizedCallee = Invoke.getMethod(q).getMethod();
		Set<Quad> allocSites = reflectRetMeths.get(summarizedCallee);
		Set<jq_Method> calleeSet = new HashSet<jq_Method>();
		Operator op = q.getOperator();
		//figure out who might get called
		if(op instanceof InvokeVirtual || op instanceof InvokeInterface) {
			if(allocSites == null)
				allocSites = new LinkedHashSet<Quad>();
			jq_NameAndDesc nd = summarizedCallee.getNameAndDesc();

			jq_Class rawCalledClass = summarizedCallee.getDeclaringClass();
			for(jq_Reference cl: reachableAllocClasses) {
				boolean matches = (cl instanceof jq_Class) && cl.isSubtypeOf(rawCalledClass);
				if(matches) {
					jq_Method targMeth = cl.getVirtualMethod(nd);
					if(targMeth == null)
						continue;
					calleeSet.add(targMeth);
					Set<Quad> returnedASites = reflectRetMeths.get(targMeth);
					if(returnedASites != null)
						allocSites.addAll(returnedASites);
				}
			}
		}  else if(op instanceof InvokeStatic) {
			calleeSet.add(summarizedCallee);
		}
		if(calleeSet.size() > 0) {
			calleeSet.remove(caller); //recursion SHOULD be okay but this is safer
			for(jq_Method calleee: calleeSet) {
				Set<jq_Method> s = callers.get(calleee);
				if(s == null) {
					s = new HashSet<jq_Method>();
					callers.put(calleee, s);
				}
				s.add(caller);
			}
		}
		if(allocSites == null)
			return;

		if(oldS == null) {
			newInstReflSites.put(retOp.getRegister(),allocSites);
			changed = true;
		} else if(oldS.addAll(allocSites))
			changed = true;
	}
/*
 * On a return, need to update summary table of function reflective returns, 
 * and then propagate up the call stack
 */
	protected void processReturn(Quad q) {
		jq_Method returningM = cfg.getMethod();
		Operand ro = Return.getSrc(q);
		if (ro instanceof RegisterOperand) {
			Register r = ((RegisterOperand) ro).getRegister();
			Set<Quad> allocSites = newInstReflSites.get(r);
			Set<Quad> oldAllocSites = reflectRetMeths.get(returningM);

			//we found alloc sites for the return value that weren't previously known
			if( (allocSites != null  && allocSites.size() > 0) && 
					(oldAllocSites == null || oldAllocSites.size() < allocSites.size()))  {
				propagatedAReturn = true;
				if(DEBUG)
					System.out.println("method " + returningM + " has newly found reflective return value");
				if(oldAllocSites != null)
					allocSites.addAll(oldAllocSites);//this appears necessary but I'm not sure why...shouldn't be.

				reflectRetMeths.put(returningM, allocSites);

				// rescan callers
				Set<jq_Method> callerSet = callers.get(returningM);
				if(callerSet != null)
					for(jq_Method caller: callerSet) {
						if(DEBUG)
							System.out.println("rescanning caller " + caller);
						staticReflectResolved.remove(caller);
						this.run(caller); //reprocess.  This is comparatively cheap, since
						//we're only scanning immediate callers. Recursion stops as soon 
						//as we run out of methods that return reflective values
					}
			}
		}
	}

	boolean propagatedAReturn = false;
	/*
	 * Used to cue RTA to do a new iteration
	 */
	public boolean needNewIter() {
		return propagatedAReturn;
	}

	public void startedNewIter() {
		propagatedAReturn = false;
	}
}
