package chord.analyses.exceptionHandlerBugs;
import chord.analyses.exceptionHandlerBugs.CodePrinting;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.io.IOException;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.ExceptionHandler;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.BasicBlock;
import chord.program.Program;
import chord.project.analyses.JavaAnalysis;
import chord.project.Chord;
import chord.project.Config;



/**
  * This analysis tries to find the empty exception handlers. 
  * In particular, if a handler only prints an error message, 
  * it will still be treated as an empty one. 
  *  
  *  TODO: 
  *  1. Differentiate the errors thrown during clean_up and close phases
  *  2. In each warning, also include the statements that reached there
  *  3. Indicate about the over-catching cases..
  *  4. Fix false positive bugs
  *  
  *  @author Ding Yuan
  */
@Chord(
   name = "exception-empty-handler-java"
)

public class CheckEmptyHandler extends JavaAnalysis {	
	boolean hasLog; // a global variable to indicate whether the exception handler has log or not
	int startingLine; // the startingLine of a try block. for printing use.
	int endingLine; // the endingLine of an exception handler. for printing use.
	CodePrinting printer;
	int totalCatches;
	
	boolean checkEmptyHandler (ExceptionHandler eh) {
		int handlerBBCount = 0;
		/* Next: check if this handler is empty! */
		hasLog = false;
		endingLine = 0;
		// boolean hasMoveA = false;
		
		/* At the beginning: assume it is empty*/
		BasicBlock exbb = eh.getEntry();
	
		while (exbb != null) {
			/* So it has a bunch of handler basic blocks... */		
			int bbsize = exbb.size();
			handlerBBCount++;

			if ((Config.verbose > 1) && (bbsize > 0)) {
				System.out.println("DEBUG: Exception handler " + handlerBBCount
						+ ": " + exbb 
						+ "@line: " + exbb.getLastQuad().getLineNumber());
			}
			
			/* Iterate through every quad. */
			for (int qIdx = 0; qIdx < bbsize; qIdx++) {
				Quad q = (Quad) exbb.getQuad(qIdx); // the call-site, in quad format
				Operator op = q.getOperator();
			    if (Config.verbose > 1) {
				    System.out.println("DEBUG: quad@line:" + q.getLineNumber() + ": " + q);
			    }

				/* MOVE_I, PUTSTATIC_A, */
				if (op instanceof Operator.Branch 
					|| op instanceof Operator.Return
					|| op instanceof Operator.Move.MOVE_I
					|| op instanceof Operator.Putstatic
					|| op instanceof Operator.Putfield) 
				{
					System.out.println("DEBUG: meaningful quad@line:" + q.getLineNumber() + ": " + q);
					return false;
				}
				
				/*
				if (op instanceof Operator.Move.MOVE_A) {
					hasMoveA = true;
				} */
			
				if (op instanceof Operator.Invoke) {
					/* in the case of invoke, we need to look at the target: */
					jq_Method targetMethod = ((Operator.Invoke)op).getMethod(q).getMethod();
					String classStr = targetMethod.getDeclaringClass().getName();
					// System.out.println("DEBUG: classStr: " + classStr);
					if (classStr.equals("java.lang.StringBuilder")) {
						// This is not a call that will make the handler bb meaningful. We should go on...
						if (Config.verbose > 1) {
							System.out.println("DEBUG: String builder, not meaningful call..." + q);
							System.out.println();
						}
						continue;
					}
				
					//if (classStr.equals("org.apache.commons.logging.Log")) {
					String methodStr = targetMethod.toString();
					if (methodStr.contains("warn:") 
						|| methodStr.contains("info:")
						|| methodStr.contains("debug:")
						|| methodStr.contains("error:")
							) {
						hasLog = true;
						if (Config.verbose > 1) {
							System.out.println("DEBUG: Logging call, not meaningful call..." + q);
							System.out.println();
						}
						continue;
					}
				
					if (Config.verbose > 1) {
						System.out.println("DEBUG: meaningful quad: Invocation: " + methodStr + " not empty!");
					}
					return false;
				}
				if (Config.verbose > 1) {
					System.out.println("DEBUG: quad" + q); 
					System.out.println("   Line: " + q.getLineNumber() + "@" + q.getMethod().getDeclaringClass().getSourceFileName()); 
					System.out.println("   operator " + op + " is an empty operator");

					System.out.println();
				}
				
				if (q.getLineNumber() > endingLine) {
					endingLine = q.getLineNumber();
				}
			}
			
			/* The code below is a workaround: for chord, sometimes it will split a basic block into two. 
			 * We detect this case by checking if the only successor has only one predecessor. In that case, 
			 * these two BBs are 1-1 mapping, and we will further check the next basic block. */
			List<BasicBlock> successors = exbb.getSuccessors();
			if (Config.verbose > 1) {
				System.out.println("DEBUG: Successor of the exception handler: " + exbb);
				for (BasicBlock nbb : successors) {
					System.out.println("  " + nbb + ": last quad@line: " + nbb.getLastQuad().getLineNumber() + ", " + nbb.getLastQuad());
					System.out.println("    This bb has " + nbb.getPredecessors().size() + " predecessors!");
				}
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
		
		/*if ((hasLog == false) && (hasMoveA == true)) {
			if (Config.verbose > 1) {
				System.out.println("DEBUG: does not have log but has a MoveA, not empty!");
			}
			return false; // no log, but with moveA, then it's a meaningful quad
		}*/
		if (Config.verbose > 1) {
			System.out.println("DEBUG: is empty handler!");
		}
		return true;
	}

	public void run() {
		int bugID = 1;
		totalCatches = 0;

		Program program = Program.g();
		Hashtable<String, Integer> warningMap = new Hashtable<String, Integer>();

		printer = new CodePrinting();
		
		for (jq_Method m : program.getMethods()) {
		    if (!m.isAbstract()) {
				System.out.println("Method found: " + m);
				String srcFile = m.getDeclaringClass().getSourceFileName();

				ControlFlowGraph cfg = null;
				try {
					cfg = m.getCFG();
				} catch (Throwable e) {
                    // We need throwable here because "java.lang.ClassFormatError" can be thrown
					System.out.println("INFO: Method: " + m + " does not have a CFG..." + e);
	    			continue;
				}
	    		if (cfg == null) {
	    			// This is not expected...
	    			System.out.println("INFO: Method: " + m + " does not have a CFG...");
	    			continue;
	    		}
	    		
	    		for (BasicBlock bb : cfg.reversePostOrder()) {
	    			boolean throws_exception = false; // Does this bb throw exception or not?
	    			startingLine = 100000;
	    			// ArrayList<jq_Class> exceptionsThrown = new ArrayList<jq_Class>();

	    			/* Every basic block... */
	    			if (Config.verbose > 1) {
	    				System.out.println("  DEBUG: Basic Block: " + bb);
	    			}

	    			/* First, we check if this BB throws a valid exception or not. 
	    			 * Theoretically we do not have to do this, that we can just enumerate
	    			 * all the exception handler blocks and check if they're empty. 
	    			 * 
	    			 * However, we have to do this because sometimes chord models the java programs
	    			 * in a weird way - that some BBs are treated as exception handler when 
	    			 * they're actually not. To prune these cases, we do this extra check: only if 
	    			 * a BB throws an exception, we further analyze its handler. */ 
	    			for (int i = 0; i < bb.size(); i++) {
	    				Quad q = bb.getQuad(i);
	    				try {
	    					if (q.getLineNumber() < startingLine) {
	    						startingLine = q.getLineNumber();
	    					}
	    					if (Config.verbose > 1) {
	    						System.out.println("DEBUG: Quad: " + q 
		    							+ " at LINE: " + q.getLineNumber());
	    					}
	    				} catch (Throwable e) {
	    					// q.getLineNumber might throw weird exception..
	    					// do nothing
	    				}
	    				Operator op = q.getOperator();
	    				if (op instanceof Operator.Invoke) {
	    					/* in the case of invoke, we need to look at the target: */
	    					jq_Method targetMethod = ((Operator.Invoke)op).getMethod(q).getMethod();

	    			        String methodStr = targetMethod.toString();
	    			        if (Config.verbose > 1) {
	    						System.out.println("DEBUG: Invoke: " + q 
		    							+ " target: " + methodStr);
	    					}
	    			        if (methodStr.contains("close") || 
	    			              methodStr.contains("cleanup") ||
	    			              methodStr.contains("stop") ||
	    			              methodStr.contains("shutdown")) {
	    			            // Prune false positive: ignore the exceptions thrown by close or cleanup
	    			        	continue; 
	    			        }
	    			        	
	    				}
	    				for (jq_Class exc : q.getThrownExceptions()) {
	    					/* Now, ignore two most general classes: java.lang.Error, 
	    					 *  and java.lang.RuntimeException. */
	    					String excStr = exc.getName();
	    					if (excStr.equals("java.lang.Error") || excStr.equals("java.lang.RuntimeException")) {
	    						continue;
	    					}
	    					
	    					/* Now, this exception can be thrown... */
	    					throws_exception = true;
	    					// if (Config.verbose > 0){
	    						System.out.println("    Quad: " + q 
	    							+ " at LINE: " + q.getLineNumber() 
	    							+ " throws exception: " + excStr);
	    					// }
	    				}
	    			}
	    			
	    			if (throws_exception == false) {
	    				/* If this BB does not even throw a valid exception, we do not bother to check
	    				 * its exception handler. */
	    				continue;
	    			}
	    			
		    		/* This BB might throw some exceptions. */
	    			/* Now we need to see if the exceptions are handled... */
	    			for (ExceptionHandler eh : bb.getExceptionHandlers()) {
	    				jq_Class handledEx = eh.getExceptionType();
	    				totalCatches++;
	    				System.out.println("DEBUG: Found handler for " + handledEx.toString() + ", BB: " + bb 
	    						+ "@line: " + bb.getLastQuad().getLineNumber() + ", File: " + srcFile);
	    				
	    				if (handledEx.toString().equals("java.io.FileNotFoundException")) {
	    					// Ignore this exception
	    					continue;
	    				}

	    				if (checkEmptyHandler(eh) == true){
	    					/* Have we seen this exception before? */
	    					String bugInfo = eh.toString() + m.toString() + m.getDeclaringClass().toString();
	    					if (warningMap.containsKey(bugInfo)) {
	    						continue;
	    					}
	    					warningMap.put(bugInfo, 1);
	    					if (handledEx.getName().equals("java.lang.InterruptedException") && hasLog == true) {
	    						continue; // heuristic to prune FP: if it's interrupted exception and has log, ignroe..
	    					}
		    				System.out.println("==========================================");
	    					System.out.println("WARNING " + bugID + ": empty handler for exception: " + handledEx.getName());
	    					if (hasLog == true) {
		    					System.out.println("There are log messages.. ");
	    					}
	    					else {
	    						System.out.println("THERE IS NO LOG MESSAGE!!!");
	    					}
	    					System.out.println("  Line: " + eh.getEntry().getLastQuad().getLineNumber()
	    							+ ", File: \"" + srcFile + "\"");
	    					
	    					try {
	    						printer.printCode(startingLine, endingLine, srcFile);
	    					} catch (IOException ioe) {
	    						System.out.println("printCode failed with file: " + srcFile 
	    								+ ", line: " + startingLine + "-" + endingLine);
	    					}
		    				System.out.println("==========================================");
		    				bugID++;
	    				}	    				
	    			}	    				
	    		}	    			
		    } // if (!m.isAbstract())
		} // for (jq_Method m : program.getMethods())
		System.out.println("==== Total catch blocks analyzed: " + totalCatches + " =========");
	} // run
} // class