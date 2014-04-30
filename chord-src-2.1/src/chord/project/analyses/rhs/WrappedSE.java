package chord.project.analyses.rhs;

import chord.util.Utils;

public class WrappedSE<PE extends IEdge, SE extends IEdge> implements IWrappedSE<PE, SE> {
    private final SE se;
    private IWrappedPE<PE, SE> wpe;
    private int len;

    public WrappedSE(SE se, IWrappedPE<PE, SE> pe, int len) {
        assert (len >= 0);
        this.se = se;
        this.wpe = pe;
        this.len = len;
    }

    public void update(IWrappedPE<PE, SE> newWPE, int newLen) {
        assert (newLen >= 0);
        this.wpe = newWPE;
        this.len = newLen;
    }

    public int getLen() { return len; }

    @Override
    public SE getSE() { return se; }

    @Override
    public IWrappedPE<PE, SE> getWPE() { return wpe; }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((se == null) ? 0 : se.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof WrappedSE)) return false;
        WrappedSE that = (WrappedSE) obj;
        return Utils.areEqual(this.se, that.se);
    }
    @Override
    public String toString() {
        return "WrappedSE [se=" + se + "]";
    }
}
