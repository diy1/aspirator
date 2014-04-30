package chord.analyses.exceptionHandlerBugs;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.ExceptionHandler;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.BasicBlock;
import chord.program.Program;
import chord.project.analyses.JavaAnalysis;
import chord.project.Chord;
import chord.project.Config;
import chord.util.Utils;



/**
  * This analysis finds exception handler with comment "TODO". 
  * The current implementation is a bit ugly -- copy-pasted code from everywhere..
  * 
  *  @author Ding Yuan
  */
@Chord(
   name = "exception-todo-in-handler-java"
)

public class TODOinHandler extends JavaAnalysis {	
	int bugID;
	Hashtable<String, Integer> warningMap;
	
    void fileSearchAndReport (int tryBlockStartingLine, int handlerStartingLine, 
        int handlerEndingLine, String fileName) {
        List<String> buffer = new ArrayList<String>();

        if ((handlerStartingLine > handlerEndingLine) || (handlerStartingLine <= 0)) {
            System.out.println("ERROR: Invalid line range: " + handlerStartingLine +
              "-" + handlerEndingLine + ", File: " + fileName);
            return;
        }

        String srcPathName = Config.srcPathName;
        if (srcPathName == null) {
           System.out.println("chord.src.path is not defined, " 
               + "cannot run analysis: exception-todo-in-handler-java.");
           return;
		}
        String[] srcDirNames = srcPathName.split(Utils.PATH_SEPARATOR);

        for (String path : srcDirNames) {
       	   String fullpath = path + "/" + fileName;
       	   BufferedReader br = null;
       	   try {
              br = new BufferedReader(new FileReader(fullpath));

       		  if (Config.verbose > 0) {
       	         System.out.println("DEBUG: fileSearchAndReport: " + handlerStartingLine 
                     + "-" + handlerEndingLine + "@" + fullpath);
       	         System.out.println();
       	      }
       		
       	      int i = 0;
              String line = null;
              
         	  boolean hasTodo = false;
           	  boolean hasFixme = false;
           	  boolean searchStart = false;
           	  int buggyLine = 0;
              // The loop below is to build a buffer: containing the lines from the       
              //   beginning of the file to the end of exception handling block.
              // The reason that we have to do this is the handlerEndingLine from Joeq
              //   may not be accurate, therefore we have to search for a more accurate
              //   handlerEndingLine.
              do {
                 line = br.readLine();
                 buffer.add(line); // this buffer is for later error reporting.

                 i++;
                 if (i == handlerStartingLine) {
                    searchStart = true;
                 }

                 if (searchStart == true) {
                    if (line.contains("TODO")) {
                        hasTodo = true;
                        buggyLine = i;
                    }
                    if (line.contains("FIXME")) {
                        hasFixme = true;
                        buggyLine = i;
                    }
                 }

                 if (i == handlerEndingLine) {
                    // It is possible that this line is: catch {.*}
                    if (line.matches("catch\\s*\\{.*\\}") == true) {
                        break;
                    }
                 }  
                 
                 if (i >= handlerEndingLine) {
                    // Search for the next "}"
                    if (line.contains("}")) {
                       break;
                    }
                 }
            } while (line != null);

       		/* int lineNum = buffer.size();
       		while (lineNum >= 0) {
       			line = buffer.get(lineNum - 1);
       			if (line.contains("TODO")) {
       				hasTodo = true;
       				break;
       			}
       			if (lineNum <= handlerStartingLine) {
       				break;
       			}
       			lineNum--;
       		}
       		*/
              
       		if ((hasTodo == true) || (hasFixme == true)) {
       			// OK, now we need to report the result...
				String bugIndex = buggyLine + fileName;
				if (warningMap.containsKey(bugIndex)) {
					return; // we reported it already..
				}
				warningMap.put(bugIndex, 1);

				String bugInfo = "";
				if (hasTodo) {
					bugInfo = bugInfo + "TODO; ";
				}
				if (hasFixme) {
					bugInfo = bugInfo + "FIXME; ";
				}
				System.out.println("==========================================");
				System.out.println("WARNING " + bugID + ": " + bugInfo + " in handler.");
				
				System.out.println("  Line: " + buggyLine
						+ ", File: \"" + fileName + "\"");
				System.out.println();
				
				int lastTryLine = 0;
				int lineId = 0;
				// Search for the "try" for the try block
				for (lineId = tryBlockStartingLine; lineId > 0; lineId--) {
					if (buffer.get(lineId - 1).contains("try")) {
						lastTryLine = lineId;
						break;
					}
				}
				
				if (lastTryLine == 0) {
					System.out.println("INFO: cannot find the start of a try block...");
				}
				else {
					for (lineId = lastTryLine; lineId <= buffer.size(); lineId++) {
						System.out.println(lineId + ": " + buffer.get(lineId-1));
					}
				}
				System.out.println();
				System.out.println("==========================================");
				bugID++;
       		}
       		return;
       	} catch (FileNotFoundException e) {
       		// ignore
       		if (Config.verbose > 1) {
       			System.out.println("Cannot find file: " + fullpath);
       		}
       		continue;
       	} catch (IOException ioe) {
       		System.out.println("ERROR: IOException found: " + ioe);
       	} finally {
       		try {
       			if (br != null)
       				br.close();
       		} catch (IOException ioe) {
           		System.out.println("ERROR: IOException found in close: " + ioe);

       		}
       	}
       }
       System.out.println("INFO: cannot print source info (likely chord.src.path is wrong): " + fileName);
       return;
	}

