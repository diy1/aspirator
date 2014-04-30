package chord.project.analyses;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

import CnCHJ.api.ItemCollection;

import chord.project.Messages;
import chord.bddbddb.RelSign;
import chord.bddbddb.Solver;
import chord.project.ICtrlCollection;
import chord.project.IDataCollection;
import chord.project.IStepCollection;
import chord.project.ModernProject;
import chord.util.Utils;

import gnu.trove.list.array.TIntArrayList;

/**
 * Generic implementation of a Dlog task (a program analysis expressed in Datalog and
 * solved using BDD-based solver <a href="http://bddbddb.sourceforge.net/">bddbddb</a>).
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class DlogAnalysis extends JavaAnalysis {
    // absolute filename of the datalog program
    private String fileName;
    private Set<String> majorDomNames;
    private Map<String, RelSign> consumedRels;
    private Map<String, RelSign> producedRels;
    private String dlogName;
    private boolean hasNoErrors = true;
    // number of line currently being parsed in the datalog program
    private int lineNum;
    // bdd ordering of all domains specified using .bddvarorder in the datalog program
    private String order;
    // ordered list of all domains specified using .bddvarorder in the datalog program
    private List<String> minorDomNames;
    // may return null
    /**
     * Provides the name of this Datalog analysis.
     * It is specified via a line of the form "# name=..." in the file containing the analysis.
     * 
     * @return    The name of this Datalog analysis.
     */
    public String getDlogName() {
        return dlogName;
    }
    /**
     * Provides the file containing this Datalog analysis.
     * 
     * @return    The file containing this Datalog analysis.
     */
    public String getFileName() {
        return fileName.toString();
    }
    /**
     * Parses the Datalog analysis in the specified file.
     * 
     * @param    fileName A file containing a Datalog analysis.
     * 
     * @return    true iff the Datalog analysis parses successfully.
     */
    public boolean parse(String fileName) {
        assert (this.fileName == null);
        this.fileName = fileName;
        majorDomNames = new HashSet<String>();
        consumedRels = new HashMap<String, RelSign>();
        producedRels = new HashMap<String, RelSign>();
        minorDomNames = new ArrayList<String>();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(fileName));
        } catch (IOException ex) {
            Messages.log(ex.getMessage());
            return false;
        }
        Pattern p = Pattern.compile("(\\w)+\\((\\w)+:(\\w)+(,(\\w)+:(\\w)+)*\\)((input)|(output))");
        for (lineNum = 1; true; lineNum++) {
            String s;
            try {
                s = in.readLine();
            } catch (IOException ex) {
                Messages.log(ex.getMessage());
                return false;
            }
            if (s == null)
                break;
            if (s.startsWith("#")) {
                if (s.startsWith("# name=")) {
                    if (dlogName == null)
                        dlogName = s.trim().substring(7);
                    else
                        error("Name redeclared via # name=...");
                }
                continue;
            }
            int k = s.indexOf('#');
            if (k != -1) s = s.substring(0, k);
            s = s.trim();
            if (s.length() == 0)
                 continue;
            // strip all whitespaces from line
            StringBuffer t = new StringBuffer(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (!Character.isWhitespace(c))
                    t.append(c);
            }
            s = t.toString();
            if (s.startsWith(".bddvarorder")) {
                if (order != null) {
                    error(".bddvarorder redefined.");
                    continue;
                }
                order = s.substring(12);
                String[] a = order.split("_|x");
                for (String minorDomName : a) {
                    if (minorDomNames.contains(minorDomName)) {
                        error("Domain name '" + minorDomName + "' occurs multiple times in .bddvarorder; " +
                            "considering first occurrence.");
                    } else {
                        minorDomNames.add(minorDomName);
                        String majorDomName = Utils.trimNumSuffix(minorDomName);
                        majorDomNames.add(majorDomName);
                    }
                }
                continue;
            }
            Matcher m = p.matcher(s);
            if (!m.matches())
                continue;
            if (order == null) {
                error(".bddvarorder not defined before first relation declared");
                return false;
            }
            int i = s.indexOf('(');
            String relName = s.substring(0, i);
            if (consumedRels.containsKey(relName)) {
                error("Relation '" + relName + "' redeclared");
                continue;
            }
            if (producedRels.containsKey(relName)) {
                error("Relation '" + relName + "' redeclared");
                continue;
            }
            s = s.substring(i + 1);
            boolean done = false;
            boolean ignore = false;
            List<String> relMinorDomNames = new ArrayList<String>();
            List<String> relMajorDomNames = new ArrayList<String>();
            TIntArrayList indices = new TIntArrayList();
            while (!done) {
                i = s.indexOf(':');
                assert (i != -1);
                s = s.substring(i + 1);
                i = s.indexOf(',');
                if (i == -1) {
                    i = s.indexOf(')');
                    assert (i != -1);
                    done = true;
                }
                String domName = s.substring(0, i);
                String minorDomName;
                String majorDomName;
                int index;
                if (!Character.isDigit(domName.charAt(i - 1))) {
                    majorDomName = domName;
                    index = 0;
                    int num = indices.size();
                    while (true) {
                        int j = 0;
                        for (String majorDomName2 : relMajorDomNames) {
                            if (majorDomName2.equals(majorDomName) &&
                                    indices.get(j) == index) {
                                index++;
                                break;
                            }
                            j++;
                        }
                        if (j == num)
                            break;
                    }
                    minorDomName = majorDomName + Integer.toString(index);
                } else {
                    minorDomName = domName;
                    int j = i - 1;
                    while (true) {
                        char c = domName.charAt(j);
                        if (Character.isDigit(c))
                            j--;
                        else
                            break;
                    }
                    majorDomName = domName.substring(0, j + 1);
                    index = Integer.parseInt(domName.substring(j + 1, i));
                }
                if (relMinorDomNames.contains(minorDomName)) {
                    error("Domain name '" + minorDomName + "' occurs multiple times in declaration of " +
                        "relation '" + relName + "'");
                    ignore = true;
                } else if (!minorDomNames.contains(minorDomName)) {
                    error("Domain name '" + domName + "' in declaration of relation '" + relName +
                        "' does not occur in .bddvarorder");
                    ignore = true;
                } else {
                    relMinorDomNames.add(minorDomName);
                    relMajorDomNames.add(majorDomName);
                    indices.add(index);
                }
                s = s.substring(i + 1);
            }
            if (ignore)
                continue;
            int numDoms = relMinorDomNames.size();
            String[] domNames = new String[numDoms];
            String domOrder = getSubOrder(relMinorDomNames);
            for (int j = 0; j < numDoms; j++)
                domNames[j] = relMinorDomNames.get(j);
            Map<String, RelSign> map = null;
            if (s.equals("input"))
                map = consumedRels;
            else if (s.equals("output"))
                map = producedRels;
            else
                assert false; 
            RelSign relSign;
            try {
                relSign = new RelSign(domNames, domOrder);
            } catch (RuntimeException ex) {
                error(ex.getMessage());
                continue;
            }
            map.put(relName, relSign);
        }
        return hasNoErrors;
    }
    private String getSubOrder(List<String> relMinorDomNames) {
        int orderLen = order.length();
        String subOrder = null;
        char lastSep = ' ';
        int i = 0;
        for (String domName : minorDomNames) {
            i += domName.length();
            if (relMinorDomNames.contains(domName)) {
                if (subOrder == null)
                    subOrder = domName;
                else
                    subOrder = subOrder + lastSep + domName;
                if (i != orderLen)
                    lastSep = order.charAt(i);
            } else {
                if (i != orderLen && order.charAt(i) == '_')
                    lastSep = '_';
            }
            i++;
        }
        return subOrder;
    }
    private void error(String errMsg) {
        Messages.log("ERROR: DlogAnalysis: " + fileName + ": line " + lineNum + ": " + errMsg);
        hasNoErrors = false;
    }
    /**
     * Executes this Datalog analysis.
     */
    public void run() {
        Solver.run(fileName.toString());
    }
    public void run(Object ctrl, IStepCollection sc) {
        ModernProject p = ModernProject.g();
        Object[] consumes = p.runPrologue(ctrl, sc);
        List<ProgramDom> allDoms = new ArrayList<ProgramDom>();
        for (Object o : consumes) {
            if (o instanceof ProgramDom)
                allDoms.add((ProgramDom) o);
        }
        run();
        List<IDataCollection> pdcList = sc.getProducedDataCollections();
        for (IDataCollection pdc : pdcList) {
            ItemCollection pic = pdc.getItemCollection();
            String relName = pdc.getName();
            RelSign sign = p.getSign(relName);
            String[] domNames = sign.getDomNames();
            ProgramDom[] doms = new ProgramDom[domNames.length];
            for (int i = 0; i < domNames.length; i++) {
                String domName = Utils.trimNumSuffix(domNames[i]);
                for (ProgramDom dom : allDoms) {
                    if (dom.getName().equals(domName)) {
                        doms[i] = dom;
                        break;
                    }
                }
                assert (doms[i] != null);
            }
            ProgramRel rel = new ProgramRel();
            rel.setName(relName);
            rel.setSign(sign);
            rel.setDoms(doms);
            pic.Put(ctrl, rel);
        }
    }
    /**
     * Provides the names of all domains of relations consumed/produced by this Datalog analysis.
     * 
     * @return    The names of all domains of relations consumed/produced by this Datalog analysis.
     */
    public Set<String> getDomNames() {
        return majorDomNames;
    }
    /**
     * Provides the names and signatures of all relations consumed by this Datalog analysis.
     * 
     * @return    The names and signatures of all relations consumed by this Datalog analysis.
     */
    public Map<String, RelSign> getConsumedRels() {
        return consumedRels;
    }
    /**
     * Provides the names and signatures of all relations produced by this Datalog analysis.
     * 
     * @return    The names and signatures of all relations produced by this Datalog analysis.
     */
    public Map<String, RelSign> getProducedRels() {
        return producedRels;
    }
}
