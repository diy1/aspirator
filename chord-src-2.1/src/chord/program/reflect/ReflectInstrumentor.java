package chord.program.reflect;

import java.util.Map;
import chord.instr.BasicInstrumentor;
import javassist.expr.MethodCall;
import javassist.CannotCompileException;
import javassist.CtConstructor;
import chord.program.MethodElem;

/**
 * Load-time bytecode instrumentor for dynamic analysis for resolving reflection.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class ReflectInstrumentor extends BasicInstrumentor {
	private static final String clsForNameEventStr =
		ReflectEventHandler.class.getName() + ".clsForNameEvent(";
	private static final String objNewInstEventStr =
		ReflectEventHandler.class.getName() + ".objNewInstEvent(";
	private static final String conNewInstEventStr =
		ReflectEventHandler.class.getName() + ".conNewInstEvent(";
	private static final String aryNewInstEventStr =
		ReflectEventHandler.class.getName() + ".aryNewInstEvent(";
	public ReflectInstrumentor(Map<String, String> argsMap) {
		super(argsMap);
	}
	@Override
	public void edit(MethodCall e) {
		String cName = e.getClassName();
		String eventStr = null;
		String cName2 = null;
		if (cName.equals("java.lang.Class"))  {
			String mName = e.getMethodName();
			if (mName.equals("forName")) {
				String mSign = e.getSignature();
				if (mSign.equals("(Ljava/lang/String;)Ljava/lang/Class;")) {
					eventStr = clsForNameEventStr;
					cName2 = "$1";
				}
			} else if (mName.equals("newInstance")) {
				String mSign = e.getSignature();
				if (mSign.equals("()Ljava/lang/Object;")) {
					eventStr = objNewInstEventStr;
					cName2 = "$0.getName()";
				}
			}
		} else if (cName.equals("java.lang.reflect.Constructor")) {
			String mName = e.getMethodName();
			if (mName.equals("newInstance")) {
				String mSign = e.getSignature();
				if (mSign.equals("([Ljava/lang/Object;)Ljava/lang/Object;")) {
					eventStr = conNewInstEventStr;
					cName2 = "$0.getName()";
				}
			}
		} else if (cName.equals("java.lang.reflect.Array")) {
			String mName = e.getMethodName();
			if (mName.equals("newInstance")) {
				String mSign = e.getSignature();
				if (mSign.equals("(Ljava/lang/Class;I)Ljava/lang/Object;")) {
					eventStr = aryNewInstEventStr;
					cName2 = "$1.getName()";
				}
			}
		}
		if (eventStr == null)
			return;
		String mElem = getMethodElem(e);
		String instr = "{ " + eventStr + mElem + "," + cName2 + "); $_ = $proceed($$); }";
		try {
			e.replace(instr);
		} catch (CannotCompileException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}
	private String getMethodElem(MethodCall e) {
		int bci = e.indexOfOriginalBytecode();
		String mName;
		if (currentMethod instanceof CtConstructor) {
			mName = ((CtConstructor) currentMethod).isClassInitializer() ?
				"<clinit>" : "<init>";
		} else {
			mName = currentMethod.getName();
		}
		String mSign = currentMethod.getSignature();
		String cName = currentClass.getName();
		MethodElem me = new MethodElem(bci, mName, mSign, cName);
		return "\"" + me.toString() + "\"";
	}
}

