package chord.analyses.typestate;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import chord.util.Utils;
import chord.project.Messages;

/**
 * Parser for type-state spec.  Start state can be anything the user specifies.
 * There is a designated implicitly defined error state named "Error".
 *
 * Grammar of spec file: 
 *
 * <ClassName> <StartState>
 * Updates
 * <MethodName>-<IncomingState>-<OutgoingState>
 * ...
 * Asserts
 * <MethodName>-<LegalState1>-<LegalState2>-...<LegalStateN>
 * ...
 *
 * Example: 
 *
 * test.LockClass UnLocked
 * Updates
 * Lock-UnLocked-Locked
 * UnLock-Locked-UnLocked
 * Asserts
 * Lock-UnLocked
 * UnLock-Locked
 * 
 * @author machiry
 */
public class TypeStateParser {
    protected static final String methodUpdatesStart = "Updates";
    protected static final String methodAssertsStart = "Asserts";
    protected static final String delimiter = "-";
    protected static final String commentPrefix = "//";
    protected static final String errorStateName = "Error";
    protected static final String initialFormat = "<class name> <start state>";
    protected static final String updatesFormat = "<method name>-<incoming state>-<outgoing state>";
    protected static final String assertsFormat = "<method name>-<legal state1>-<legal state2>-...";
    /**
     * Parse the type state spec contained in the specified file.
     * 
     * @param fileName
     * @return true if parsing is successful and false otherwise.
     */
    public static TypeStateSpec parse(String fileName) {
        List<String> lines = Utils.readFileToList(fileName);
        TypeStateSpec sp = new TypeStateSpec();
        boolean inUpdates = false;
        boolean inAsserts = false;
        boolean parsingError = false;
        for (int i = 0; !parsingError && i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith(commentPrefix) || line.equals("")) {
                ;  // do nothing
            } else if (line.equals(methodUpdatesStart)) {
                inUpdates = true;
                inAsserts = false;
            } else if (line.equals(methodAssertsStart)) {
                inAsserts = true;
                inUpdates = false;
            } else if (inUpdates) {
                String a[] = line.split(delimiter);
                if (a.length != 3)
                    parsingError = true;
                else {
                    a[0] = a[0].trim();
                    a[1] = a[1].trim();
                    a[2] = a[2].trim();
                    if (a[0].length() <= 0 || a[1].length() <= 0 || a[2].length() <= 0) {
                        parsingError = true;
                    } else {
                        sp.addState(a[1]);
                        sp.addState(a[2]);
                        sp.addUpdate(a[0], sp.getState(a[1]), sp.getState(a[2]));
                    }
                }
                if (parsingError)
                    error(fileName, line, updatesFormat);
            } else if (inAsserts) {
                String a[] = line.split(delimiter);
                if (a.length < 2)
                    parsingError = true;
                else {
                    a[0] = a[0].trim();
                    if (a[0].length() <= 0)
                        parsingError = true;
                    else {
                        for (int j = 1; j < a.length; j++) {
                            a[j] = a[j].trim();
                            if (a[j].length() <= 0) {
                                parsingError = true;
                                break;
                            }
                            sp.addState(a[j]);
                            sp.addAssert(a[0], sp.getState(a[j]));
                        }
                    }
                }
                if (parsingError)
                    error(fileName, line, assertsFormat);
            } else if (sp.getStartState() == null) {
                String[] a = line.split(" ");
                if (a.length != 2)
                    parsingError = true;
                else {
                    a[0] = a[0].trim();
                    a[1] = a[1].trim();
                    if (a[0].length() <= 0 || a[1].length() <= 0) {
                        parsingError = true;
                    } else {
                        sp.addState(a[1]);
                        sp.addStartInfo(a[0], sp.getState(a[1]));
                    }
                }
                if (parsingError)
                    error(fileName, line, initialFormat);
            } else {
                error(fileName, line, null);
                parsingError = true;
            }
        }
        if (!parsingError && sp.getStartState() != null) {
            sp.addState(errorStateName);
            sp.addErrorState(sp.getState(errorStateName));
            return sp;
        }
        return null;
    }

    protected static void error(String fileName, String line, String expected) {
        String s = "Invalid line in typestate spec in file " + fileName + ": \"" + line + "\".";
        if (expected != null) s += " Expected: \"" + expected + "\".";
        Messages.log(s);
    }
}
