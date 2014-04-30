package chord.project;

public class StepCollectionForStatelessTask extends AbstractStepCollection {
    protected ITask task;
    // must be called in step collection initialization stage
    public void setTask(ITask task) {
        this.task = task;
    }
    @Override
    public void run(Object ctrl) {
        System.out.println("ENTER: ctrl=" + ctrl + " sc(stateless)="  + getName());
        task.run(ctrl, this);
        System.out.println("LEAVE: ctrl=" + ctrl + " sc(stateless)="  + getName());
    }
}

