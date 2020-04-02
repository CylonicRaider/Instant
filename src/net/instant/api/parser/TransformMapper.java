package net.instant.api.parser;

/**
 * A Mapper that applies some sort of transformation to the return value of
 * another Mapper.
 * The transformation can be specified by creating a subclass of
 * TransformMapper or by passing a Transformer to the static of() method.
 */
public abstract class TransformMapper<F, T> implements Mapper<T> {

    /**
     * Somewhat-generic interface for arbitrary (single) object
     * transformations.
     * While a Mapper maps from ParseTree to some object type, a Transformer
     * maps from any object type to any other.
     */
    public static interface Transformer<F, T> {

        /**
         * Transform the given object.
         * Failures may be indicated by throwing a MappingException.
         */
        T transform(F value) throws MappingException;

    }

    private final Mapper<F> nested;

    /**
     * Create a new TransformMapper wrapping the given Mapper.
     * The mapper is stored in an internal field and returned by the default
     * implementation of getNestedMapper().
     */
    public TransformMapper(Mapper<F> nested) {
        this.nested = nested;
    }

    /**
     * Retrieve the Mapper to be used for "initial" mapping.
     * The default implementation returns the mapper stored by the
     * TransformMapper constructor.
     */
    protected Mapper<F> getNestedMapper() {
        return nested;
    }

    /**
     * Map the given parse tree to an object or throw an exception.
     * The default implementation does the following:
     *
     *     return transform(getNestedMapper().map(tree));
     */
    public T map(Parser.ParseTree tree) throws MappingException {
        return transform(getNestedMapper().map(tree));
    }

    /**
     * Transform the given object or throw an exception.
     * See also the Transformer interface.
     */
    protected abstract T transform(F value) throws MappingException;

    /**
     * Create a TransformMapper whose transform is defined by the given
     * Transformer.
     * The mapper is passed on to the TransformMapper constructor.
     */
    public static <F, T> TransformMapper<F, T> of(
            final Mapper<F> nested, final Transformer<F, T> transformer) {
        return new TransformMapper<F, T>(nested) {
            protected T transform(F value) throws MappingException {
                return transformer.transform(value);
            }
        };
    }

}
