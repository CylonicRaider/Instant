package net.instant.console;

public interface BackendConsoleManagerMXBean {

    int[] listConsoles();

    BackendConsole getConsole(int id);

    BackendConsole newConsole();

}
