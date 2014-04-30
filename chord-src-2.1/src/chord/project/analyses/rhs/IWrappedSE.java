package chord.project.analyses.rhs;

public interface IWrappedSE<PE extends IEdge, SE extends IEdge> {
    public SE getSE();
    public IWrappedPE<PE,SE> getWPE();
}