	void checkTODOandReport(ExceptionHandler eh, String fileName, int tryBlockStartingLine) {
		int handlerBBCount = 0;
		
		/* Next: check if this handler is empty! */
		int handler_endingLine = 0;
		int handler_startLine = 10000;
		
		/* At the beginning: assume it is empty*/
		BasicBlock exbb = eh.getEntry();
	
		/* This loop is to compute the ending line number for the exception handler block.. */
		while (exbb != null) {
			/* So it has a bunch of handler basic blocks... */		
			int bbsize = exbb.size();

			if (bbsize > 0) {
				System.out.println("DEBUG: Exception handler " + handlerBBCount
						+ ": " + exbb 
						+ "@line: " + exbb.getLastQuad().getLineNumber());
			}
			
			/* Iterate through every quad. */
			for (int qIdx = 0; qIdx < bbsize; qIdx++) {
				Quad q = (Quad) exbb.getQuad(qIdx); 
				
				int qLineNumber = q.getLineNumber();
				if (qLineNumber > handler_endingLine) {
					handler_endingLine =qLineNumber;
				}
				if ((qLineNumber != 0) && (qLineNumber < handler_startLine)) {
					handler_startLine = qLineNumber;
				}
			}
			
			/* The code below is a workaround: for chord, sometimes it will split a basic block into two. 
			 * We detect this case by checking if the only successor has only one predecessor. In that case, 
			 * these two BBs are 1-1 mapping, and we will further check the next basic block. */
			List<BasicBlock> successors = exbb.getSuccessors();
			if (Config.verbose > 0) {
				System.out.println("DEBUG: Successor of the exception handler: " + exbb);
			}
			if (successors.size() == 1 && successors.get(0).getPredecessors().size() == 1) {
				/* this block has more than one successor BBs... */
				exbb = successors.get(0);
				if (Config.verbose > 0)
					System.out.println("DEBUG: this exception handler bb has a 1-1 successor bb: " + exbb + ", continuing..");
			}
			else {
				exbb = null;
			}
		}
		
		// OK, now, after the above loop, we got the last line number of the exception handler block. 
		//   Next, we are going to open the file and search for "TODO". The handler line number is:
		//   [handler_startLine, handler_endingLine]
			fileSearchAndReport (tryBlockStartingLine, 
					handler_startLine, handler_endingLine, fileName);
	}

	public void run() {
		bugID = 1;

		Program program = Program.g();
		warningMap = new Hashtable<String, Integer>();
		
		for (jq_Method m : program.getMethods()) {
		    if (!m.isAbstract()) {
				System.out.println("Method found: " + m);
				String srcFile = m.getDeclaringClass().getSourceFileName();

				ControlFlowGraph cfg = null;
				try {
					cfg = m.getCFG();
				} catch (Exception e) {
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
	    			int tryBlockStartingLine = 100000;

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
	    					if (q.getLineNumber() < tryBlockStartingLine) {
	    						tryBlockStartingLine = q.getLineNumber();
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
	    					//if (Config.verbose > 0){
	    						System.out.println("    Quad: " + q 
	    							+ " at LINE: " + q.getLineNumber() 
	    							+ " throws exception: " + excStr);
	    					//}
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
	    				if (Config.verbose > 0)
	    					System.out.println("DEBUG: Found exception handler for BB: " + bb 
	    							+ "@line: " + bb.getLastQuad().getLineNumber() + ", File: " + srcFile);

	    				checkTODOandReport(eh, srcFile, tryBlockStartingLine);   				
	    			}	    				
	    		}	    			
		    } // if (!m.isAbstract())
		} // for (jq_Method m : program.getMethods())
	} // run
} // class