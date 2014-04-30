package chord.analyses.exceptionHandlerBugs;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chord.project.Config;
import chord.util.Utils;

public class CodePrinting {		
	void printCode (int startingLine, int endingLine, String fileName) throws IOException {
		List<String> buffer = new ArrayList<String>();
		
		if ((startingLine > endingLine) || (startingLine <= 0)) {
			System.out.println("ERROR: Invalid line range: " + startingLine +
					"-" + endingLine + ", File: " + fileName);
			return;
		}

		String srcPathName = Config.srcPathName;
		if (srcPathName == null)
           System.out.println("chord.src.path is not defined...");
        String[] srcDirNames = srcPathName.split(Utils.PATH_SEPARATOR);
       
       for (String path : srcDirNames) {
       	String fullpath = path + "/" + fileName;
       	BufferedReader br = null;
       	try {
       		br = new BufferedReader(new FileReader(fullpath));
       		
       		if (Config.verbose > 0) {
       			System.out.println("DEBUG: printCode: " + startingLine + "-" + endingLine
       					+ "@" + fullpath);
       		}
       		System.out.println();
       		
       		int i = 0;
       		String line = null;
       		do {
       			line = br.readLine();
       			buffer.add(line);
       			
       			i++;
       			if (i < startingLine) {
       				continue;
       			}
       			
       			if (i == startingLine) {
       				// Now, search backwards in buffer, until we reach a try   					
   					int tryIdx = 0;
   					boolean foundTry = false;
       				for (int j = buffer.size()-1; j >= 0; j--) {
       					if (buffer.get(j).contains("try")) {
       						tryIdx = j;
       						foundTry = true;
       						break;
       					}
       				}
       				if (foundTry == true) {
       					for (int j = tryIdx; j < buffer.size(); j++) {
       						int linenum = j+1;
       						System.out.println(linenum + ": " + buffer.get(j));
       					}
       				} else {
       					System.out.println("ERROR: cannot find try! " + startingLine + "-" + endingLine
       							+ "@" + fullpath);
       				}
       				// the current line is also printed...
       			}
       			
       			if (i == endingLine) {
   					System.out.println(i + ": " + line);

       				// search for the ending "}"
       				if (line.matches("catch\\s*\\{.*\\}") == false) {
       					// Search for the next "}"
       					while ((line = br.readLine()) != null) {
       						i++;
       						System.out.println(i + ": " + line);
       						if (line.contains("}")) {
       							return;
       						}
       					}
       				}
       				// we are done...
       				return;
       			}
       			System.out.println(i + ": " + line);
       		} while (line != null);    		
       	} catch (FileNotFoundException e) {
       		// ignore
       		if (Config.verbose > 1) {
       			System.out.println("Cannot find file: " + fullpath);
       		}
       		continue;
       	} finally {
       		if (br != null)
       			br.close();
       	}
       }
       System.out.println("INFO: cannot print source info (likely chord.src.path is wrong)");
       return;
	}
	
	public CodePrinting() {
		// do nothing...
	}
}