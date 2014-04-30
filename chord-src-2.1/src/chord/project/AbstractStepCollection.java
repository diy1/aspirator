package chord.project;

import java.util.List;

// abstract since does not implement the run(Object ctrl) method
public abstract class AbstractStepCollection implements IStepCollection {
    protected String name;
    protected List<IDataCollection> consumedDataCollections;
    protected List<IDataCollection> producedDataCollections;
    protected List<ICtrlCollection> producedCtrlCollections;
    protected ICtrlCollection prescribingCollection;
    @Override
    public void setName(String name) {
        this.name = name;
    }
    @Override
    public String getName() {
        return name;
    }
    @Override
    public void setPrescribingCollection(ICtrlCollection c) {
        prescribingCollection = c;
    }
    @Override
    public ICtrlCollection getPrescribingCollection() {
        return prescribingCollection;
    }
    @Override
    public void setConsumedDataCollections(List<IDataCollection> c) {
        consumedDataCollections = c;
    }
    @Override
    public List<IDataCollection> getConsumedDataCollections() {
        return consumedDataCollections;
    }
    @Override
    public void setProducedDataCollections(List<IDataCollection> c) {
        producedDataCollections = c;
    }
    @Override
    public List<IDataCollection> getProducedDataCollections() {
        return producedDataCollections;
    }
    @Override
    public void setProducedCtrlCollections(List<ICtrlCollection> c) {
        producedCtrlCollections = c;
    }
    @Override
    public List<ICtrlCollection> getProducedCtrlCollections() {
        return producedCtrlCollections;
    }
}
