package chord.analyses.method;

import joeq.Class.jq_Method;
import chord.program.Program;
import chord.project.Chord;
import chord.project.Config;
import chord.project.analyses.ProgramRel;
import chord.util.IndexSet;
import chord.util.Utils;

/**
 * @author Ariel Rabkin (asrabkin@gmail.com)
 */
@Chord(
    name = "scopeExcludedM",
    sign = "M0:M0"
  )
public class RelScopeExcludedM extends ProgramRel {

  public static boolean isOutOfScope(String cName) {
    return Utils.prefixMatch(cName, Config.scopeExcludeAry);
  }
  
  public static boolean isOutOfScope(jq_Method m) {
    String cName = m.getDeclaringClass().getName();
    return isOutOfScope(cName);
  }
  
  public void fill() {
    Program program = Program.g();
    IndexSet<jq_Method> methods = program.getMethods();
    for(jq_Method m: methods) {
      if(isOutOfScope(m))
        add(m);
    }
  }
}

