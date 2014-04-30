package chord.program;

import joeq.Class.jq_Reference;

public class PhantomClsVal {
    public final jq_Reference r;
    public PhantomClsVal(jq_Reference r) {
        assert (r != null);
        this.r = r;
    }
    @Override
    public int hashCode() {
        return r.hashCode();
    }
    @Override
    public boolean equals(Object o) {
        if (o instanceof PhantomClsVal) {
            return ((PhantomClsVal) o).r == this.r;
        }
        return false;
    }
}
