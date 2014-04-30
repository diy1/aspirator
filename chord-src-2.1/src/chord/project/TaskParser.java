package chord.project;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;

import org.scannotation.AnnotationDB;

import chord.project.analyses.DlogAnalysis;
import chord.project.analyses.ProgramDom;
import chord.project.analyses.ProgramRel;
import chord.project.ITask;
import chord.util.Utils;
import chord.bddbddb.RelSign;

/**
 * A Chord project comprising a set of tasks and a set of targets
 * produced/consumed by those tasks.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class TaskParser {
    private static final String ANON_JAVA_TASK =
        "WARN: TaskParser: Java analysis '%s' is not named via a @Chord(name=\"...\") annotation; using its class name itself as its name.";
    private static final String ANON_DLOG_TASK =
        "WARN: TaskParser: Dlog analysis '%s' is not named via a # name=... line; using its filename itself as its name.";
    private static final String NON_EXISTENT_PATH_ELEM = "WARN: TaskParser: Ignoring non-existent entry '%s' in path '%s'.";
    private static final String MALFORMED_PATH_ELEM = "WARN: TaskParser: Ignoring malformed entry '%s' in path '%s': %s";
    private static final String JAVA_TASK_REDEFINED =
        "ERROR: TaskParser: Ignoring Java analysis '%s': its @Chord(name=\"...\") annotation uses name '%s' that is also used for another task '%s'.";
    private static final String DLOG_TASK_REDEFINED =
        "ERROR: TaskParser: Ignoring Dlog analysis '%s': its # name=\"...\" line uses name '%s' that is also used for another task '%s'.";
    private static final String IGNORE_DLOG_TASK =
        "ERROR: TaskParser: Ignoring Dlog analysis '%s'; errors were found while parsing it (see above).";
    private static final String IGNORE_JAVA_TASK =
        "ERROR: TaskParser: Ignoring Java analysis '%s'; errors were found in its @Chord annotation (see above).";

    private final Map<String, Class<ITask>> nameToJavaTaskMap =
        new HashMap<String, Class<ITask>>();
    private final Map<String, DlogAnalysis> nameToDlogTaskMap =
        new HashMap<String, DlogAnalysis>();
    private final Map<String, String> nameToPrescriberNameMap =
        new HashMap<String, String>();
    private final Map<String, List<String>> nameToConsumeNamesMap =
        new HashMap<String, List<String>>();
    private final Map<String, List<String>> nameToProduceNamesMap =
        new HashMap<String, List<String>>();
    private final Map<String, List<String>> nameToControlNamesMap =
        new HashMap<String, List<String>>();
    private final Map<String, Set<TrgtInfo>> nameToTrgtInfosMap =
        new HashMap<String, Set<TrgtInfo>>();
    private boolean hasNoErrors = true;

    public Map<String, Class<ITask>> getNameToJavaTaskMap() {
        return nameToJavaTaskMap;
    }

    public Map<String, DlogAnalysis> getNameToDlogTaskMap() {
        return nameToDlogTaskMap;
    }

    public Map<String, String> getNameToPrescriberNameMap() {
        return nameToPrescriberNameMap;
    }

    public Map<String, List<String>> getNameToConsumeNamesMap() {
        return nameToConsumeNamesMap;
    }

    public Map<String, List<String>> getNameToProduceNamesMap() {
        return nameToProduceNamesMap;
    }

    public Map<String, List<String>> getNameToControlNamesMap() {
        return nameToControlNamesMap;
    }

    public Map<String, Set<TrgtInfo>> getNameToTrgtInfosMap() {
        return nameToTrgtInfosMap;
    }

    public boolean run() {
        buildDlogAnalysisMap();
        buildJavaAnalysisMap();
        return hasNoErrors;
    }

    private void buildJavaAnalysisMap() {
        String javaAnalysisPathName = Config.javaAnalysisPathName;
        if (javaAnalysisPathName.equals(""))
            return;
        ArrayList<URL> list = new ArrayList<URL>();
        String[] fileNames = javaAnalysisPathName.split(Utils.PATH_SEPARATOR);
        for (String fileName : fileNames) {
            File file = new File(fileName);
            if (!file.exists()) {
                nonexistentPathElem(fileName, "chord.java.analysis.path");
                continue;
            }
            try {
               list.add(file.toURL());
            } catch (MalformedURLException ex) {
                malformedPathElem(fileName, "chord.java.analysis.path", ex.getMessage());
                continue;
           }
        }
        URL[] urls = new URL[list.size()];
        list.toArray(urls);
        AnnotationDB db = new AnnotationDB();
        try {
            db.scanArchives(urls);
        } catch (IOException ex) {
            Messages.fatal(ex);
        }
        Map<String, Set<String>> index = db.getAnnotationIndex();
        if (index == null)
            return;
        Set<String> classNames = index.get(Chord.class.getName());
        if (classNames == null)
            return;
        for (String className : classNames) {
            processJavaAnalysis(className);
        }
    }

    private void buildDlogAnalysisMap() {
        String dlogAnalysisPathName = Config.dlogAnalysisPathName;
        if (dlogAnalysisPathName.equals(""))
            return;
        String[] fileNames = dlogAnalysisPathName.split(Utils.PATH_SEPARATOR);
        for (String fileName : fileNames) {
            File file = new File(fileName);
            if (!file.exists()) {
                nonexistentPathElem(fileName, "chord.dlog.analysis.path");
                continue;
            }
            processDlogAnalysis(file);
        }
    }

    private void processJavaAnalysis(String className) {
        Class<ITask> type = null;
        try {
            type = (Class<ITask>) Class.forName(className);
        } catch (ClassNotFoundException ex) {
            Messages.fatal(ex);
        }
        ChordAnnotParser info = new ChordAnnotParser(type);
        boolean success = info.parse();
        if (!success) {
            ignoreJavaTask(className);
            return;
        }
        String name = info.getName();
        if (name.equals("")) {
            if (Config.verbose >= 2) Messages.log(ANON_JAVA_TASK, className);
            name = className;
        }
        DlogAnalysis dlogTask = nameToDlogTaskMap.get(name);
        if (dlogTask != null) {
            redefinedJavaTask(className, name, dlogTask.getFileName());
            return;
        }
        Class<ITask> javaTask = nameToJavaTaskMap.get(name);
        if (javaTask != null) {
            redefinedJavaTask(className, name, javaTask.getName());
            return;
        }
        Map<String, Class  > nameToTypeMap = info.getNameToTypeMap();
        Map<String, RelSign> nameToSignMap = info.getNameToSignMap();
        for (Map.Entry<String, Class> e : nameToTypeMap.entrySet()) {
            String name2 = e.getKey();
            Class type2 = e.getValue();
            RelSign sign2 = nameToSignMap.get(name2);
            if (sign2 != null)
                createTrgt(name2, type2, className, sign2);
            else
                createTrgt(name2, type2, className);
        }
        for (Map.Entry<String, RelSign> e : nameToSignMap.entrySet()) {
            String name2 = e.getKey();
            if (nameToTypeMap.containsKey(name2))
                continue;
            RelSign sign2 = e.getValue();
            createTrgt(name2, ProgramRel.class, className, sign2);
        }
        String prescriberName = info.getPrescriber();
        nameToPrescriberNameMap.put(name, prescriberName);
        List<String> consumeNames = info.getConsumes();
        nameToConsumeNamesMap.put(name, consumeNames);
        List<String> produceNames = info.getProduces();
        nameToProduceNamesMap.put(name, produceNames);
        List<String> controlNames = info.getControls();
        nameToControlNamesMap.put(name, controlNames);
        for (String s : consumeNames) {
            if (!nameToTypeMap.containsKey(s) && !nameToSignMap.containsKey(s)) {
                createTrgt(s, null, className);
            }
        }
        for (String s : produceNames) {
            if (!nameToTypeMap.containsKey(s) && !nameToSignMap.containsKey(s)) {
                createTrgt(s, null, className);
            }
        }
        nameToJavaTaskMap.put(name, type);
    }

    private void processDlogAnalysis(File file) {
        if (file.isDirectory()) {
            File[] subFiles = file.listFiles();
            for (File subFile : subFiles) {
                if (subFile.isDirectory())
                    processDlogAnalysis(subFile);
                else {
                    String subFileName = subFile.getAbsolutePath();
                    if (subFileName.endsWith(".dlog") || subFileName.endsWith(".datalog")) {
                        processDlogAnalysis(subFileName);
                    }
                }
            }
        } else {
            String fileName = file.getAbsolutePath();
            try {
                if (fileName.endsWith(".jar")) {
                    JarFile jarFile = new JarFile(fileName);
                    Enumeration e = jarFile.entries();
                    while (e.hasMoreElements()) {
                        JarEntry je = (JarEntry) e.nextElement();
                        String fileName2 = je.getName();
                        if (fileName2.endsWith(".dlog") || fileName2.endsWith(".datalog")) {
                            InputStream is = jarFile.getInputStream(je);
                            String fileName3 = OutDirUtils.copyResourceByPath(fileName2, is, "dlog");
                            processDlogAnalysis(fileName3);
                        }
                    }
                } else if (fileName.endsWith(".zip")) {
                    ZipFile zipFile = new ZipFile(fileName);
                    Enumeration e = zipFile.entries();
                    while (e.hasMoreElements()) {
                        ZipEntry ze = (ZipEntry) e.nextElement();
                        String fileName2 = ze.getName();
                        if (fileName2.endsWith(".dlog") || fileName2.endsWith(".datalog")) {
                            InputStream is = zipFile.getInputStream(ze);
                            String fileName3 = OutDirUtils.copyResourceByPath(fileName2, is, "dlog");
                            processDlogAnalysis(fileName3);
                        }
                    }
                }
            } catch (IOException ex) {
                malformedPathElem(fileName, "chord.dlog.analysis.path", ex.getMessage());
            }
        }
    }

    private void processDlogAnalysis(String fileName) {
        DlogAnalysis task = new DlogAnalysis();
        boolean success = task.parse(fileName);
        if (!success) {
            ignoreDlogTask(fileName);
            return;
        }
        String name = task.getDlogName();
        if (name == null) {
            if (Config.verbose >= 2) Messages.log(ANON_DLOG_TASK, fileName);
            name = fileName;
        }
        DlogAnalysis dlogTask = nameToDlogTaskMap.get(name);
        if (dlogTask != null) {
            redefinedDlogTask(fileName, name, dlogTask.getFileName());
            return;
        }
        Class<ITask> javaTask = nameToJavaTaskMap.get(name);
        if (javaTask != null) {
            redefinedDlogTask(fileName, name, javaTask.getName());
            return;
        }
        Set<String> domNames = task.getDomNames();
        for (String domName : domNames) {
            createTrgt(domName, ProgramDom.class, fileName);
        }
        Map<String, RelSign> consumedRelsMap = task.getConsumedRels();
        for (Map.Entry<String, RelSign> e : consumedRelsMap.entrySet()) {
            String relName = e.getKey();
            RelSign relSign = e.getValue();
            createTrgt(relName, ProgramRel.class, fileName, relSign);
        }
        Map<String, RelSign> producedRelsMap = task.getProducedRels();
        for (Map.Entry<String, RelSign> e : producedRelsMap.entrySet()) {
            String relName = e.getKey();
            RelSign relSign = e.getValue();
            createTrgt(relName, ProgramRel.class, fileName, relSign);
        }
        task.setName(name);
        nameToPrescriberNameMap.put(name, name);
        List<String> consumeNames = new ArrayList<String>();
        // NOTE: domains MUST be added BEFORE relations;
        // ModernProject relies on this invariant.
        consumeNames.addAll(domNames);
        consumeNames.addAll(consumedRelsMap.keySet());
        nameToConsumeNamesMap.put(name, consumeNames);
        List<String> produceNames = new ArrayList<String>();
        produceNames.addAll(producedRelsMap.keySet());
        nameToProduceNamesMap.put(name, produceNames);
        List<String> controlNames = Collections.EMPTY_LIST;
        nameToControlNamesMap.put(name, controlNames);
        nameToDlogTaskMap.put(name, task);
    }

    private void createTrgt(String name, Class type, String location) {
        TrgtInfo info = new TrgtInfo(type, location, null);
        createTrgt(name, info);
    }
    
    private void createTrgt(String name, Class type, String location, RelSign relSign) {
        for (String name2 : relSign.getDomKinds()) {
            createTrgt(name2, ProgramDom.class, location); 
        }
        TrgtInfo info = new TrgtInfo(type, location, relSign);
        createTrgt(name, info);
    }

    private void createTrgt(String name, TrgtInfo info) {
        Set<TrgtInfo> infos = nameToTrgtInfosMap.get(name);
        if (infos == null) {
            infos = new HashSet<TrgtInfo>();
            nameToTrgtInfosMap.put(name, infos);
        }
        infos.add(info);
    }

    private void ignoreDlogTask(String name) {
        Messages.log(IGNORE_DLOG_TASK, name);
        hasNoErrors = false;
    }

    private void ignoreJavaTask(String name) {
        Messages.log(IGNORE_JAVA_TASK, name);
        hasNoErrors = false;
    }
    
    private void redefinedJavaTask(String newTaskName, String name, String oldTaskName) {
        Messages.log(JAVA_TASK_REDEFINED, name, oldTaskName, newTaskName);
        hasNoErrors = false;
    }

    private void redefinedDlogTask(String newTaskName, String name, String oldTaskName) {
        Messages.log(DLOG_TASK_REDEFINED, newTaskName, name, oldTaskName);
        hasNoErrors = false;
    }

    private void malformedPathElem(String elem, String path, String msg) {
        if (Config.verbose >= 2) Messages.log(MALFORMED_PATH_ELEM, elem, path, msg);
    }

    private void nonexistentPathElem(String elem, String path) {
        if (Config.verbose >= 2) Messages.log(NON_EXISTENT_PATH_ELEM, elem, path);
    }
}
