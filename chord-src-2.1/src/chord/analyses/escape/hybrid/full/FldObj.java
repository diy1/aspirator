package chord.analyses.escape.hybrid.full;

import joeq.Class.jq_Field;

public class FldObj {
    public final jq_Field f;
    public final boolean isLoc;
    public final boolean isEsc;
    public FldObj(jq_Field f, boolean isLoc, boolean isEsc) {
        this.f = f;
        // to optimize space, forbid storing Obj.EMTY
        assert (isLoc || isEsc);
        this.isLoc = isLoc;
        this.isEsc = isEsc;
    }
    public boolean equals(Object o) {
        if (!(o instanceof FldObj))
            return false;
        FldObj that = (FldObj) o;
        return that.f == f && that.isLoc == isLoc && that.isEsc == isEsc;
    }
    public int hashCode() {
        return f == null ? 0 : f.hashCode();
    }
}
