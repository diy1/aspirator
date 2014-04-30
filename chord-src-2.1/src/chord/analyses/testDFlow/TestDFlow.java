package chord.analyses.testDFlow;

import chord.program.Program;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.Operand.RegisterOperand;

//import joeq.Util.Templates.ListIterator;

import chord.project.analyses.JavaAnalysis;
import chord.project.Chord;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
  * This analysis is to test the Dataflow APIs. It will calculate the path-insensitive 
  *  definition point of a register. 
  * 
  * Input: assume there is a input file in the workingDir named "register.idx". 
  *   This file should be in the following format:
  *   [method name, Register]
  *   Note that we do not need a "class name" element because the method name already
  *   contains the classname as well. 
  *   Also, we do not need a basic block ID because 
  *  
  * Output: the quad that defines this register (and its source-level info).
  * 
  *  @author Ding Yuan
  */
@Chord(
   name = "dflow-test-java",
   consumes = {} // This analysis consumes: 
)

public class TestDFlow extends JavaAnalysis {
	String methodname;
	boolean isTemp;
	int rIdx;
	
	void parseInput() {
		Pattern pattern = Pattern.compile("\\[([^,]+), (\\w)(\\d+)\\]");
		try {
		    File file = new File("register.idx");
		    
		    FileReader fileReader = new FileReader(file);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			
		    String line;
		    while ((line = bufferedReader.readLine()) != null) {
				// System.out.println("Read line: " + line);

				Matcher m = pattern.matcher(line);
				while (m.find()) {
					methodname = m.group(1);
					String registerSymbol = m.group(2);
					if (registerSymbol.equals("R")) {
						isTemp = false;
					} else if (registerSymbol.equals("T")) {
						isTemp = true;
					} else {
						System.out.println("Cannot parse register symbol: " + registerSymbol + " in " + line);
						System.exit(1);
					}
					rIdx = Integer.parseInt(m.group(3));
					dflow_analysis();
				}
				
		    }
		    bufferedReader.close();
		} catch (IOException e) {
			if (e instanceof FileNotFoundException) {
				System.out.println("Input file not found: Please create an input file named \"register.idx\" in the working dir");
				System.out.println("File format: [methodname, R#]");
				System.exit(1); 
			}
		}
	}
	
	void dflow_analysis () {
		System.out.println("DEBUG: After parsing input: methodname = " + methodname +
				             "; isTemp: " + isTemp +
				             "; rIdx: " + rIdx);
		
		// 1. let's get the methods first.
		Program program = Program.g();

		for (jq_Method m : program.getMethods()) {
		    if (!m.isAbstract()) {
		    	if (m.toString().equals(methodname)) {
		    		//System.out.println("methodname matches: " + m);
		    	
		    		ControlFlowGraph cfg = m.getCFG();
		    		//ListIterator.BasicBlock it = cfg.reversePostOrderIterator();
		    		for (BasicBlock bb : cfg.reversePostOrder()) {
		    			for (int i = 0; i < bb.size(); i++) {
		    				Quad q = bb.getQuad(i);
		    				for (RegisterOperand def : q.getDefinedRegisters()) {
		    					if (def.getRegister().getNumber() == rIdx && def.getRegister().isTemp() == isTemp) {
				    				System.out.print("Found the definition quad: " + q + ", for register: ");
				    				if (isTemp) {
				    					System.out.print("T");
				    				}
				    				else {
				    					System.out.print("R");
				    				}
				    				System.out.println(rIdx);
				    				
				    				System.out.println("  Basic block: " + bb.getID());
				    				
				    				System.out.println("  Source location: " + 
				    				   m.getDeclaringClass().getSourceFileName() + 
				    				   ":" + q.getLineNumber());
				    				System.out.println();
		    					}
		    				}		    		            		    				
		    			}
		    		}
		    	}
		    }
		}
	}
	
	public void run() {		
		parseInput();
	}
}
