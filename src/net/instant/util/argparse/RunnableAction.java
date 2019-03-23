package net.instant.util.argparse;

public class RunnableAction extends Action implements Runnable {

    private final Runnable task;

    public RunnableAction(Runnable task) {
        this.task = task;
    }
    protected RunnableAction() {
        this(null);
    }

    /* If the return value is null, this instance itself is the task. */
    public Runnable getTask() {
        return task;
    }

    public void parse(ArgumentSplitter source, ParseResultBuilder drain)
            throws ParsingException {
        super.parse(source, drain);
        run();
    }

    public void run() {
        getTask().run();
    }

}
