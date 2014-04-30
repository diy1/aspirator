package chord.project;

import hj.runtime.wsh.WshRuntime_c;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import CnCHJ.api.ItemCollection;
import CnCHJ.runtime.CnCRuntime;
import CnCHJ.runtime.ItemCollectionFactory;

import chord.project.analyses.DlogAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.ITask;
import chord.bddbddb.RelSign;

/**
 * A Chord project comprising a set of tasks and a set of targets
 * produced/consumed by those tasks.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class ModernProject extends Project {
    private static final String MULTIPLE_STEPS_PRODUCING_DATA =
        "ERROR: ModernProject: Multiple step collections (%s) producing data collection '%s'; include exactly one of them via -Dchord.run.analyses";
    private static final String STEP_PRODUCING_DATA_NOT_FOUND =
        "ERROR: ModernProject: No step collection producing data collection '%s' found in project";

    private ModernProject() { }

    private static ModernProject project = null;

    public static ModernProject g() {
        if (project == null)
            project = new ModernProject();
        return project;
    }

    private static final String PROGRAM_TAG = "program";
    private final Map<String, IStepCollection> nameToStepCollectionMap = new HashMap<String, IStepCollection>();
    private final Map<String, ICtrlCollection> nameToCtrlCollectionMap = new HashMap<String, ICtrlCollection>();
    private final Map<String, IDataCollection> nameToDataCollectionMap = new HashMap<String, IDataCollection>();
    private Map<String, RelSign> nameToTrgtSignMap;
    private CnCRuntime runtime;
    private boolean isBuilt = false;

    @Override
    public void build() {
        if (isBuilt)
            return;

        TaskParser taskParser = new TaskParser();
        if (!taskParser.run())
            abort();
        
        // create all step collections and populate nameToStepCollectionMap;
        // also set the name and task/taskKind of each step collection
        Map<String, Class<ITask>> nameToJavaTaskMap = taskParser.getNameToJavaTaskMap();
        Map<String, DlogAnalysis> nameToDlogTaskMap = taskParser.getNameToDlogTaskMap();
        for (Map.Entry<String, Class<ITask>> e : nameToJavaTaskMap.entrySet()) {
            StepCollectionForTaskWithState sc =
                new StepCollectionForTaskWithState();
            String name = e.getKey();
            Class<ITask> taskKind = e.getValue();
            sc.setName(name);
            sc.setTaskKind(taskKind);
            nameToStepCollectionMap.put(name, sc);
        }
        for (Map.Entry<String, DlogAnalysis> e : nameToDlogTaskMap.entrySet()) {
            StepCollectionForStatelessTask sc = new StepCollectionForStatelessTask();
            String name = e.getKey();
            DlogAnalysis task = e.getValue();
            sc.setName(name);
            sc.setTask(task);
            nameToStepCollectionMap.put(name, sc);
        }
        
        // create each control collection that prescribes some step
        // collection and populate nameToCtrlCollectionMap; also set the
        // prescribingCollection of each step collection and the
        // prescribedCollections of each control collection
        Map<String, String> nameToPrescribingNameMap = taskParser.getNameToPrescriberNameMap();
        for (Map.Entry<String, String> e : nameToPrescribingNameMap.entrySet()) {
            String scName = e.getKey();
            String ccName = e.getValue();
            IStepCollection sc = nameToStepCollectionMap.get(scName);
            assert (sc != null);
            ICtrlCollection cc = getOrMakeCtrlCollection(ccName);
            sc.setPrescribingCollection(cc);
            List<IStepCollection> scList = cc.getPrescribedCollections();
            if (scList == null) {
                scList = new ArrayList<IStepCollection>();
                cc.setPrescribedCollections(scList);
            } 
            assert (!scList.contains(sc));
            scList.add(sc);
        }
        
        // map each step collection to the control collections it produces;
        // also create control collections encountered that were not created
        // above because they do not prescribe any step collection
        Map<String, List<String>> nameToControlNamesMap = taskParser.getNameToControlNamesMap();
        for (Map.Entry<String, List<String>> e : nameToControlNamesMap.entrySet()) {
            String scName = e.getKey();
            List<String> ccNames = e.getValue();
            IStepCollection sc = nameToStepCollectionMap.get(scName);
            assert (sc != null);
            int n = ccNames.size();
            List<ICtrlCollection> ccList = (n == 0) ? Collections.EMPTY_LIST : new ArrayList<ICtrlCollection>(n);
            sc.setProducedCtrlCollections(ccList);
            for (String ccName : ccNames) {
                ICtrlCollection cc = getOrMakeCtrlCollection(ccName);
                assert (!ccList.contains(cc));
                ccList.add(cc);
            }
        }
        
        // map each step collection to the data collections it consumes
        Map<String, List<String>> nameToConsumeNamesMap = taskParser.getNameToConsumeNamesMap();
        for (Map.Entry<String, List<String>> e : nameToConsumeNamesMap.entrySet()) {
            String scName = e.getKey();
            List<String> dcNames = e.getValue();
            IStepCollection sc = nameToStepCollectionMap.get(scName);
            assert (sc != null);
            int n = dcNames.size();
            List<IDataCollection> dcList = (n == 0) ? Collections.EMPTY_LIST : new ArrayList<IDataCollection>(n);
            sc.setConsumedDataCollections(dcList);
            for (String dcName : dcNames) {
                IDataCollection dc = getOrMakeDataCollection(dcName);
                assert (!dcList.contains(dc));
                dcList.add(dc);
            }
        }
        
        // map each step collection to the data collections it produces
        Map<String, List<String>> nameToProduceNamesMap = taskParser.getNameToProduceNamesMap();
        for (Map.Entry<String, List<String>> e : nameToProduceNamesMap.entrySet()) {
            String scName = e.getKey();
            List<String> dcNames = e.getValue();
            IStepCollection sc = nameToStepCollectionMap.get(scName);
            assert (sc != null);
            int n = dcNames.size();
            List<IDataCollection> dcList = (n == 0) ? Collections.EMPTY_LIST : new ArrayList<IDataCollection>(n);
            sc.setProducedDataCollections(dcList);
            for (String dcName : dcNames) {
                IDataCollection dc = getOrMakeDataCollection(dcName);
                assert (!dcList.contains(dc));
                dcList.add(dc);
                List<IStepCollection> scList = dc.getProducingCollections();
                if (scList == null) {
                    scList = new ArrayList<IStepCollection>();
                    dc.setProducingCollections(scList);
                }
                assert (!scList.contains(sc));
                scList.add(sc);
            }
        }

        for (ICtrlCollection cc : nameToCtrlCollectionMap.values()) {
            if (cc.getPrescribedCollections() == null)
                cc.setPrescribedCollections(Collections.EMPTY_LIST);
        }
        for (IDataCollection dc : nameToDataCollectionMap.values()) {
            if (dc.getProducingCollections() == null)
                dc.setProducingCollections(Collections.EMPTY_LIST);
        }

        // build nameToTrgtSignMap
        Map<String, Set<TrgtInfo>> nameToTrgtInfosMap = taskParser.getNameToTrgtInfosMap();
        TrgtParser trgtParser = new TrgtParser(nameToTrgtInfosMap);
        if (!trgtParser.run())
            abort();
        nameToTrgtSignMap = trgtParser.getNameToTrgtSignMap();

        Collection<ICtrlCollection> ccSet = nameToCtrlCollectionMap.values();
        ArrayList<ICtrlCollection> ccList = new ArrayList<ICtrlCollection>(ccSet);
        runtime = new CnCRuntime(ccList, null);
        isBuilt = true;
    }

    @Override
    public void run(String[] taskNames) {
        build();
        WshRuntime_c.getCurrentWshActivity().startFinish();
        try {
            for (String name : taskNames) {
                ICtrlCollection cc = getCtrlCollectionOfStep(name);
                cc.Put(PROGRAM_TAG);
            }
        } catch (Throwable ex) {
            Messages.fatal(ex);
        }
        WshRuntime_c.getCurrentWshActivity().stopFinish();
    }
    
    @Override
    public void printRels(String[] relNames) {
        build();
        for (String name : relNames) {
            IDataCollection dc = getDataCollectionByName(name);
            ItemCollection ic = dc.getItemCollection();
            ProgramRel rel = (ProgramRel) ic.Get(PROGRAM_TAG);
            rel.load();
            rel.print();
        }
    }

    @Override
    public void print() {
        build();
        throw new RuntimeException();
    }

    public RelSign getSign(String name) {
        return nameToTrgtSignMap.get(name);
    }
    
    private ICtrlCollection getOrMakeCtrlCollection(String name) {
        ICtrlCollection cc = nameToCtrlCollectionMap.get(name);
        if (cc == null) {
            cc = new DefaultCtrlCollection();
            cc.setName(name);
            nameToCtrlCollectionMap.put(name, cc);
        }
        return cc;
    }

    private IDataCollection getOrMakeDataCollection(String name) {
        IDataCollection dc = nameToDataCollectionMap.get(name);
        if (dc == null) {
            dc = new DefaultDataCollection();
            dc.setName(name);
            ItemCollection ic = ItemCollectionFactory.Create(name);
            dc.setItemCollection(ic);
            nameToDataCollectionMap.put(name, dc);
        }
        return dc;
    }

    public Object[] runPrologue(Object ctrl, IStepCollection sc) {
        List<IDataCollection> cdcList = sc.getConsumedDataCollections();
        int n = cdcList.size();
        Object[] consumes = new Object[n];
        for (int i = 0; i < n; i++) {
            IDataCollection cdc = cdcList.get(i);
            List<IStepCollection> scList = cdc.getProducingCollections();
            int m = scList.size();
            if (m > 1) {
                String stepsStr = "";
                for (IStepCollection sc2 : scList)
                    stepsStr += " " + sc2.getName();
                Messages.fatal(MULTIPLE_STEPS_PRODUCING_DATA, stepsStr, cdc.getName());
            }
            if (m == 0)
                Messages.fatal(STEP_PRODUCING_DATA_NOT_FOUND, cdc.getName());
            IStepCollection sc2 = scList.get(0);
            ICtrlCollection cc2 = sc2.getPrescribingCollection();
            System.out.println(Thread.currentThread() + " Prologue of step sc=" + sc.getName() +
                " doing PUT of ctrl=" + ctrl + " into cc=" + cc2.getName());
            cc2.Put(ctrl);
            ItemCollection cic = cdc.getItemCollection();
            System.out.println(Thread.currentThread() + " Prologue of step sc=" + sc.getName() +
                " doing GET of ctrl=" + ctrl + " from dc=" + cdc.getName());
            consumes[i] = cic.Get(ctrl);
        }
        return consumes;
    }

    public void runEpilogue(Object ctrl, IStepCollection sc, Object[] produces, Object[] controls) {
        List<IDataCollection> pdcList = sc.getProducedDataCollections();
        int m = pdcList.size();
        for (int i = 0; i < m; i++) {
            IDataCollection pdc = pdcList.get(i);
            ItemCollection pic = pdc.getItemCollection();
            Object o = produces[i];
            if (o != null) {
                System.out.println(Thread.currentThread() + " Epilogue of step sc=" +
                    sc.getName() + " doing PUT of ctrl=" + ctrl + " into dc=" + pdc.getName());
                pic.Put(ctrl, o);
            }
        }
        List<ICtrlCollection> pccList = sc.getProducedCtrlCollections();
        int k = pccList.size();
        for (int i = 0; i < k; i++) {
            ICtrlCollection pcc = pccList.get(i);
            Object ctrl2 = controls[i];
            if (ctrl2 != null) {
                System.out.println(Thread.currentThread() + " Epilogue of step sc=" +
                    sc.getName() + " doing PUT of ctrl=" + ctrl2 + " into cc=" + pcc.getName());
                pcc.Put(ctrl2);
            }
        }
    }

    public IStepCollection getStepCollectionByName(String name) {
        return nameToStepCollectionMap.get(name);
    }

    public IDataCollection getDataCollectionByName(String name) {
        return nameToDataCollectionMap.get(name);
    }

    public ICtrlCollection getCtrlCollectionByName(String name) {
        return nameToCtrlCollectionMap.get(name);
    }

    public ICtrlCollection getCtrlCollectionOfStep(String name) {
        IStepCollection sc = getStepCollectionByName(name);
        return (sc != null) ? sc.getPrescribingCollection() : null;
    }

    public CnCRuntime getRuntime() {
        return runtime;
    }
}
