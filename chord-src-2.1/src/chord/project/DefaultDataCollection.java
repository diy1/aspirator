package chord.project;

import java.util.List;

import CnCHJ.api.ItemCollection;

public class DefaultDataCollection implements IDataCollection {
    protected String name;
    protected ItemCollection ic;
    protected List<IStepCollection> producingCollections;
    @Override
    public void setName(String name) {
        this.name = name;
    }
    @Override
    public String getName() {
        return name;
    }
    @Override
    public void setItemCollection(ItemCollection ic) {
        this.ic = ic;
    }
    @Override
    public ItemCollection getItemCollection() {
        return ic;
    }
    @Override
    public void setProducingCollections(List<IStepCollection> c) {
        producingCollections = c;
    }
    @Override
    public List<IStepCollection> getProducingCollections() {
        return producingCollections;
    }
}
