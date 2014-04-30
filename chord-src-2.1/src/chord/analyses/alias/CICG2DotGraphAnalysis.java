package chord.analyses.alias;

import java.io.PrintWriter;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;

import chord.analyses.method.DomM;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;

/**
 * Converting a context-insensitive call graph to a dot-graph.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(name="cicg2dot-java")
public class CICG2DotGraphAnalysis extends JavaAnalysis {
    private DomM domM;
    public void run() {
        ClassicProject project = ClassicProject.g();
        CICGAnalysis analysis = (CICGAnalysis) project.runTask("cicg-java");
        ICICG cicg = analysis.getCallGraph();
        domM = (DomM) project.getTrgt("M");

        PrintWriter out = OutDirUtils.newPrintWriter("cicg.dot");
        out.println("digraph G {");
        for (jq_Method m1 : cicg.getNodes()) {
            String id1 = id(m1);
            out.println("\t" + id1 + " [label=\"" + str(m1) + "\"];");
            for (jq_Method m2 : cicg.getSuccs(m1)) {
                String id2 = id(m2);
                Set<Quad> labels = cicg.getLabels(m1, m2);
                for (Quad q : labels) {
                    String el = q.toJavaLocStr();
                    out.println("\t" + id1 + " -> " + id2 + " [label=\"" + el + "\"];");
                }
            }
        }
        out.println("}");
        out.close();

        analysis.free();
    }
    private String id(jq_Method m) {
        return "m" + domM.indexOf(m);
    }
    private static String str(jq_Method m) {
        jq_Class c = m.getDeclaringClass();
        String desc = m.getDesc().toString();
        String args = desc.substring(1, desc.indexOf(')'));
        String sign = "(" + Program.typesToStr(args) + ")";
        return c.getName() + "." + m.getName().toString() +  sign;
    }
}

