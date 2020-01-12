package net.instant.util;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

public class LinkedSet<E> extends AbstractSet<E> {

    protected class Node {

        protected final E data;
        protected Node prev;
        protected Node next;

        public Node(E data) {
            this.data = data;
        }

        public void linkAfter(Node p) {
            prev = p;
            next = p.next;
            link();
        }

        public void linkBefore(Node n) {
            next = n;
            prev = n.prev;
            link();
        }

        private void link() {
            next.prev = this;
            prev.next = this;
            index.put(data, this);
        }

        public void unlink() {
            prev.next = next;
            next.prev = prev;
            index.remove(data);
        }

    }

    protected class LinkedSetIterator implements Iterator<E> {

        private Node cur;

        public LinkedSetIterator() {
            cur = sentinel;
        }

        public boolean hasNext() {
            return (getNext(cur) != sentinel);
        }

        public E next() {
            cur = getNext(cur);
            if (cur == sentinel)
                throw new NoSuchElementException("No next element");
            return cur.data;
        }

        public void remove() {
            if (cur == sentinel)
                throw new IllegalStateException("No element to remove");
            cur.unlink();
            cur = getPrev(cur);
        }

        protected Node getNext(Node n) {
            return n.next;
        }

        protected Node getPrev(Node n) {
            return n.prev;
        }

    }

    protected class ReverseIterator extends LinkedSetIterator {

        protected Node getNext(Node n) {
            return n.prev;
        }

        protected Node getPrev(Node n) {
            return n.next;
        }

    }

    private final Map<E, Node> index;
    private final Node sentinel;

    protected LinkedSet(Map<E, Node> index, Collection<? extends E> data) {
        this.index = index;
        this.sentinel = new Node(null);
        sentinel.prev = sentinel.next = sentinel;
        if (data != null) addAll(data);
    }
    public LinkedSet(Collection<? extends E> data) {
        this(new HashMap<E, Node>(), data);
    }
    public LinkedSet() {
        this(null);
    }

    public int size() {
        return index.size();
    }

    public Iterator<E> iterator() {
        return new LinkedSetIterator();
    }

    public Iterator<E> descendingIterator() {
        return new ReverseIterator();
    }

    public boolean contains(Object obj) {
        return index.containsKey(obj);
    }

    public E getFirst() {
        if (isEmpty()) throw new NoSuchElementException("LinkedSet is empty");
        return sentinel.next.data;
    }

    public E getLast() {
        if (isEmpty()) throw new NoSuchElementException("LinkedSet is empty");
        return sentinel.prev.data;
    }

    public boolean add(E elem) {
        if (contains(elem)) return false;
        new Node(elem).linkBefore(sentinel);
        return true;
    }

    public boolean addFirst(E elem) {
        if (contains(elem)) return false;
        new Node(elem).linkAfter(sentinel);
        return true;
    }

    public boolean addAllFirst(Collection<? extends E> coll) {
        boolean modified = false;
        Node oldFirst = sentinel.next;
        for (E elem : coll) {
            if (contains(elem)) continue;
            new Node(elem).linkBefore(oldFirst);
            modified = true;
        }
        return modified;
    }

    public boolean addLast(E elem) {
        return add(elem);
    }

    public boolean addAllLast(Collection<? extends E> coll) {
        return addAll(coll);
    }

    public boolean remove(Object obj) {
        Node node = index.remove(obj);
        if (node == null) return false;
        node.unlink();
        return true;
    }

    public void clear() {
        index.clear();
        sentinel.unlink();
        sentinel.prev = sentinel.next = sentinel;
    }

}
