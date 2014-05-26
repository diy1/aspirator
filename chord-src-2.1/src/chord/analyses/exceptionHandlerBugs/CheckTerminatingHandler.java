package chord.analyses.exceptionHandlerBugs;

import chord.analyses.exceptionHandlerBugs.CodePrinting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

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
  * This analysis is trying to find the exception handlers that will 
  * take down the cluster...
  * 
  * For this analysis: we need to check the following:
  *   1. Does the system contain exit/abort in the exception handler
  *   2. Which exceptions fall into this handler?
  *  
  *  @author Ding Yuan
  */
@Chord(
   name = "terminating-handler-java"
)

public class CheckTerminatingHandler extends JavaAnalysis {	
	String [] terminatingMethods = { "terminat", "halt", "exit", "abort", "fatal" }; // System.exit
	CodePrinting printer;
	int startingLine; // the startingLine of a try block. for printing use.
	int endingLine; // the endingLine of an exception handler. for printing use.

	/* Return 1 if thrownEx is a subclass of handledEx. return -1
	 * if not. Return 0 if they're the same class. */
	int isSubClass(jq_Class thrownEx, jq_Class handledEx) {
		jq_Class subclass = thrownEx;
		if (subclass.equals(handledEx)) {
			return 0;
		}
		while (subclass != null) {
			if (subclass.equals(handledEx)) {
				return 1;
			}
			try {
				subclass = subclass.getSuperclass();
			} catch (RuntimeException e) {
				System.out.println("ERROR: Failed to get superclass for " + subclass + "; exception: " + e);
				return 1; // conservatively return 1 to fold false positives...
			}
			// System.out.println(" is a subclass of: " + subclass);	
		}
		return -1;
	}
	
	boolean checkTerminatingHandler (ExceptionHandler eh) {
		/* Next: check if this handler is terminating? */
		BasicBlock exbb = eh.getEntry();
		System.out.println("DEBUG: exception handler block: " + exbb);
	    
		endingLine = 0;
		
		while (exbb != null) {		
			int bbsize = exbb.size();

			for (int qIdx = 0; qIdx < bbsize; qIdx++) {
				Quad q = (Quad) exbb.getQuad(qIdx); // the call-site, in quad format
				Operator op = q.getOperator();

				if (q.getLineNumber() > endingLine) {
					endingLine = q.getLineNumber();
				}

				if (op instanceof Operator.Invoke) {
					/* in the case of invoke, we need to look at the target: */
					jq_Method targetMethod = ((Operator.Invoke)op).getMethod(q).getMethod();
					//String classStr = targetMethod.getDeclaringClass().getName();
					//if (classStr.equals("org.apache.hadoop.util.ExitUtil")) {	
			        String methodStr = targetMethod.toString();
					System.out.println("DEBUG: invoke in Exception Hanlder: " + q + "; target method: " + methodStr);

			        for (String meth : terminatingMethods) {
			        	if (methodStr.contains(meth)) {
			        		System.out.println("DEBUG: found abort method: " + q);
			        		return true;
			        	}
			        }			
				}
			}
			
			List<BasicBlock> successors = exbb.getSuccessors();
			System.out.println("DEBUG: Successor of the exception handler: " + exbb);
			
			if (successors.size() == 1 && successors.get(0).getPredecessors().size() == 1) {
				/* this block has more than one successor BBs... */
				exbb = successors.get(0);
				System.out.println("DEBUG: this exception handler bb has a 1-1 successor bb: " + exbb + ", continuing..");
			}
			else {
				exbb = null;
			}
		}
		return false;
	}
	
