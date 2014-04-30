package chord.project;

import java.util.List;

/**
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public interface IStepCollection {
    /**
     * Sets the name of this program analysis.
     * 
     * @param name A name unique across all program analyses included in a Chord project.
     */
    public void setName(String name);
    /**
     * Provides the name of this program analysis.
     * 
     * @return  The name of this program analysis.
     */
    public String getName();

    public void run(Object ctrl);
 
    public void setConsumedDataCollections(List<IDataCollection> c);

    public List<IDataCollection> getConsumedDataCollections();

    public void setProducedDataCollections(List<IDataCollection> c);

    public List<IDataCollection> getProducedDataCollections();

    public void setProducedCtrlCollections(List<ICtrlCollection> c);

    public List<ICtrlCollection> getProducedCtrlCollections();

    public void setPrescribingCollection(ICtrlCollection c);

    public ICtrlCollection getPrescribingCollection();
}
