package chord.program.reflect;

import java.io.IOException;
import chord.runtime.BasicEventHandler;
import chord.runtime.TraceEventHandler;

/*
 * Event handler for dynamic analysis for resolving reflection.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class ReflectEventHandler extends TraceEventHandler {
	public static void clsForNameEvent(String q, String c) {
		synchronized (BasicEventHandler.class) {
			if (trace) {
				trace = false;
				try {
					buffer.putByte(ReflectEventKind.CLS_FOR_NAME_CALL);
					buffer.putString(q);
					buffer.putString(c);
				} catch (IOException ex) { throw new RuntimeException(ex); }
				trace = true;
			}
		}
	}
	public static void objNewInstEvent(String q, String c) {
		synchronized (BasicEventHandler.class) {
			if (trace) {
				trace = false;
				try {
					buffer.putByte(ReflectEventKind.OBJ_NEW_INST_CALL);
					buffer.putString(q);
					buffer.putString(c);
				} catch (IOException ex) { throw new RuntimeException(ex); }
				trace = true;
			}
		}
	}
	public static void conNewInstEvent(String q, String c) {
		synchronized (BasicEventHandler.class) {
			if (trace) {
				trace = false;
				try {
					buffer.putByte(ReflectEventKind.CON_NEW_INST_CALL);
					buffer.putString(q);
					buffer.putString(c);
				} catch (IOException ex) { throw new RuntimeException(ex); }
				trace = true;
			}
		}
	}
	public static void aryNewInstEvent(String q, String c) {
		synchronized (BasicEventHandler.class) {
			if (trace) {
				trace = false;
				try {
					buffer.putByte(ReflectEventKind.ARY_NEW_INST_CALL);
					buffer.putString(q);
					buffer.putString(c + "[]");
				} catch (IOException ex) { throw new RuntimeException(ex); }
				trace = true;
			}
		}
	}
}
