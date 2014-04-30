package chord.program;

/**
 * Representation of the unique identifier of any bytecode instruction in any method in the program.
 * 
 * Its format is {@code offset!mName:mDesc@cName} where:
 * <ul>
 * <li> {@code offset} denotes the bytecode offset of the instruction in its containing method. </li>
 * <li> {@code mName} denotes the name of the method. </li>
 * <li> {@code mDesc} denotes the descriptor of the method. </li>
 * <li> {@code cName} denotes the fully-qualified name of the class declaring the method. </li>
 * </ul>
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class MethodElem extends MethodSign {
    public final int offset;

    /**
     * Creates the representation of the unique identifier of the given bytecode instruction.
     * 
     * @param offset The bytecode offset of the instruction in its containing method.
     * @param mName  The name of the method.
     * @param mDesc  The descriptor of the method.
     * @param cName  The fully-qualified name of the class declaring the method.
     */
    public MethodElem(int offset, String mName, String mDesc, String cName) {
        super(mName, mDesc, cName);
        this.offset = offset;
    }

    /**
     * Provides the representation of the unique identifier of the given bytecode instruction.
     * 
     * @param s A string of the form {@code offset!mName:mDesc@cName} uniquely identifying a bytecode instruction.
     *
     * @return The representation of the unique identifier of the given bytecode instruction.
     */
    public static MethodElem parse(String s) {
        int exclIdx = s.indexOf('!');
        int colonIdx  = s.indexOf(':');
        int atIdx = s.indexOf('@');
        int num = Integer.parseInt(s.substring(0, exclIdx));
        String mName = s.substring(exclIdx + 1, colonIdx);
        String mDesc = s.substring(colonIdx + 1, atIdx);
        String cName = s.substring(atIdx + 1);
        return new MethodElem(num, mName, mDesc, cName);
    }

    public String toString() {
        return offset + "!" + mName + ":" + mDesc + "@" + cName;
    }
}
