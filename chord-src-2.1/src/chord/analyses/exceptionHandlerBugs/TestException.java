package chord.analyses.exceptionHandlerBugs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.ExceptionHandler;
import joeq.Compiler.Quad.Operand.MethodOperand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.BasicBlock;
import chord.program.Program;
import chord.project.analyses.JavaAnalysis;
import chord.project.Chord;


/**
  * This analysis is to test the CFG APIs. It prints the following information:
  *  1. All the basic blocks;
  *  2. All the quads within each basic blocks;
  *  3. All the local variables;
  *  
  *  @author Ding Yuan
  */
@Chord(
   name = "exception-test-java"
)

public class TestException extends JavaAnalysis {	
	boolean checkEmptyHandler (ExceptionHandler eh) {
		/* Next: check if this handler is empty! 
		 * The key question is how do we define that an exception handler is EMPTY? */
		
		/* At the beginning: assume it is empty*/
		BasicBlock exbb = eh.getEntry();
		System.out.println("DEBUG: exception handler block: " + exbb);
	
		while (exbb != null) {
			/* So it has a bunch of handler basic blocks... */
		
			int bbsize = exbb.size();

			for (int qIdx = 0; qIdx < bbsize; qIdx++) {
				Quad q = (Quad) exbb.getQuad(qIdx); // the call-site, in quad format
				Operator op = q.getOperator();
			
				if (op instanceof Operator.Branch 
					|| op instanceof Operator.Return) {
					System.out.println("DEBUG: meaningful quad: " + q);
					return false;
				}
			
				if (op instanceof Operator.Invoke) {
					/* in the case of invoke, we need to look at the target: */
					jq_Method targetMethod = ((Operator.Invoke)op).getMethod(q).getMethod();
					String classStr = targetMethod.getDeclaringClass().getName();
					// System.out.println("DEBUG: classStr: " + classStr);
					if (classStr.equals("java.lang.StringBuilder")) {
						// This is not a call that will make the handler bb meaningful. We should go on...
						System.out.println("DEBUG: String builder, not meaningful call..." + q);
						System.out.println();
						continue;
					}
				
					//if (classStr.equals("org.apache.commons.logging.Log")) {
					String methodStr = targetMethod.toString();
					if (methodStr.contains("warn:") 
						|| methodStr.contains("info:")
						|| methodStr.contains("debug:")
						|| methodStr.contains("error:")
							) {
						System.out.println("DEBUG: Logging call, not meaningful call..." + q);
						System.out.println();
						continue;
					}
				
					System.out.println("DEBUG: meaningful quad: Invocation: " + methodStr + " not empty!");
					return false;
					//System.out.println("DEBUG: invoke quad. getDest: " + ((Operator.Invoke)op).getDest(q));
					//System.out.println("     getMethod: " + ((Operator.Invoke)op).getMethod(q));

				}
				System.out.println("DEBUG: quad" + q); 
				System.out.println("   Line: " + q.getLineNumber() + "@" + q.getMethod().getDeclaringClass().getSourceFileName()); 
				System.out.println("   operator " + op + " is an empty operator");

				System.out.println();

			}
			List<BasicBlock> successors = exbb.getSuccessors();
			System.out.println("DEBUG: Successor of the exception handler: " + exbb);
			for (BasicBlock nbb : successors) {
				System.out.println("  " + nbb + ": last quad@line: " + nbb.getLastQuad().getLineNumber() + ", " + nbb.getLastQuad());
				System.out.println("    This bb has " + nbb.getPredecessors().size() + " predecessors!");
			}
			if (successors.size() == 1 && successors.get(0).getPredecessors().size() == 1) {
				/* this block has more than one successor BBs... */
				exbb = successors.get(0);
				System.out.println("DEBUG: this exception handler bb has a 1-1 successor bb: " + exbb + ", continuing..");
			}
			else {
				exbb = null;
			}
		}
		
		return true;
	}
	
	boolean isSubClass(jq_Class thrownEx, jq_Class handledEx) {
		/* Return true if thrownEx is a subclass of handledEx. */
		jq_Class subclass = thrownEx;
		while (subclass != null) {
			if (subclass.equals(handledEx)) {
				return true;
			}
			// System.out.print("Debug: class: " + subclass);
			try {
				subclass = subclass.getSuperclass();
			} catch (RuntimeException e) {
				System.out.println("ERROR: Failed to get superclass for " + subclass + "; exception: " + e);
				return false;
			}
			// System.out.println(" is a subclass of: " + subclass);
			
		}
		return false;
	}
	
