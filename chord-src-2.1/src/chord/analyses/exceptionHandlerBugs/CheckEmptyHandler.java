package chord.analyses.exceptionHandlerBugs;
import chord.analyses.exceptionHandlerBugs.CodePrinting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.io.IOException;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.ExceptionHandler;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.RegisterFactory.Register;
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
    CodePrinting printer; // Print the buggy code snippet
    int totalCatches;

    /**
     *  Get the operand that is modified by this quad (e.g., the method return value or destination
     *  of a move instruction. 
     *  @param q the quad
     *  @return null if this quad does not modify any variable. A special case the caller needs
     *  to handle is the constructor, which returns void. 
     *  Or the modified operand. 
     *  */
    Operand getModifiedOperand(Quad q) {
    	/* Helper method: given a quad, get its modified operand */
        Operator op = q.getOperator();
        
        if (op instanceof Operator.Invoke) {
        	// This is easy: the return value should always be a temporary register that is newly 
        	// defined. Simply return this register
        	List <RegisterOperand> definedRegs = q.getDefinedRegisters();
        	if (Config.verbose > 1) {
                for (RegisterOperand r : definedRegs) {
            	    System.out.println("DEBUG [getModifiedReg]: Quad: " + q + ", at LINE: " + q.getLineNumber()
            			+ ", Defined register: " + r);          	
                }
        	}
            if (definedRegs.size() == 0) {
                // no defined registers...
            	// This is caused by a method that returns void. However, one special case that
            	// requires special handling is the constructor (of a void type). This will be 
            	// handled by the caller. 
        	    return null;
        	} else if (definedRegs.size() > 1) {
        		System.out.print("WARN: Invoke Quad: " + q + ", at LINE: " + q.getLineNumber() 
        				+ " Defined multiple registers (using the first): ");
        		for (RegisterOperand r : definedRegs) {
             	    System.out.print(r + ", ");          	
                }
        		System.out.println();
        	}
            return definedRegs.get(0);
        } else if (op instanceof Operator.Move) {
        	return Operator.Move.getDest(q);
        } else if (op instanceof Operator.Getfield) {
        	return Operator.Getfield.getDest(q);
        } else if (op instanceof Operator.Getstatic) {
        	return Operator.Getstatic.getDest(q);
        } else if (op instanceof Operator.Putfield) {
        	return Operator.Putfield.getField(q);
        } else if (op instanceof Operator.Putstatic) {
        	return Operator.Putstatic.getField(q);
        }
        return null;
    }
    
    /* Given the exception handler (an empty one), and the try bb, find the immediate bb after.
     * Note that we need to return a list of BBs, because Chord might split the same
     * BB into different ones.*/
    List<BasicBlock> findImmBB(ExceptionHandler eh) {
    	BasicBlock exbb = eh.getEntry();
        List<BasicBlock> successors = null;
        
        /* This loop is to first find the *last* bb in this exception handler. Normally, since
         * this exception handler is empty, there should be just one bb. But Chord might model
         * this bb in a weird way and split it into different bbs (even if it's an empty one!), 
         * therefore this loop is necessary! */
        while (true) {
        	successors = exbb.getSuccessors();
            if (Config.verbose > 1) {
                System.out.println("DEBUG [findImmBB]: Successor of the exception handler: " + exbb);
                for (BasicBlock nbb : successors) {
                    System.out.println("  " + nbb + ": last quad@line: " + nbb.getLastQuad().getLineNumber() + ", " + nbb.getLastQuad());
                    System.out.println("    This bb has " + nbb.getPredecessors().size() + " predecessors!");
                }
            }
        
            if (successors.size() == 1 && successors.get(0).getPredecessors().size() == 1) {
                /* this block has more than one successor BBs... */
                exbb = successors.get(0);
                System.out.println("DEBUG [findImmBB]: this exception handler bb has a 1-1 successor bb: " + exbb + ", continuing..");
            }
            else {
                break;
            }
        }
        
        /* At this point, exbb is the last bb in the exception handling blocks; successors are its succ. */
        if (successors.size() != 1) {
        	// more than one BB, we can't handle this case...
            System.out.print("INFO [findImmBB]: More than one successors for exception handling block: " + exbb);
            for (BasicBlock nbb : successors) {
                System.out.print(", [" + nbb + ": last quad@line: " + nbb.getLastQuad().getLineNumber() + "]");
            }
        	return null;
        }
        
        if (successors.size() == 0) {
        	// no successor... shouldn't happen!
            System.out.print("WARN [findImmBB]: NO SUCCESSOR BB for exception handling block: " + exbb);
            return null;
        }
        
        List<BasicBlock> immBBs = new ArrayList<BasicBlock>();
        BasicBlock nextBB = successors.get(0);
        immBBs.add(nextBB);
        while (true) {
        	successors = nextBB.getSuccessors();
        	if (successors.size() != 1) {
        		if (Config.verbose > 1) {
        			try {
                	    System.out.println("DEBUG [findImmBB]: more than 1 successors for BB: " + nextBB
                			+ ", at line: " + nextBB.getLastQuad().getLineNumber() + ": ");
                	    for (BasicBlock nbb : successors) {
                            System.out.println("   " + nbb + ": last quad@line: " + nbb.getLastQuad().getLineNumber());
                        }
        			} catch (Exception e) { /* ignore exceptions in printing debug info */ }
                }
        		break;
        	}
        	nextBB = successors.get(0);
        	immBBs.add(nextBB);
        }
        if (Config.verbose > 0) {
        	try {
        	    System.out.println("INFO [findImmBB]: immediate BBs for the catch block: " + exbb
        			+ ", at line: " + exbb.getLastQuad().getLineNumber() + ": ");
        	    for (BasicBlock nbb : immBBs) {
                    System.out.println("   " + nbb + ": last quad@line: " + nbb.getLastQuad().getLineNumber());
                }
        	} catch (Exception e) { /* ignore exceptions during debug info printing. */ }
        	
        }
    	return immBBs;
    }
    
    boolean operandsEquals (Operand src1, Operand src2) {
    	if (src1 != null && src2 != null) {
    		if (src1.toString().equals(src2.toString())) {
    			return true;
    		}
    	}
    	return false;        	
    }
    
    /**
     * Whether this operand op is used by this quad q
     * 
     * @param op: operand
     * @param q : quad
     * @return true if q uses op, false if not
     */
    boolean isThisOperandUsed (Operand op, Quad q) {
    	Operator operator = q.getOperator();
    	if (op instanceof Operand.RegisterOperand) {
    		// This is the easy case since chord already provides API to get all the used registers 
       		boolean usesOp = false; // whether this quad uses the opToTrack
    		for (RegisterOperand r : q.getUsedRegisters()) {
    			if (operandsEquals(r, op)) {
    				if (Config.verbose > 1) {
    				    System.out.println("DEBUG [isThisOperandUsed]: Quad: " + q 
    				    	+ " at LINE: " + q.getLineNumber() 
            				+ ", uses register: " + op);
    				}
    				usesOp = true;
    				break;
    			}
            }
    		return usesOp;
    	} else if (op instanceof Operand.FieldOperand) {
    		// So this operand is a field, and only getfield can uses the field
    		if (operator instanceof Operator.Getfield) {
    			if (operandsEquals(op, Operator.Getfield.getField(q)) == true) {
    				return true;
    			}
    		} else if (operator instanceof Operator.Getstatic) {
    			if (operandsEquals(op, Operator.Getstatic.getField(q)) == true) {
    				return true;
    			}
    		}
    	} 
    	return false;
    }
    
    /**
     *  modifiedOpInTryBB: given the quad ID that throws exception, find the variable that
     *  is modified by this Quad, and track its data-flow to the end of the try block. 
     *  
     *  For example: 
     *  try { 
     *    a = meth_that_throws_exception();
     *    b = a;
     *  } catch (Exception e) {..}
     *  
     *  modifiedOpInTryBB will return the quad operand of b.
     *  
     *  @param trybb - the try basic block
     *  @prama exceptionQID - the ID of the quad that throws exception
     *  @return the operand that is modified by this quad, at the end of the try basic block
     *  */
    Operand modifiedOpInTryBB (BasicBlock trybb, int exceptionQID) {
    	Quad q = trybb.getQuad(exceptionQID);
        Operand opToTrack = getModifiedOperand(q); // the variable value whose data-flow we should track
        int bbsize = trybb.size();
        if (opToTrack == null) {
        	System.out.println("INFO [modifiedOpInTryBB]: this quad has NO modified variable; "
        	  + q + ", at LINE: " + q.getLineNumber() 
        	  + ", FILE: " + q.getMethod().getDeclaringClass().getSourceFileName());
            try {
                if (Operator.Invoke.getMethod(q).toString().contains("<init>")) {
     	    	    // A constructor (<init>) will have a void return type in java bc. For example:
     	    	    // Invoke Quad: 31: INVOKESTATIC_V <init>:()V@..., (T24)
        		    //  the target method returns void. We need to further track the data-flow
                	//  of its return value
                	if (Config.verbose > 1) { 
                		System.out.println("DEBUG [modifiedOpInTryBB]: the exception-throwing quad"
                				+ " at LINE: " + q.getLineNumber() + " is an init: " + q); 
                    }
                	
                	/* Now, we need to track the return value of this <init>. 
                	 *   
                	 * Here is a typical example of the data-flow involving <init>:
                	 *   Quad 1: NEW T2, org.apache.hadoop.hbase.io.TimeRange
                	 *   Quad 2: MOVE_A T3, T2 
                	 *   Quad 3: INVOKESTATIC_V <init>:(JJ)V@org.apache.hadoop.hbase.io.TimeRange, (T3, R1, T4)
                	 *   Quad 4: PUTFIELD_A R0, .tr, T2
                	 *   
                	 * The putfield_a can also be putstatic, or move. But in any case, the 
                	 *  variable should be T2: and then we can just use the standard data-flow 
                	 *  tracking logic on this T2 below.
                	 *   
                	 * Therefore we search backwards for the "new", and find the register assigned by
                	 *  this new.
                	 */   	
                	for (int qIdx = exceptionQID - 1; qIdx >= 0; qIdx--) {
                		Quad prevq = (Quad) trybb.getQuad(qIdx);
                		if (prevq.getOperator() instanceof Operator.Move) {
                			// OK, we found the move, and the move target should be the operand
                            //  we're trying to find
                			opToTrack = Operator.Move.getSrc(prevq);
                			if (Config.verbose > 1) {
                				System.out.println("DEBUG [modifiedOpInTryBB]: found the move src when "
                						+ "tracking the d-flow of <init>: " + opToTrack);
                			}
                			// We can break out of the loop here since we already found the register;
                			//   but for safety, let's further double-check until we found the new!
                		}
                		if (prevq.getOperator() instanceof Operator.New) {
                			if (opToTrack == null) {
                				// It means we haven't encountered the MOVE yet, it's not supposed to happen!
                				System.out.println("WARN [modifiedOpInTryBB]: when tracking the d-flow"
                						+ "of <init>, found New but no move!" + prevq 
                						+ ", At line: " + prevq.getLineNumber());
                				return null;
                			}
                			if (operandsEquals(opToTrack, Operator.New.getDest(prevq)) == false) {
                				System.out.println("WARN [modifiedOpInTryBB]: when tracking the d-flow"
                						+ "of <init>, the register of New: " + Operator.New.getDest(prevq)
                						+ ", is not the same as the MOVE: " + opToTrack
                						+ ", Quad" + prevq 
                						+ ", At line: " + prevq.getLineNumber());
                				return null;
                			}
                			// If we are here, then everything is good, we found the register that has the
                			//  return value of the <init> and it is stored in opToTrack
                			break;
                		}
                	}
                } // if (Operator.Invoke.getMethod...
            } catch (Exception e) {
            	// getMethod can thrown weird exception, if so, return null below
            }
            if (opToTrack == null) {
            	// Still null, give up!
            	return null;
            }
        }
        
        /* starting from the next quad; follow the data-flow and update regToTrack*/
    	for (int qIdx = exceptionQID + 1; qIdx < bbsize; qIdx++) {
    		Quad nextq = (Quad) trybb.getQuad(qIdx); // the next quad;
    		
    		if (isThisOperandUsed(opToTrack, nextq) == true) { // opToTrack is used by nextq.
    			opToTrack = getModifiedOperand(nextq);
    			if (opToTrack == null) {
    				break; // we're lost...
    			}
    		}
    	}
    	return opToTrack;
    }
 
    

    
    /**
     * opCheckedAfterCatch
     * @param eh: ExceptionHandler, used to find all the basic blocks after this exception handler
     * @param operandToTrack: whether this operandToTrack is checked in an if
     * @return true if this operandToTrack is checked, false otherwise
     */
    boolean opCheckedAfterCatch (ExceptionHandler eh, Operand operandToTrack) {
	    List<BasicBlock> nextbbs = findImmBB(eh);
	    int startingLine = 100000; // printing purpose
	    
	    // nextbbs contains all the basic blocks immediately after the exception handler block.
	    if (nextbbs != null) {
		    for (BasicBlock nextbb : nextbbs) {
		        int nextbb_size = nextbb.size();
		        for (int nqIdx = 0; nqIdx < nextbb_size; nqIdx ++) {
		        	// For each quad here, we need to further track the data-flow of operandToTrack
		        	//   and see if it is compared in an if!
			        Quad quadAfterCatch = (Quad) nextbb.getQuad(nqIdx);
			        int quadLN = quadAfterCatch.getLineNumber();
			        if (quadLN != 0 && startingLine > quadLN) {
			        	startingLine = quadLN; // for code printing purpose
			        }
        			        
			        if (quadAfterCatch.getOperator() instanceof Operator.IntIfCmp) {
			    	    // check if regToTrack is involved. 
			        	Operand src1 = Operator.IntIfCmp.getSrc1(quadAfterCatch);
			        	Operand src2 = Operator.IntIfCmp.getSrc2(quadAfterCatch);
			        	if (operandsEquals (src1, operandToTrack) || 
			        			operandsEquals (src2, operandToTrack)) {
			        		// We found that the operand is checked in an if condition after
			        		// the exception block. 
			        		if (Config.verbose > 1) {
			        			System.out.println("DEBUG [opCheckedAfterCatch]: intifcmp quad: " + quadAfterCatch
					        			+ "At LINE: " + quadLN
					        			+ ", getSrc1: " + src1
					        			+ ", getSrc2: " + src2
					        			+ ", compares the regToTrack: " + operandToTrack);
			        			System.out.println("=== Code printing begins ====");
		        				try {
									printer.printCode(startingLine, quadLN, quadAfterCatch.getMethod().getDeclaringClass().getSourceFileName(), false);
								} catch (IOException e) {
									System.out.println("WARN: Cannot print non-exceptional code: " + quadAfterCatch.getMethod().getDeclaringClass().getSourceFileName() + e);
								}
		        				System.out.println("=== Code printing ends ====");
			        		}
			        		return true;
			        	}
			        } else {
			        	if (isThisOperandUsed(operandToTrack, quadAfterCatch) == true) {
			        		// Could be getField or getStatick
			        		operandToTrack = getModifiedOperand (quadAfterCatch); 
			        		if (operandToTrack == null) {
			        			// dataflow is lost.. we are done...
			        			if (Config.verbose > 1) {
				        			System.out.println("DEBUG [opCheckedAfterCatch]: dataflow is lost for opToTrack: " + operandToTrack
				        					+ ", at quad: " + quadAfterCatch
						        			+ ", At LINE: " + quadLN);
			        			}
			        			return false;
			        		}
			        	}
			        }
		        }
		    } // for (BasicBlock nextbb: 
	    } // if (nextbbs != null
	    return false; // not compared. not a false positive.
    }
    
    boolean handledByControlFlow (BasicBlock trybb) {
        Quad lastQuad = trybb.getLastQuad();
        Operator op = lastQuad.getOperator();
        if (op instanceof Operator.Return) {
        	return true;
        }
        return false;
    }    
    
    /**
     * handledByVarValue: returns true if the exception is handled by checking the return value.
     * Check for the following conditions:
     *  1.  the exception-throwing instruction is supposed to modify a var (if no exception). 
     *       e.g., a method call assigns its return value to a var;
     *  2.a if this var is not a class field and this value is checked in the bb immediately
     *      after the catch; (HBase warning 18)
     *  2.b if this var is a class field (also identify this.table, warning 49 in hbase) -> true
     * And returns true when (1) & (2.a | 2.b) is true. 
     * 
     * @param eh: the exception handler;
     * @param trybb: the try basic block that throws exception
     * @param quadExceptionMap: <qid, exception class> map, indexed by each quad that throws exception
     */
    boolean handledByVarValue (ExceptionHandler eh, BasicBlock trybb, 
    		    HashMap<Integer, List<jq_Class>> quadExceptionsMap) {
    	/* The check contains XX steps: 
    	 *  1. For each quad Q in the trybb, check if it is an invoke and throws an exception E, if so 
    	 *  2. Check if E is handled by eh, if so
    	 *  3. Tracks the use of Q's return value, until it is assigned to a register R
    	 *  4. Find the BB after the exception handler, and see if R is involved in a compare
    	 *      if so, returns true
    	 */
        int bbsize = trybb.size();
        boolean checkedLater = false;
        
        /* For each quad in the trybb that throws an exception. */
        for (Entry<Integer, List<jq_Class>> entry : quadExceptionsMap.entrySet()) {
        	int exceptionQID = entry.getKey();
    		Quad q = (Quad) trybb.getQuad(exceptionQID);
    	    
    		/* The check below is to make sure the exceptions thrown by this exceptionQID is 
    		 * actually handled by this eh. If not, then it's not the business of this eh. */
        	boolean caughtByThisEh = false;
        	for (jq_Class exc : entry.getValue()) {
        		if (eh.mayCatch(exc)) {
        			if (Config.verbose > 1) {
        				System.out.println("DEBUG [handledByVarValue]: Catch block at line: " + eh.getEntry().getLastQuad().getLineNumber()
        						+ ", catching exception: " + eh.getExceptionType()
        						+ "; catches the thrown exception: " + exc);
        			}
    				caughtByThisEh = true;
    				break;
        		}
        	}
        	
        	if (caughtByThisEh == false) {
        		if (Config.verbose > 1) {
    				System.out.println("DEBUG [handledByVarValue]: Catch block at line: " + eh.getEntry().getLastQuad().getLineNumber()
    						+ ", catching exception: " + eh.getExceptionType()
    						+ "; DOES NOT catch the exception by quad: " + q + " at LINE: " + q.getLineNumber());
    			}
        		continue;
        	}
        	
        	/* At this point, we know this quad q throws the exception that is caught by this eh. 
        	 * Next, we need to find the initial operand (variable value) that we need to track its
        	 *   data-flow. 
        	 */
        	Operand operandToTrack = modifiedOpInTryBB(trybb, exceptionQID); // this operand is modified by this quad
        	                                                       // Note that operandToTrack might be null, and
        	                                                       //   it will be handled by opCheckedAfterCatch below!
        	if (Config.verbose > 1) {
				System.out.println("DEBUG [handledByVarValue]: OperandToTrack after try: " + operandToTrack);
			}
        	if (opCheckedAfterCatch(eh, operandToTrack) == true) {
        		checkedLater = true;
        		continue;
        	} else {
        		return false; // for this exceptionQID, the modified var is not checked later, so the
        		              // empty catch block is not a false positive!
        	}
        }  // for (Entry<Integer..
        
        /* If there is an exception-throwing instruction that is handled by this eh, and
         *   its modified value is not further checked, we should have returned false in the
         *   above loop body. 
         * So if we reach here, there could be two possibilities:
         *   1. all the exception-throwing instructions are false positives, or
         *   2. this handler block doesn't handle any relevant exceptions! 
         * which case it is can be found out by checking the value of checkedLater. 
         **/
        if (checkedLater == true) {
            return true; // case 1, false positive!
        }
        // case 2: in this case, we return false, which will cause simplex to report a warning. 
        System.out.println("INFO: this empty exception handling block doesn't catch any relevant"
        		+ "exceptions! ");
        return false;  
     }
            
 
    /* checkEmptyHandler: returns true if the handler is empty and fails all the false-positive tests. 
     * eh - the Exception Handler to be checked;
     * trybb - the basic block that throws the exception. */
    boolean checkEmptyHandler (ExceptionHandler eh, BasicBlock trybb, 
    		     HashMap<Integer, List<jq_Class>> quadExceptionsMap) {
        int handlerBBCount = 0;
        /* Next: check if this handler is empty! */
        hasLog = false;
        endingLine = 0;
        
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

        /* OK, at this point, this eh is empty. We further need to check for two things:
         * 1). maybe the exception is handled by variable value checking in the bb after the catch block;
         * 2). maybe the try block will return, break, or continue at the end. */
        if (handledByVarValue(eh, trybb, quadExceptionsMap) == true) {
        	if (Config.verbose > 0) {
                System.out.println("INFO: [IMPORTANT] Catch block is empty, but the exception is handled by checking"
        			+ "variable values in a later block: FILE: " 
            		+ trybb.getLastQuad().getMethod().getDeclaringClass().getSourceFileName()
            		+ ", try block line: " + trybb.getLastQuad().getLineNumber());
        	}
            return false;
        }
        if (handledByControlFlow(trybb) == true) {
        	if (Config.verbose > 0) {
                System.out.println("INFO: [IMPORTANT] Catch block is empty, but the exception is handled by control"
        			+ "flow change at the end of the try block. FILE: " 
            		+ trybb.getLastQuad().getMethod().getDeclaringClass().getSourceFileName()
            		+ ", try block line: " + trybb.getLastQuad().getLineNumber());
        	}
            return false;
        }
        if (Config.verbose > 1) {
            System.out.println("DEBUG: is empty handler!");
        }
        return true;
    }

    public void run() {
        int bugID = 1; // Unique ID for each bug.
        totalCatches = 0; // Statistic purpose: count the total number of catch blocks

        Program program = Program.g();
        HashMap<String, Integer> warningMap = new HashMap<String, Integer>();

        printer = new CodePrinting();

        for (jq_Method m : program.getMethods()) {
            if (!m.isAbstract()) {
                if (Config.verbose > 0) { System.out.println("Method found: " + m); }
                String srcFile = m.getDeclaringClass().getSourceFileName();

                ControlFlowGraph cfg = null;
                try {
                    cfg = m.getCFG();
                } catch (Throwable e) {
                    // We need throwable here because "java.lang.ClassFormatError" can be thrown
                    // Handled later
                }
                if (cfg == null) {
                    // This is not expected...
                    System.out.println("INFO: Method: " + m + " does not have a CFG...");
                    continue;
                }

                for (BasicBlock bb : cfg.reversePostOrder()) {
                    boolean throws_exception = false; // Does this bb throw exception or not?
                    startingLine = 100000;
                    
                    // key: quad ID, value: list of exceptions it throws
                    HashMap<Integer, List<jq_Class>> quadExceptionsMap = 
                    		new HashMap<Integer, List<jq_Class>>();

                    // key: exception, value: list of quad IDs that throws it
                    HashMap<jq_Class, List<Integer>> exceptionQuadsMap = 
                    		new HashMap<jq_Class, List<Integer>>();

                    /* Every basic block... */
                    if (Config.verbose > 1) { System.out.println("  DEBUG: Basic Block: " + bb); }

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
                            
                            if (!quadExceptionsMap.containsKey(i)) {
                            	quadExceptionsMap.put(i, new ArrayList<jq_Class>());
                            } 
                        	quadExceptionsMap.get(i).add(exc);
                        	
                        	if (!exceptionQuadsMap.containsKey(exc)) {
                        		exceptionQuadsMap.put(exc,  new ArrayList<Integer>());
                        	}
                        	exceptionQuadsMap.get(exc).add(i);
                        	
                            //if (Config.verbose > 0){
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

                        if (checkEmptyHandler(eh, bb, quadExceptionsMap) == true){
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
        System.out.println("==== Total catch blocks analyzed: " + totalCatches + " =========");
    } // run
} // class

	
/* regCheckedAfterCatch: returns true if the variable (register) is further checked in an
 * if branch in the BB immediately after the exception handling block. */
/* boolean regCheckedAfterCatch (ExceptionHandler eh, BasicBlock trybb, 
		Operand operandToTrack, boolean isField) {
    List<BasicBlock> nextbbs = findImmBB(eh, trybb);
    int startingLine = 100000; // printing purpose
    
    RegisterOperand regToTrack = null;
    if (isField == false) {
    	regToTrack = (RegisterOperand) operandToTrack;
    }
    
    if (nextbbs != null) {
	    for (BasicBlock nextbb : nextbbs) {
	        int nextbb_size = nextbb.size();
	        for (int nqIdx = 0; nqIdx < nextbb_size; nqIdx ++) {
		        Quad quadAfterCatch = (Quad) nextbb.getQuad(nqIdx);
		        int quadLN = quadAfterCatch.getLineNumber();
		        if (quadLN != 0 && startingLine > quadLN) {
		        	startingLine = quadLN; // for code printing purpose
		        }

		        // search for getfield
		        if (isField == true && 
		        		quadAfterCatch.getOperator() instanceof Operator.Getfield) {
		        	if (operandsEquals (operandToTrack, Operator.Getfield.getField(quadAfterCatch)) == true) {
		        		// We found the getfield
		        		regToTrack = Operator.Getfield.getDest(quadAfterCatch);
		        		if (Config.verbose > 1) {
		        			System.out.println("DEBUG [regCheckedAfterCatch]: we found the getfield: "
		        					+ "Quad: " + quadAfterCatch + ", AT LINE: " + quadLN
		        					+ ", Operator field: " + operandToTrack + ", dst register: " + regToTrack);
		        		}
		        	}
		        }			        
		        
		        if (quadAfterCatch.getOperator() instanceof Operator.IntIfCmp) {
		    	    // check if regToTrack is involved. 
		        	Operand src1 = Operator.IntIfCmp.getSrc1(quadAfterCatch);
		        	Operand src2 = Operator.IntIfCmp.getSrc2(quadAfterCatch);
		        	if (operandsEquals (src1, regToTrack) || 
		        			operandsEquals (src2, regToTrack)) {
		        		// We found that the operand is checked in an if condition after
		        		// the exception block. 
		        		if (Config.verbose > 1) {
		        			System.out.println("DEBUG [regCheckedAfterCatch]: intifcmp quad: " + quadAfterCatch
				        			+ "At LINE: " + quadLN
				        			+ ", getSrc1: " + src1
				        			+ ", getSrc2: " + src2
				        			+ ", compares the regToTrack: " + operandToTrack);
		        			System.out.println("=== Code printing begins ====");
	        				try {
								printer.printCode(startingLine, quadLN, quadAfterCatch.getMethod().getDeclaringClass().getSourceFileName(), false);
							} catch (IOException e) {
								System.out.println("WARN: Cannot print non-exceptional code: " + quadAfterCatch.getMethod().getDeclaringClass().getSourceFileName() + e);
							}
	        				System.out.println("=== Code printing ends ====");
		        		}
		        		return true;
		        	}
		        }
	        }
	    } // for (BasicBlock nextbb: 
    } // if (nextbbs != null
    return false; // not compared. not a false positive.
} */

/*			
	
	if (nextq.getOperator() instanceof Operator.Putfield) {
		// The return value is assigned to a class field, so could be false positive.
		if (Config.verbose > 1) {
			System.out.println("DEBUG: Quad: " + nextq + " is a putfield quad, therefore could be a FP");
		}
		checkedLater = true;
		break; // break out of the iteration of each quad in the try bb
	}    			
	regToTrack = getModifiedReg(nextq);
	if (regToTrack == null) {
        System.out.println("INFO [handledByVarValue]: Quad: " + nextq + ", at LINE: " + q.getLineNumber()
                + "has no modified registers! Data-flow is lost!"); 
        return false; // The data-flow trace is lost, so this is NOT a false positive
	}
	if (Config.verbose > 1) {
		System.out.println("DEBUG [handledByVarValue]: Quad: " + nextq + " modified register: " + regToTrack);
	}
	if (regToTrack.getRegister().isTemp() == false) {
		// Needs to further track this register. 
		if (Config.verbose > 1) {
			System.out.println("DEBUG [handledByVarValue]: Register:  " + regToTrack
					+ "; isGuard? " + regToTrack.getRegister().isGuard()
					+ "; isTemp? " + regToTrack.getRegister().isTemp()
					+ "; isSSA? " + regToTrack.getRegister().isSSA()
					+ "; isPhysical? " + regToTrack.getRegister().isSSA()
					+ "; getType: " + regToTrack.getRegister());
		}
		if (regCheckedAfterCatch(eh, trybb, regToTrack, false) == true) {
			// Then possibly an FP; 
			checkedLater = true;
			break; // break out of the iteration of each quad in the try bb
		}
		// This register is not further checked! Definitely not an FP!
		return false; // not false positive. 

	}
}
} // for (int qIdx = exceptionQID 		

    	
    	
    	
    	boolean isThisInitChecked = false;
    	// First, let's see if the return value of the <init> is assigned to a field;
    	// We use a heuristic: in the same line, there should be a putfield with the
    	//  same line number:
    	// This for loop is to iterate through each quad to find the putfield instruction
    	for (int qIdx = exceptionQID + 1; qIdx < bbsize; qIdx++) {
    		Quad nextq = (Quad) trybb.getQuad(qIdx); // the next quad;
            if (nextq.getLineNumber() != q.getLineNumber()) {
            	// search is over, we haven't found putfield! this will return false below!
            	break;
            }
            if (nextq.getOperator() instanceof Operator.Putfield) {
            	Operand dstField = Operator.Putfield.getField(nextq);
            	
            	if (Config.verbose > 1) {
            		System.out.println("DEBUG [handledByVarValue]: putfield field: " + dstField);
            	}
				if (regCheckedAfterCatch(eh, trybb, dstField, true) == true) {
					// The field is further checked after the catch block, 
					// It is an FP. But we cannot return true right now, because there may
					// be other quads may throw exceptions that are not checked!
					if (Config.verbose > 1) {
                		System.out.println("DEBUG [handledByVarValue]: field: " + dstField
                				+ " is checked after the catch block!");
                		isThisInitChecked = true;
                	}
					break;
				}
            }
    	}
    	
    	if (isThisInitChecked == true) {
    		// this qid is a false positive, we continue the outer loop to check other qids
    		checkedLater = true;
    	    continue;
    	}
    	// hasPutField == false, can't even find a putfiled,  
    	// then not a false positive! return false below
     }
} catch (Exception e) {
	// getMethod can thrown weird exception, if so, return false (not false positive).
}
return false; // not a false positive!
}

}

}

} */