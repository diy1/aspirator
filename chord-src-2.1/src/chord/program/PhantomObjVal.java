package chord.program;

import joeq.Class.jq_Reference;

public class PhantomObjVal {
    public final jq_Reference r;
    public PhantomObjVal(jq_Reference r) {
        assert (r != null);
        this.r = r;
    }
    @Override
    public int hashCode() {
        return r.hashCode();
    }
    @Override
    public boolean equals(Object o) {
        if (o instanceof PhantomObjVal) {
            return ((PhantomObjVal) o).r == this.r;
        }
        return false;
    }
}
