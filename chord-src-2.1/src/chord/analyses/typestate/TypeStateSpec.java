package chord.analyses.typestate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.UTF.Utf8;

import chord.util.ArraySet;

/**
 * A type-state specification.  It consists of five parts:
 * a) A type whose states are being specified.
 * b) A user-defined start state.
 * c) An implicit error state.
 * d) For each method of interest: the (pre-state -> post-state) map.
 *    The map is partial: states not specified in its domain transition to the implicit error state.
 *    For uninteresting methods, it is the id map: each state transitions to itself.
 * e) For each method of interest: the set of legal pre-states.
 *    For uninteresting methods, it is the set of all states (i.e., any pre-state is regarded legal).
 *
 * @author machiry
 */
public class TypeStateSpec {
    protected final ArraySet<TypeState> states;
    protected String type;
    protected TypeState startState;
    protected TypeState errorState;
    protected final Map<Utf8, Map<TypeState, TypeState>> updates;
    protected final Map<Utf8, Set<TypeState>> asserts;

    public void addState(String name) {
        states.add(new TypeState(name));
    }

    public TypeState getState(String name) {
        TypeState ts = new TypeState(name);
        if (states.contains(ts))
            return states.get(states.indexOf(ts));
        return null;
    }

    public TypeStateSpec() {
        states = new ArraySet<TypeState>();
        type = null;
        updates = new HashMap<Utf8, Map<TypeState, TypeState>>();
        asserts = new HashMap<Utf8, Set<TypeState>>();
        startState = null;
        errorState = null;
    }

    public String getType() { return type; }

    public TypeState getStartState() { return startState; }

    public TypeState getErrorState() { return errorState; }
    

    /**
     * This function given the function name and the source state will give the
     * target state if the source state satisfies Assertion (if there is some)
     * if its doesn't satisfy this will return errorState if the source state
     * has transition defined under this method the target state will be
     * returned if there is no defined transition then error state will be
     * returned
     * 
     * @param methodName
     * @param sourceState
     * @return target TypeState
     */
    public TypeState getTargetState(String methodName, TypeState sourceState) {
        Utf8 m = Utf8.get(methodName);
        return getTargetState(m, sourceState);
    }

    public TypeState getTargetState(Utf8 finalMethodName, TypeState sourceState) {
        TypeState targetState = sourceState;
        if (isMethodOfInterest(finalMethodName)) {
            if (asserts.containsKey(finalMethodName)) {
                if (!asserts.get(finalMethodName).contains(sourceState)) {
                    return errorState;
                }
            }
            if (updates.containsKey(finalMethodName)) {
                Map<TypeState, TypeState> map = updates.get(finalMethodName);
                if (map.containsKey(sourceState)) {
                    targetState = map.get(sourceState);
                } else {
                    return errorState;
                }
            }
        }
        return targetState;
    }

    /**
     * This will give all the transitions valid for the provided method name
     * 
     * @param methodName
     * @return
     */

    public Map<TypeState, TypeState> getUpdates(String methodName) {
        return getUpdates(Utf8.get(methodName));
    }

    public Map<TypeState, TypeState> getUpdates(Utf8 finalMethodName) {
        if (!updates.containsKey(finalMethodName)) return null;
        return updates.get(finalMethodName);
    }

    /**
     * Provides all assertions that need to be hold true for the provided method.
     * 
     * @param methodName
     * @return will return the set of assertion states
     */
    public Set<TypeState> getAsserts(String methodName) {
        return getAsserts(Utf8.get(methodName));
    }

    public Set<TypeState> getAsserts(Utf8 finalMethodName) {
        if (!asserts.containsKey(finalMethodName)) return null;
        return asserts.get(finalMethodName);
    }
    
    public boolean isMethodOfInterest(jq_Method method){
        return isMethodOfInterest(method.getName());
    }
    
    public boolean isMethodOfInterest(Utf8 methodName) {
        return asserts.containsKey(methodName) || updates.containsKey(methodName);
    }

    /***
     * For a given method, tells whether it has any assertions or transitions defined.
     * 
     * @param methodName
     * @return true iff the method has at least one transition or assertion defined.
     */
    public boolean isMethodOfInterest(String methodName) {
        return isMethodOfInterest(Utf8.get(methodName));
    }

    /**
     * Adds a method transition along with the method name to the state specification.
     * 
     * @param methodName
     * @param sourceState
     * @param targetState
     * @return true if the transition is new else false
     */
    public void addUpdate(String methodName, TypeState sourceState, TypeState targetState) {
        Utf8 m = Utf8.get(methodName);
        Map<TypeState, TypeState> map = updates.get(m);
        if (map == null) {
            map = new HashMap<TypeState, TypeState>();
            updates.put(m, map);
        }
        map.put(sourceState, targetState);
    }

    /**
     * Adds the given assert state to the method assert state.
     * 
     * @param methodName
     * @param assertState
     * @return
     */
    public void addAssert(String methodName, TypeState assertState) {
        Utf8 m = Utf8.get(methodName);
        Set<TypeState> targetSet = asserts.get(m);
        if (targetSet == null) {
            targetSet = new ArraySet<TypeState>();
            asserts.put(m, targetSet);
        }
        targetSet.add(assertState);
    }

    /**
     * This method adds the Start info to the state specification
     * 
     * @param typeName
     * @param startState
     */
    public void addStartInfo(String typeName, TypeState initState) {
        startState = initState;
        type = typeName;
    }

    /**
     * This method adds the error state to the type specification
     * 
     * @param error
     */
    public void addErrorState(TypeState error) {
        errorState = error;
    }
    
    public String toString() {
        String s = "Type=" + type + " Start_State=" + startState + " Error_State=" + errorState;
        s += "\nUpdates:";
        for (Map.Entry<Utf8, Map<TypeState, TypeState>> e : updates.entrySet()) {
            s += "\n\t" + e.getKey() + "=";
            for (Map.Entry<TypeState, TypeState> e2 : e.getValue().entrySet())
                s += e2.getKey() + "->" + e2.getValue() + " ";
        }
        s += "\nAsserts:";
        for (Map.Entry<Utf8, Set<TypeState>> e : asserts.entrySet()) {
            s += "\n\t" + e.getKey() + "=";
            for (TypeState ts : e.getValue())
                s += ts + " ";
        }
        return s;
    }
}
