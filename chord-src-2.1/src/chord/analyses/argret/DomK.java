package chord.analyses.argret;

import chord.project.Chord;
import chord.project.analyses.ProgramDom;

/**
 * Domain of integers from 0 to chord.domK.size - 1 in order.
 */
@Chord(name = "K")
public class DomK extends ProgramDom<Integer> {
    public static final int MAXZ = Integer.getInteger("chord.domK.size", 32);
    @Override
    public void fill() {
        for (int i = 0; i < MAXZ; i++)
            getOrAdd(new Integer(i));  
    }
}
