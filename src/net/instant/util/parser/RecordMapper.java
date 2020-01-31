package net.instant.util.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class RecordMapper<T> implements Mapper<T> {

    public static class Result {

        private final Map<Mapper<?>, Object> data;

        protected Result(Map<Mapper<?>, Object> data) {
            this.data = data;
        }
        protected Result() {
            this(new HashMap<Mapper<?>, Object>());
        }

        protected Map<Mapper<?>, Object> getData() {
            return Collections.unmodifiableMap(data);
        }

        public <T> T get(Mapper<T> mapper) {
            @SuppressWarnings("unchecked")
            T ret = (T) data.get(mapper);
            return ret;
        }
        protected <T> void put(Mapper<T> mapper, T item) {
            data.put(mapper, item);
        }
        protected void remove(Mapper<?> mapper) {
            data.remove(mapper);
        }

    }

    public static abstract class WrapperMapper<C, T> extends RecordMapper<T> {

        private final Mapper<C> wrapped;

        public WrapperMapper(Mapper<C> wrapped) {
            this.wrapped = wrapped;
            add(wrapped);
        }

        protected T mapInner(Result res) {
            return process(res.get(wrapped));
        }

        protected abstract T process(C value);

    }

    private final List<Mapper<?>> mappers;

    public RecordMapper() {
        mappers = new ArrayList<Mapper<?>>();
    }

    protected List<Mapper<?>> getRawMappers() {
        return mappers;
    }
    public List<Mapper<?>> getMappers() {
        return Collections.unmodifiableList(mappers);
    }

    protected <T> Mapper<T> add(Mapper<T> m) {
        mappers.add(m);
        return m;
    }

    private <T> void mapAndPut(Mapper<T> mapper, Parser.ParseTree pt,
                               Result drain) {
        drain.put(mapper, mapper.map(pt));
    }
    public T map(Parser.ParseTree pt) {
        if (pt.childCount() != mappers.size())
            throw new IllegalArgumentException(
                "Incorrect child amount in parse tree");
        Result res = new Result();
        for (int i = 0; i < mappers.size(); i++) {
            mapAndPut(mappers.get(i), pt.childAt(i), res);
        }
        return mapInner(res);
    }

    protected abstract T mapInner(Result res);

    public static <T> RecordMapper<T> wrap(final Mapper<T> mapper) {
        return new WrapperMapper<T, T>(mapper) {
            protected T process(T value) {
                return value;
            }
        };
    }

}