	public void run() {
		int bugID = 1;

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

	                /* We use exceptionsThrown to store all the exceptions thrown by the basic block. 
	                 * Each entry is a pair: <jq_Class exception, String source info>. 
	                 * This way, later when we go into the exception handler, we know exactly how many
	                 * exceptions reach the handler and where they're from! */
	    			Hashtable<jq_Class, List<String>> exceptionsThrown = new Hashtable<jq_Class, List<String>>(); 

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
		    				if (Config.verbose > 1) {
		    					System.out.println("  Found quad@line " + q.getLineNumber() + "; " + q);
		    				}
		    				
	    					if (q.getLineNumber() < startingLine && q.getLineNumber() != 0) {
	    						startingLine = q.getLineNumber();
	    					}
	    				} catch (Throwable e) {
	    					// q.getLineNumber might throw weird exception..
	    					// do nothing
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
	                        if (!exceptionsThrown.containsKey(exc)) {
	                        	exceptionsThrown.put(exc, new ArrayList<String>());
	                        } 
                        	exceptionsThrown.get(exc).add("Line: " + q.getLineNumber() + ": " + q);

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
	    				System.out.println("DEBUG: Found handler for " + handledEx.toString() + ", BB: " + bb 
	    						+ "@line: " + bb.getLastQuad().getLineNumber() + ", File: " + srcFile);


	    				if (checkTerminatingHandler(eh) == true) {
	    					/* Have we seen this exception before? */
	    					String bugInfo = eh.toString() + m.toString() + m.getDeclaringClass().toString();
	    					if (warningMap.containsKey(bugInfo)) {
	    						continue;
	    					}
	    					warningMap.put(bugInfo, 1);
		    				System.out.println("==========================================");
	    					System.out.println("WARNING " + bugID + ": abort in handler for exception: " + handledEx.getName());
	    					System.out.println("  Line: " + eh.getEntry().getLastQuad().getLineNumber()
	    							+ ", File: \"" + srcFile + "\"");
	    					
	    					System.out.println("  Handled exceptions:");
	    					int numHandledEx = 0;
		    				// Next, check if this is an overcatch:
		                    Iterator<jq_Class> exIterator = exceptionsThrown.keySet().iterator();
		                    while(exIterator.hasNext()) {
		                        jq_Class thrownEx = exIterator.next();
		                        if (isSubClass(thrownEx, handledEx) >= 0) {
		                        	numHandledEx++;
		                        	System.out.println("  " + thrownEx.getName());
		                        	List<String> thrownExes = exceptionsThrown.get(thrownEx);
		                        	for (String throwInfo : thrownExes) {
		                        		System.out.println("    @" + throwInfo);
		                        	}
		                        }
		                    }
                            if (numHandledEx > 1) {
                            	System.out.println("ERROR: THIS IS AN OVER-CATCH!!!");
                            } else {
                            	System.out.println("This is not an over-catch");
                            }
	    					
	    					try {
	    						printer.printCode(startingLine, endingLine, srcFile, true);
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
	} // run
	
	
//	public void run() {
//		int bugID = 1;
//
//		Program program = Program.g();
//		
//		for (jq_Method m : program.getMethods()) {
//		    if (!m.isAbstract()) {
//				System.out.println("Method found: " + m);
//				String srcFile = m.getDeclaringClass().getSourceFileName();
//
//	    		ControlFlowGraph cfg = m.getCFG();
//	    		if (cfg == null) {
//	    			// This is not expected...
//	    			System.out.println("INFO: Method: " + m + " does not have a CFG...");
//	    			continue;
//	    		}
//	    		
//	    		for (BasicBlock bb : cfg.reversePostOrder()) {
//	    			boolean throws_exception = false; // Does this bb throw exception or not?
//
//	    			/* Every basic block... */
//	    			System.out.println("  DEBUG: Basic Block: " + bb);
//
//	    			/* First, we check if this BB throws a valid exception or not. 
//	    			 * Theoretically we do not have to do this, that we can just enumerate
//	    			 * all the exception handler blocks and check if they're empty. 
//	    			 * 
//	    			 * However, we have to do this because sometimes chord models the java programs
//	    			 * in a weird way - that some BBs are treated as exception handler when 
//	    			 * they're actually not. To prune these cases, we do this extra check: only if 
//	    			 * a BB throws an exception, we further analyze its handler. */ 
//	    			for (int i = 0; i < bb.size(); i++) {
//	    				Quad q = bb.getQuad(i);
//	    				for (jq_Class exc : q.getThrownExceptions()) {
//	    					/* Now, ignore two most general classes: java.lang.Error, 
//	    					 *  and java.lang.RuntimeException. */
//	    					String excStr = exc.getName();
//	    					if (excStr.equals("java.lang.Error") || excStr.equals("java.lang.RuntimeException")) {
//	    						continue;
//	    					}
//	    					
//	    					/* Now, this exception can be thrown... */
//	    					throws_exception = true;
//	    					System.out.println("    Quad: " + q 
//	    							+ " at LINE: " + q.getLineNumber() 
//	    							+ " throws exception: " + excStr);
//	    				}
//	    			}
//	    			
//	    			if (throws_exception == false) {
//	    				/* If this BB does not even throw a valid exception, we do not bother to check
//	    				 * its exception handler. */
//	    				continue;
//	    			}
//	    			
//		    		/* This BB might throw some exceptions. */
//	    			/* Now we need to see if the exceptions are handled... */
//	    			for (ExceptionHandler eh : bb.getExceptionHandlers()) {
//	    				jq_Class handledEx = eh.getExceptionType();
//	    				System.out.println("DEBUG: Found exception handler for BB: " + bb 
//	    						+ "@line: " + bb.getLastQuad().getLineNumber() + ", File: " + srcFile);
//
//	    				if (checkTerminatingHandler(eh) == true){
//		    				System.out.println("==========================================");
//	    					System.out.println("WARNING " + bugID + 
//	    							": TERMINATING handler for exception: " + handledEx.getName());
//	    					System.out.println("  Line: " + eh.getEntry().getLastQuad().getLineNumber()
//	    							+ ", File: \"" + srcFile + "\"");
//		    				System.out.println("==========================================");
//		    				bugID++;
//	    				}	    				
//	    			}	    				
//	    		}	    			
//		    } // if (!m.isAbstract())
//		} // for (jq_Method m : program.getMethods())
//	} // run */
} // class