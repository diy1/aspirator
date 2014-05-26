package chord.analyses.exceptionHandlerBugs;
import chord.analyses.exceptionHandlerBugs.CodePrinting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.ExceptionHandler;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.BasicBlock;
import chord.program.Program;
import chord.project.analyses.JavaAnalysis;
import chord.project.Chord;


/**
  * This analysis finds bugs related to not handled exceptions.
  *  
  *  @author Ding Yuan
  */
@Chord(
   name = "unhandled-exception-java"
)

public class CheckUnhandledExceptions extends JavaAnalysis {	

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
				//if (handledEx.toString().equals("java.lang.Throwable")
				//		|| handledEx.toString().equals("java.lang.Exception")
				//		) {
					/* workaround in some general cases: if handledEx is Throwable or Exception, then
					 * the thrownEx most likely is a subclass! */
					//return true;
				//}
				return true; // conservatively return true to fold false positives...
			}
			// System.out.println(" is a subclass of: " + subclass);
			
		}
		return false;
	}
	
	public void run() {
		int bugID = 0;
		int startingLine;
		int endingLine;
		Program program = Program.g();
		CodePrinting printer = new CodePrinting();
		/* warningMap stores the line #, filename for each */
		Hashtable<String, Integer> warningMap = new Hashtable<String, Integer>();
		
		for (jq_Method m : program.getMethods()) {
		    if (!m.isAbstract()) {
		    	if (m.toString().startsWith("<clinit>")) {
		    		/* Ignoring the exceptions in clinit completely... */
		    		continue;
		    	}
				System.out.println("Method found: " + m);
				ControlFlowGraph cfg = null;
				try {
					cfg = m.getCFG();
				} catch (Throwable e) {
					System.out.println("WARNING: cannot get cfg for method: " + m + ", " + e);
					continue;
				}
	    		if (cfg == null) {
	    			// This is not expected...
	    			System.out.println("Method: " + m + " does not have a CFG...");
	    			continue;
	    		}
	    		
	    		for (BasicBlock bb : cfg.reversePostOrder()) {
	    			startingLine = 100000;
	    			endingLine = 0;
	    			
	    			/* Every basic block... */
	    			System.out.println("  Basic Block: " + bb);
	    			List<jq_Class> thrownExList = new ArrayList<jq_Class>();
	    			List<jq_Class> unhandledExList = new ArrayList<jq_Class>();
	    			
	    			Map <jq_Class, List<Quad>> exQuadMap = new HashMap<jq_Class, List<Quad>>();
	    				    			
	    			boolean throws_exception = false; // Does this bb throw exception or not?
	    			boolean has_handler = false; // Is there an exception handler for this bb?
	    			
	    			for (int i = 0; i < bb.size(); i++) {
	    				Quad q = bb.getQuad(i);
	    				try {
	    					if ((q.getLineNumber() < startingLine) && (q.getLineNumber() > 0)) {
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
	    					if (excStr.equals("java.lang.Error") 
	    							|| excStr.equals("java.lang.RuntimeException")
	    							|| excStr.equals("java.lang.Throwable")
	    							|| excStr.equals("java.lang.NegativeArraySizeException")
	    							|| excStr.equals("org.apache.hadoop.util.ExitUtil$ExitException")
	    							|| excStr.equals("java.lang.AssertionError")
	    							|| excStr.equals("org.apache.commons.logging.LogConfigurationException")
	    							|| excStr.equals("java.lang.CloneNotSupportedException")) {
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
	    					try {
	    						int exhandlerLineNum = eh.getEntry().getLastQuad().getLineNumber();
	    						if (exhandlerLineNum > endingLine) {
	    							endingLine = exhandlerLineNum;
	    						}
	    					} catch (Throwable e) {
	    						//ignore...
	    					}
	    				}
	    				
	    				for (jq_Class unhandledEx : unhandledExList) {
	    					boolean ignore = false;
	    					if (has_handler == false) {
	    					/* Now, there simply isn't any handler. Maybe it will just
	    					 * propagate to the caller... In that case, we should ignore...
	    					 * */
	    						if (m.getThrownExceptionsTable() != null) {
	    							for (jq_Class tex : m.getThrownExceptionsTable()) {
	    							// System.out.print(tex + ", ");
	    								if (tex.equals(unhandledEx)
	    									|| isSubClass(unhandledEx, tex) // unhandledEx is a subclass of tex
	    									) {
	    									// this exception will be propagated to the caller...
	    									System.out.println("DEBUG: Exception " + unhandledEx
	    										+ " will be simply propagated to the caller (this "
	    										+ "method throws: " + tex);
	    									ignore = true;
	    									break;
	    								}
	    							}
	    						}	    							    							    						
	    					}    				
	    					
	    					if (ignore == true) {
	    						continue;
	    					}
	    					
	    					/* Next, we check if the exception is a subclass of RuntimeException. If so, 
    						 * it probably is OK... */
	    					jq_Class superclass = null;
	    					try {
	    						superclass = unhandledEx.getSuperclass();
	    					} catch (RuntimeException e) {
	    						ignore = true; // conservative to fold false positives
	    					}
    						while (superclass != null) {
    							if (superclass.toString().equals("java.lang.RuntimeException")) {
    								// ignore this...
    								ignore = true;
    								break;
    							}
    							try {
    								superclass = superclass.getSuperclass();
    							} catch (RuntimeException e) {
    								ignore = true;
									break;
    							}			
    						}
    						
    						if (ignore == true) {
    							continue;
    						}
    						
	    					/* Have we seen this exception before? */
	    					String bugInfo = unhandledEx.toString() + bb.toString() + m.toString() + m.getDeclaringClass().toString();
	    					if (warningMap.containsKey(bugInfo)) {
	    						continue;
	    					}
	    					warningMap.put(bugInfo, 1);
	    					
	    					bugID++;
	    					System.out.println("==========================================");
	    					System.out.println("WARNING " + bugID + ": exception: " + unhandledEx + " is not handled!");
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
	    					try {
	    						if (endingLine == 0) {endingLine = startingLine;}
	    						printer.printCode(startingLine, endingLine, m.getDeclaringClass().getSourceFileName(), true);
	    					} catch (IOException ioe) {
	    						System.out.println("printCode failed with file: " + m.getDeclaringClass().getSourceFileName() 
	    								+ ", line: " + startingLine + "-" + endingLine);
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
