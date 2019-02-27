package net.instant.console;

public interface BackendConsoleMXBean {

    int getID();

    int getHistorySize();

    String historyEntry(int index);

    String runCommand(String command);

    long submitCommand(String command);

    void close();

}
