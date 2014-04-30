package chord.project;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

import CnCHJ.api.Closure;
import CnCHJ.api.CnCReturnValue;
import CnCHJ.runtime.CnCRuntime;

public class DefaultCtrlCollection implements ICtrlCollection {
    protected String name;
    protected List<IStepCollection> prescribedCollections;
    protected Set tags = Collections.synchronizedSet(new HashSet());
    @Override
    public void setName(String name) {
        this.name = name;
    }
    @Override
    public String getName() {
        return name;
    }
    @Override
    public void setPrescribedCollections(List<IStepCollection> c) {
        prescribedCollections = c;
    }
    @Override
    public List<IStepCollection> getPrescribedCollections() {
        return prescribedCollections;
    }
    @Override
    public String name() {
        return name;
    }
    @Override
    public void Put(final Object ctrl) {
        if (!tags.add(ctrl))
            return;
        int n = prescribedCollections.size();
        Closure[] c = new Closure[n];
        for (int i = 0; i < n; i++) {
            final IStepCollection sc = prescribedCollections.get(i);
            c[i] = new Closure() {
                @Override
                public boolean ready() { return true; }
                @Override
                public CnCReturnValue compute() {
                    sc.run(ctrl);
                    return CnCReturnValue.Success;
                }
            };
        }
        CnCRuntime runtime = ModernProject.g().getRuntime();
        runtime.PutTag(ctrl, this, c);
    }
}
