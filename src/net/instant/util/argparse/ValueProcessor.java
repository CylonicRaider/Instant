package net.instant.util.argparse;

/* This interface adorns Processor with a type variable to allow the "typesafe
 * map" pattern to be used in ParseResult; at the same time, it neatly
 * distinguishes those Processor subclasses that can be usefully used as
 * ParseResult keys. */
public interface ValueProcessor<T> extends Processor {}