	public void run() {
		Program program = Program.g();
		
		for (jq_Method m : program.getMethods()) {
		    if (!m.isAbstract()) {
				System.out.println("Method found: " + m);

	    		ControlFlowGraph cfg = m.getCFG();
	    		if (cfg == null) {
	    			// This is not expected...
	    			System.out.println("Method: " + m + " does not have a CFG...");
	    			continue;
	    		}
	    		
	    		for (BasicBlock bb : cfg.reversePostOrder()) {
	    			/* Every basic block... */
	    			System.out.println("  Basic Block: " + bb);
	    			List<jq_Class> thrownExList = new ArrayList<jq_Class>();
	    			List<jq_Class> unhandledExList = new ArrayList<jq_Class>();
	    			
	    			Map <jq_Class, List<Quad>> exQuadMap = new HashMap<jq_Class, List<Quad>>();
	    				    			
	    			boolean throws_exception = false; // Does this bb throw exception or not?
	    			boolean has_handler = false; // Is there an exception handler for this bb?
	    			
	    			for (int i = 0; i < bb.size(); i++) {
	    				Quad q = bb.getQuad(i);
	    				for (jq_Class exc : q.getThrownExceptions()) {
	    					/* Now, ignore two most general classes: java.lang.Error, 
	    					 *  and java.lang.RuntimeException. */
	    					String excStr = exc.getName();
	    					if (excStr.equals("java.lang.Error") || excStr.equals("java.lang.RuntimeException")) {
	    						continue;
	    					}
	    					
	    					/* Now, this exception can be thrown... */
	    					throws_exception = true;
	    					thrownExList.add(exc);
	    					unhandledExList.add(exc);
	    					
	    					if (!exQuadMap.containsKey(exc)) {
	    						exQuadMap.put(exc, new ArrayList<Quad>());
	    					}					
	    					exQuadMap.get(exc).add(q);
	    					
	    					System.out.println("    Quad: " + q 
	    							+ " at LINE: " + q.getLineNumber() 
	    							+ " throws exception: " + excStr);
	    				}
	    			}
	    			
	    			if (throws_exception == true) {
		    			/* This BB might throw some exceptions. */
	    				/* Now we need to see if the exceptions are handled... */
	    				for (ExceptionHandler eh : bb.getExceptionHandlers()) {
	    					has_handler = true;
	    					jq_Class handledEx = eh.getExceptionType();
	    					for (jq_Class thrownEx : thrownExList) {
	    						
	    						if (thrownEx.equals(handledEx)) {
	    							System.out.println("Debug: exception " + thrownEx + " is handled!");
	    							unhandledExList.remove(thrownEx);
	    						}
	    						
	    						/* Now, this thrownEx is not directly handled. But, if thrownEx is a subclass 
	    						 *  of handledEx (e.g., thrownEx = EOFException, handledEx = IOException), 
	    						 *  then it is OK. But not the other way around. */
	    						if (isSubClass(thrownEx, handledEx)) {
	    							System.out.println("The thrown exception: " + thrownEx 
	    									+ " will be handled by SUPERCLASS: " + handledEx);
	    							unhandledExList.remove(thrownEx);
	    						}
	    					}
	    					
	    					if (checkEmptyHandler(eh) == true){
		    					System.out.println("==========================================");
	    						System.out.println("WARNING2: empty handler!");
	    						System.out.println("  Line: " + eh.getEntry().getLastQuad().getLineNumber()
	    								+ ", File: \"" + m.getDeclaringClass().getSourceFileName() + "\"");
		    					System.out.println("==========================================");
	    					}
	    				
	    					// System.out.println("  The exception handler for this bb: " + eh.getExceptionType());
	    				}
	    				
	    				for (jq_Class unhandledEx : unhandledExList) {
	    					System.out.println("==========================================");
	    					System.out.println("WARNING1: exception: " + unhandledEx + " is not handled!");
	    					System.out.print("Thrown at line:");
	    					for (Quad tq : exQuadMap.get(unhandledEx)) {
	    						System.out.println("  " + tq.getLineNumber() + ", File: \"" + m.getDeclaringClass().getSourceFileName() + "\"");
	    						System.out.println("  by quad: " + tq);
	    					}
	    					System.out.println("In method: " + m);
	    					if (has_handler == false) {
	    						System.out.println("  There is simply no any exception handler for this bb in this method!");
	    					}
	    					else {
	    						System.out.print("  There is exception handler for this BB for the follwing exceptions: ");
	    	    				for (ExceptionHandler eh : bb.getExceptionHandlers()) {
	    	    					jq_Class handledEx = eh.getExceptionType();
	    	    					System.out.print(handledEx.getName());
	    	    					if (isSubClass (handledEx, unhandledEx)) {
	    	    						System.out.println(": which is only a subclass of: " + unhandledEx.getName());
	    	    					}
	    	    					else {
	    	    						System.out.println(": no sub-super class relationship with: " + unhandledEx.getName());
	    	    					}
	    	    				}
	    					}
	    					System.out.println("==========================================");
	    					System.out.println();
	    				}
	    			}
	    		}
	    			
		    } // if (!m.isAbstract())
		} // for (jq_Method m : program.getMethods())
	} // run
} // class
			/*		
			jq_TryCatchBC[] tbc_list = m.getExceptionTable();
			if (tbc_list == null) {
				continue;
			}
			int bcSize = tbc_list.length;
			System.out.println("  Exception table (getExceptionTable()): ");
			for (int i = 0; i < bcSize; i++) {
				System.out.println("    " + tbc_list[i]);
			}
			System.out.println();
			
			jq_Class[] eclasses = m.getThrownExceptionsTable();
			if (eclasses == null) {
				continue;
			}
			int ecSize = eclasses.length;
			System.out.println("  Thrown exception table (getThrownExceptionsTable()): ");
			for (int i = 0; i < ecSize; i++) {
				System.out.println("    " + eclasses[i]);
			}
			System.out.println();
			*/
		
		
		/* Not very useful...
		for (jq_Type t : program.getTypes()) {
			System.out.println("Type found: " + t);
		}
		*/
