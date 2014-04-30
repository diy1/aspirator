package chord.program.reflect;

import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import chord.util.tuple.object.Pair;
import chord.util.ByteBufferedFile;
import chord.project.analyses.BasicDynamicAnalysis;
import chord.project.Chord;
import chord.project.Config;

/**
 * Dynamic analysis for resolving reflection.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class DynamicReflectResolver extends BasicDynamicAnalysis {
	private final List<Pair<String, List<String>>> resolvedClsForNameSites =
		new ArrayList<Pair<String, List<String>>>();
	private final List<Pair<String, List<String>>> resolvedObjNewInstSites =
		new ArrayList<Pair<String, List<String>>>();
	private final List<Pair<String, List<String>>> resolvedConNewInstSites =
		new ArrayList<Pair<String, List<String>>>();
	private final List<Pair<String, List<String>>> resolvedAryNewInstSites =
		new ArrayList<Pair<String, List<String>>>();
	private final boolean verbose = (Config.verbose >= 2);

	public List<Pair<String, List<String>>> getResolvedClsForNameSites() {
		return resolvedClsForNameSites;
	}

	public List<Pair<String, List<String>>> getResolvedObjNewInstSites() {
		return resolvedObjNewInstSites;
	}

	public List<Pair<String, List<String>>> getResolvedConNewInstSites() {
		return resolvedConNewInstSites;
	}

	public List<Pair<String, List<String>>> getResolvedAryNewInstSites() {
		return resolvedAryNewInstSites;
	}

	@Override
	public String getInstrKind() {
		return "online";
	}

	@Override
	public Class getInstrumentorClass() {
		return ReflectInstrumentor.class;
	}

	@Override
	public Class getEventHandlerClass() {
		return ReflectEventHandler.class;
	}

	@Override
	public void handleEvent(ByteBufferedFile buffer) throws IOException {
		byte opcode = buffer.getByte();
		switch (opcode) {
		case ReflectEventKind.CLS_FOR_NAME_CALL:
		{
			String q = buffer.getString();
			String c = buffer.getString();
			if (verbose) System.out.println("CLS_FOR_NAME: " + q + " " + c);
			add(resolvedClsForNameSites, q, c);
			break;
		}
		case ReflectEventKind.OBJ_NEW_INST_CALL:
		{
			String q = buffer.getString();
			String c = buffer.getString();
			if (verbose) System.out.println("OBJ_NEW_INST: " + q + " " + c);
			add(resolvedObjNewInstSites, q, c);
			break;
		}
		case ReflectEventKind.CON_NEW_INST_CALL:
		{
			String q = buffer.getString();
			String c = buffer.getString();
			if (verbose) System.out.println("CON_NEW_INST: " + q + " " + c);
			add(resolvedConNewInstSites, q, c);
			break;
		}
		case ReflectEventKind.ARY_NEW_INST_CALL:
		{
			String q = buffer.getString();
			String c = buffer.getString();
			if (verbose) System.out.println("ARY_NEW_INST: " + q + " " + c);
			add(resolvedAryNewInstSites, q, c);
			break;
		}
		default:
			throw new RuntimeException("Unknown opcode: " + opcode);
		}
	}
	private static void add(List<Pair<String, List<String>>> l, String q, String c) {
		for (Pair<String, List<String>> p : l) {
			if (p.val0.equals(q)) {
				List<String> s = p.val1;
				if (!s.contains(c))
					s.add(c);
				return;
			}
		}
		List<String> s = new ArrayList<String>(2);
		s.add(c);
		l.add(new Pair<String, List<String>>(q, s));
	}
}

