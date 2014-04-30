package chord.project;

import java.util.List;

import CnCHJ.api.TagCollection;

/**
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public interface ICtrlCollection extends TagCollection {
    public void setName(String name);

    public String getName();
    
    public void setPrescribedCollections(List<IStepCollection> c);
    
    public List<IStepCollection> getPrescribedCollections();
}
