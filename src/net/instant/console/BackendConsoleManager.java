package net.instant.console;

import java.util.HashMap;
import java.util.Map;
import net.instant.InstantRunner;
import net.instant.Main;

public class BackendConsoleManager {

    private final Main main;
    private final InstantRunner runner;
    private final Map<Integer, BackendConsole> consoles;
    private int nextID;

    public BackendConsoleManager(Main main, InstantRunner runner) {
        if (runner == null && main != null) runner = main.getRunner();
        this.main = main;
        this.runner = runner;
        this.consoles = new HashMap<Integer, BackendConsole>();
        this.nextID = 1;
    }

    public Main getMain() {
        return main;
    }

    public InstantRunner getRunner() {
        return runner;
    }

    public synchronized int[] listConsoles() {
        Integer[] result = consoles.keySet().toArray(
            new Integer[consoles.size()]);
        int[] ret = new int[result.length];
        for (int i = 0; i < result.length; i++) ret[i] = result[i];
        return ret;
    }

    public synchronized BackendConsole getConsole(int id) {
        return consoles.get(id);
    }

    public synchronized BackendConsole newConsole() {
        int id = nextID++;
        BackendConsole ret = new BackendConsole(this, id);
        consoles.put(id, ret);
        return ret;
    }

    protected synchronized void remove(BackendConsole console) {
        consoles.remove(console.getID());
    }

}
