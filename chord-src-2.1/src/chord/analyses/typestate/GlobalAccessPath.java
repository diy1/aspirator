package chord.analyses.typestate;

import java.util.Collections;
import java.util.List;

import joeq.Class.jq_Field;

public class GlobalAccessPath extends AccessPath {
    public final jq_Field global; // static field; non-null

    public GlobalAccessPath(jq_Field g, List<jq_Field> fields) {
        super(fields);
        assert (g != null);
        this.global = g;
    }

    public GlobalAccessPath(jq_Field g) {
        super(Collections.EMPTY_LIST);
        this.global = g;
    }

    @Override
    public int hashCode() {
        return 31 * global.hashCode() + super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof GlobalAccessPath) {
            GlobalAccessPath that = (GlobalAccessPath) obj;
            return global == that.global && fields.equals(that.fields);
        }
        return false;
    }

    @Override
    public String toString() {
        return global.getName() + super.toString();
    }
}
