package chord.project;

import java.util.List;

import CnCHJ.api.ItemCollection;

/**
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public interface IDataCollection {
    public void setName(String name);

    public String getName();
    
    public void setItemCollection(ItemCollection ic);
    
    public ItemCollection getItemCollection();

    public void setProducingCollections(List<IStepCollection> ic);

    public List<IStepCollection> getProducingCollections();
}
