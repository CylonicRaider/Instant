package net.instant.console;

public class BackendConsole {

    private final BackendConsoleManager parent;
    private final int id;
    private final ScriptRunner runner;

    public BackendConsole(BackendConsoleManager parent, int id) {
        this.parent = parent;
        this.id = id;
        this.runner = new ScriptRunner();
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

    public void close() {
        parent.remove(this);
    }

}
