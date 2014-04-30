package chord.analyses.typestate;

/**
 * Representation of a type state.
 *
 * @author machiry
 */
public class TypeState {
    public final String name;

    public TypeState(String name) {
        this.name = name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof TypeState)) return false;
        TypeState that = (TypeState) o;
        return name.equals(that.name);
    }
    
    @Override
    public String toString() {
        return name;
    }
}
