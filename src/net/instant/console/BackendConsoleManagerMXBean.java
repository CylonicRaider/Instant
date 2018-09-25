package net.instant.console;

public interface BackendConsoleManagerMXBean {

    int[] listConsoles();

    BackendConsoleMXBean getConsole(int id);

    BackendConsoleMXBean newConsole();

}
