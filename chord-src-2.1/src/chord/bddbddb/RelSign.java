package chord.bddbddb;

import java.util.ArrayList;
import java.util.List;

import chord.util.Utils;
import chord.util.tuple.object.Pair;

/**
 * Signature of a BDD-based relation.
 * It specifies:
 * (1) an ordered list of domain names of the relation (e.g., [M1, H0]) and
 * (2) the BDD ordering of the domain names (e.g., M1_H0 or M1xH0)
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class RelSign extends Pair<String[], String> {
    private static final long serialVersionUID = -7422086853641720643L;
    /**
     * Constructor.
     * 
     * @param domNames An ordered list of domain names of the relation (e.g., [M1, H0]).
     * It must be non-null and not contain any duplicate domain names.
     * Each domain name must consist of a major component (a sequence of one or more alphabets)
     * followed by a minor component (a sequence of one or more digits).
     * @param domOrder The BDD ordering of the domain names (e.g., M1_H0 or M1xH0).
     *  It may be null.
     */
    public RelSign(String[] domNames, String domOrder) {
        super(domNames, domOrder);
        validate();
    }
    public String toString() {
        return Utils.toString(val0, "[", ",", "]") + " " + val1;
    }
    private void validate() {
        assert (val0 != null);
        assert (!Utils.hasDuplicates(val0));
        if (val1 == null)
            return;
        String[] domNamesFromDomOrder = val1.split("_|x");
        if (val0.length != domNamesFromDomOrder.length) {
            throw new RuntimeException(
                "Number of domains in domNames and domOrder of relSign '" +
                this + "' do not match.");
        }
        assert (!Utils.hasDuplicates(domNamesFromDomOrder));
        for (String domName : val0) {
            boolean found = false;
            for (String domName2 : domNamesFromDomOrder) {
                if (domName2.equals(domName))
                    found = true;
            }
            if (!found) {
                throw new RuntimeException("Domain name '" + domName +
                    "' in domNames is missing in domOrder of relSign '" +
                    this + "'.");
            }
        }
    }
    /**
     * Provides an ordered list of domain names specified by this signature.
     *
     * @return An ordered list of domain names specified by this signature.
     */
    public String[] getDomNames() {
        return val0;
    }
    /**
     * Provides the BDD ordering of domain names specified by this signature.
     *
     * @return The BDD ordering of domain names specified by this signature.
     */
    public String getDomOrder() {
        return val1;
    }
    /**
     * Provides the list of domain kinds (major components of domain names) specified by this signature.
     * For instance, if the list of domain names is [C0,M0,I0,C1,M1] then the list of domain kinds is [C,M,I].
     * 
     * @return The list of domain kinds specified by this signature.
     */
    public String[] getDomKinds() {
        List<String> domKindsList = new ArrayList<String>(val0.length);
        for (String domName : val0) {
            String domKind = Utils.trimNumSuffix(domName);
            if (!domKindsList.contains(domKind))
                domKindsList.add(domKind);
        }
        String[] domKindsAry = new String[domKindsList.size()];
        domKindsList.toArray(domKindsAry);
        return domKindsAry;
    }
}
