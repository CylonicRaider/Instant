package net.instant.console;

public class BackendConsole {

    private final BackendConsoleManager parent;
    private final int id;
    private final ScriptRunner runner;
    private final CommandHistory history;

    public BackendConsole(BackendConsoleManager parent, int id) {
        this.parent = parent;
        this.id = id;
        this.runner = new ScriptRunner();
        this.history = new CommandHistory();
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

    public void close() {
        parent.remove(this);
    }

}
