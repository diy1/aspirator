package chord.program;

/**
 * Representation of the unique identifier of any field or method in the program.
 * 
 * Its format is {@code mName:mDesc@cName} where:
 * <ul>
 *   <li>{@code mName} denotes the name of the field/method.</li>
 *   <li>{@code mDesc} denotes the descriptor of the field/method.</li>
 *   <li>{@code cName} denotes the fully-qualified name of the class declaring the field/method.</li>
 * </ul>

 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class MethodSign {
    public final String mName;
    public final String mDesc;
    public final String cName;

    /**
     * Creates the representation of the unique identifier of the given field/method.
     * 
     * @param mName The name of the field/method.
     * @param mDesc The descriptor of the field/method.
     * @param cName The fully-qualified name of the class declaring the field/method.
     */
    public MethodSign(String mName, String mDesc, String cName) {
        this.mName = mName;
        this.mDesc = mDesc;
        this.cName = cName;
    }

    /**
     * Provides the representation of the unique identifier of the given field/method.
     * 
     * @param s A string of the form {@code mName:mDesc@cName} uniquely identifying a field/method.
     *
     * @return The representation of the unique identifier of the given field/method.
     */
    public static MethodSign parse(String s) {
        int colonIdx = s.indexOf(':');
        int atIdx = s.indexOf('@');
        String mName = s.substring(0, colonIdx);
        String mDesc = s.substring(colonIdx + 1, atIdx);
        String cName = s.substring(atIdx + 1);
        return new MethodSign(mName, mDesc, cName);
    }
}
