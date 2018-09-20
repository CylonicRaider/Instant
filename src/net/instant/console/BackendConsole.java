package net.instant.console;

public class BackendConsole {

    private final BackendConsoleManager parent;
    private final int id;
    private final ScriptRunner runner;
    private final CommandHistory history;
    private final CapturingWriter writer;

    public BackendConsole(BackendConsoleManager parent, int id) {
        this.parent = parent;
        this.id = id;
        this.runner = new ScriptRunner();
        this.history = new CommandHistory();
        this.writer = new CapturingWriter();
        runner.redirectOutput(writer);
    }

    public BackendConsoleManager getParent() {
        return parent;
    }

    public int getID() {
        return id;
    }

    public ScriptRunner getRunner() {
        return runner;
    }

    public CommandHistory getHistory() {
        return history;
    }

    public CapturingWriter getWriter() {
        return writer;
    }

    public void close() {
        parent.remove(this);
    }

}
