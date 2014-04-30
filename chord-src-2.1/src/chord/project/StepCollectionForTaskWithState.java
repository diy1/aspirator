package chord.project;

public class StepCollectionForTaskWithState extends AbstractStepCollection {
    protected Class<ITask> taskKind;
    // must be called in step collection initialization stage
    public void setTaskKind(Class<ITask> taskKind) {
        this.taskKind = taskKind;
    }
    @Override
    public void run(Object ctrl) {
        ITask task = null;
        try {
            task = taskKind.newInstance();
            task.setName(name);
        } catch (InstantiationException e) {
            Messages.fatal(e);
        } catch (IllegalAccessException e) {
            Messages.fatal(e);
        }
        System.out.println(Thread.currentThread() + " ENTER: ctrl=" + ctrl + " sc(stateful)="  + getName());
        task.run(ctrl, this);
        System.out.println(Thread.currentThread() + " LEAVE: ctrl=" + ctrl + " sc(stateful)="  + getName());
    }
}
