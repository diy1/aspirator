package chord.project;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import chord.bddbddb.RelSign;
import chord.project.analyses.ProgramRel;
import chord.util.Utils;

public class TrgtParser {
    private static final String DOM_NAMES_INCONSISTENT = 
        "ERROR: TrgtParser: Relation '%s' declared with different domain names '%s' and '%s' in '%s' and '%s' respectively.";
    private static final String DOM_ORDERS_INCONSISTENT = 
        "WARN: TrgtParser: Relation '%s' declared with different domain orders '%s' and '%s' in '%s' and '%s' respectively.";
    private static final String RELATION_SIGN_UNKNOWN = "ERROR: TrgtParser: Sign of relation '%s' unknown.";
    private static final String RELATION_ORDER_UNKNOWN = "ERROR: TrgtParser: Order of relation '%s' unknown.";
    private static final String TARGET_TYPE_UNKNOWN = "ERROR: TrgtParser: Type of target '%s' unknown.";
    private static final String TARGET_TYPE_INCONSISTENT =
        "ERROR: TrgtParser: Target '%s' declared with inconsistent types '%s' and '%s' in '%s' and '%s' respectively.";

    private final Map<String, Set<TrgtInfo>> nameToTrgtInfosMap;
    private final Map<String, Class> nameToTrgtTypeMap =
        new HashMap<String, Class>();
    private final Map<String, RelSign> nameToTrgtSignMap =
        new HashMap<String, RelSign>();
    private boolean hasNoErrors = true;
    
    public TrgtParser(Map<String, Set<TrgtInfo>> nameToTrgtInfosMap) {
        this.nameToTrgtInfosMap = nameToTrgtInfosMap;
    }

    public Map<String, Class> getNameToTrgtTypeMap() {
        return nameToTrgtTypeMap;
    }

    public Map<String, RelSign> getNameToTrgtSignMap() {
        return nameToTrgtSignMap;
    }

    public boolean run() {
        for (Map.Entry<String, Set<TrgtInfo>> e : nameToTrgtInfosMap.entrySet()) {
            String name = e.getKey();
            Set<TrgtInfo> infos = e.getValue();
            Iterator<TrgtInfo> it = infos.iterator();
            TrgtInfo fstInfo = it.next();
            Class resType = fstInfo.type;
            String resTypeLoc = fstInfo.location;
            String[] resDomNames;
            String resDomOrder;
            if (fstInfo.sign != null) {
                resDomNames = fstInfo.sign.val0;
                resDomOrder = fstInfo.sign.val1;
            } else {
                resDomNames = null;
                resDomOrder = null;
            }
            String resDomNamesLoc = fstInfo.location;
            String resDomOrderLoc = fstInfo.location;
            boolean corrupt = false;
            while (it.hasNext()) {
                TrgtInfo info = it.next();
                Class curType = info.type;
                if (curType != null) {
                    if (resType == null) {
                        resType = curType;
                        resTypeLoc = info.location;
                    } else if (Utils.isSubclass(curType, resType)) {
                        resType = curType;
                        resTypeLoc = info.location;
                    } else if (!Utils.isSubclass(resType, curType)) {
                        inconsistentTypes(name, resType.toString(),
                            curType.toString(), resTypeLoc, info.location);
                        corrupt = true;
                        break;
                    }
                }
                RelSign curSign = info.sign;
                if (curSign != null) {
                    String[] curDomNames = curSign.val0;
                    if (resDomNames == null) {
                        resDomNames = curDomNames;
                        resDomNamesLoc = info.location;
                    } else if (!Arrays.equals(resDomNames, curDomNames)) {
                        inconsistentDomNames(name,
                            Utils.toString(resDomNames),
                            Utils.toString(curDomNames),
                            resDomNamesLoc, info.location);
                        corrupt = true;
                        break;
                    }
                    String curDomOrder = curSign.val1;
                    if (curDomOrder != null) {
                        if (resDomOrder == null) {
                            resDomOrder = curDomOrder;
                            resDomOrderLoc = info.location;
                        } else if (!resDomOrder.equals(curDomOrder)) {
                            inconsistentDomOrders(name, resDomOrder,
                                curDomOrder, resDomOrderLoc, info.location);
                        }
                    }
                }
            }
            if (corrupt)
                continue;
            if (resType == null) {
                unknownType(name);
                continue;
            }
            RelSign sign = null;
            if (Utils.isSubclass(resType, ProgramRel.class)) {
                if (resDomNames == null) {
                    unknownSign(name);
                    continue;
                }
                if (resDomOrder == null) {
                    unknownOrder(name);
                    continue;
                }
                sign = new RelSign(resDomNames, resDomOrder);
            }
            nameToTrgtTypeMap.put(name, resType);
            if (sign != null)
                nameToTrgtSignMap.put(name, sign);
        }
        return hasNoErrors;
    }
    
    private void inconsistentDomNames(String relName, String names1, String names2, String loc1, String loc2) {
        Messages.log(DOM_NAMES_INCONSISTENT, relName,
            names1, names2, loc1, loc2);
        hasNoErrors = false;
    }
    
    private void inconsistentDomOrders(String relName, String order1, String order2, String loc1, String loc2) {
        if (Config.verbose >= 2) Messages.log(DOM_ORDERS_INCONSISTENT, relName,
            order1, order2, loc1, loc2);
    }

    private void inconsistentTypes(String name, String type1, String type2, String loc1, String loc2) {
        Messages.log(TARGET_TYPE_INCONSISTENT, name, type1, type2, loc1, loc2);
        hasNoErrors = false;
    }
    
    private void unknownSign(String name) {
        Messages.log(RELATION_SIGN_UNKNOWN, name);
        hasNoErrors = false;
    }
    
    private void unknownOrder(String name) {
        Messages.log(RELATION_ORDER_UNKNOWN, name);
        hasNoErrors = false;
    }
    
    private void unknownType(String name) {
        Messages.log(TARGET_TYPE_UNKNOWN, name);
        hasNoErrors = false;
    }

}
