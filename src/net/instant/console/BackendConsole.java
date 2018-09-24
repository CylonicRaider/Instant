package net.instant.console;

import java.io.IOException;

public class BackendConsole {

    private final BackendConsoleManager parent;
    private final int id;
    private final ScriptRunner runner;
    private final CommandHistory history;
    private final CapturingWriter writer;

    protected BackendConsole(BackendConsoleManager parent, int id) {
        this.parent = parent;
        this.id = id;
        this.runner = new ScriptRunner();
        this.history = new CommandHistory();
        this.writer = new CapturingWriter();
        runner.redirectOutput(writer);
    }
    public BackendConsole() {
        this(null, -1);
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

    public int getHistorySize() {
        return history.size();
    }

    public String historyEntry(int index) {
        return history.get(index);
    }

    public synchronized String runCommand(String command) {
        history.add(command);
        Object result = runner.executeSafe(command);
        String resultStr = (result == null) ? "" : result.toString();
        if (result != null) {
            try {
                writer.write(resultStr + "\n");
            } catch (IOException exc) {
                throw new RuntimeException(exc);
            }
        }
        return resultStr;
    }

    public void close() {
        if (parent != null) parent.remove(this);
    }

}
