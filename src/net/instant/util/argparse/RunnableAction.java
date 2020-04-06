package net.instant.util.argparse;

public class RunnableAction extends Action implements Runnable {

    private final Runnable task;

    public RunnableAction(Runnable task) {
        this.task = task;
    }
    protected RunnableAction() {
        this.task = this;
    }

    public Runnable getTask() {
        return task;
    }

    public void parse(ArgumentSplitter source, ParseResultBuilder drain)
            throws ParsingException {
        super.parse(source, drain);
        run();
    }

    public void run() {
        Runnable task = getTask();
        if (task == this)
            throw new IllegalStateException("Using RunnableAction without " +
                "specifying Runnable or overriding run()");
        task.run();
    }

}
