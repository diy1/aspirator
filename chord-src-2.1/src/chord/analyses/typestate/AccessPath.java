package chord.analyses.typestate;

import java.util.List;
import joeq.Class.jq_Field;

public abstract class AccessPath {
    // non-null and immutable; may be empty
    public final List<jq_Field> fields;

    public AccessPath(List<jq_Field> f) {
        assert (f != null);
        this.fields = f;
    }
    
    @Override
    public int hashCode() {
        int code = 0;
        for (jq_Field f : fields) code = code ^ f.hashCode();
        return code;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof AccessPath)
            return ((AccessPath) obj).fields.equals(this.fields);
        return false;
    }

    @Override
    public String toString() {
        if (fields.isEmpty()) return "";
        String ret = "";
        for (jq_Field f : fields) ret += "." + f.getName();
        return ret;
    }
}

