package chord.program;

import joeq.Class.jq_Method;
import chord.util.IndexSet;

public interface ScopeBuilder {
    /**
     * Provides all methods in the input Java program that are deemed reachable by this scope builder.
     *
     * @return All methods in the input Java program that are deemed reachable by this scope builder.
     */
    public abstract IndexSet<jq_Method> getMethods();
    /**
     * Provides reflection information in the input Java program that is resolved by this scope builder.
     *
     * @return Reflection information in the input Java program that is resolved by this scope builder.
     */
    public abstract Reflect getReflect();
}
