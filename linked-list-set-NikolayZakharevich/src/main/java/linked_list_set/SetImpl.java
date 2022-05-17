package linked_list_set;

import kotlinx.atomicfu.AtomicRef;

public class SetImpl implements Set {

    private final int HEAD = Integer.MIN_VALUE;

    private final int TAIL = Integer.MAX_VALUE;

    private abstract class NodeOrRemoved {

        abstract AtomicRef<NodeOrRemoved> next();

        abstract int key();

        NodeOrRemoved node() {
            return this;
        }

        boolean compareAndSet(NodeOrRemoved expectedNext, NodeOrRemoved update) {
            return (this instanceof Node) && next().compareAndSet(expectedNext, update);
        }
    }

    private final class Node extends NodeOrRemoved {

        AtomicRef<NodeOrRemoved> next = new AtomicRef<>(null);
        int key;

        Node(int key, NodeOrRemoved next) {
            this.next = new AtomicRef<>(next);
            this.key = key;
        }

        @Override
        public AtomicRef<NodeOrRemoved> next() {
            return next;
        }

        @Override
        public int key() {
            return key;
        }
    }

    private final class Removed extends NodeOrRemoved {
        final NodeOrRemoved node;

        Removed(NodeOrRemoved node) {
            this.node = node;
        }

        @Override
        public int key() {
            return node.key();
        }

        @Override
        public AtomicRef<NodeOrRemoved> next() {
            return node.next();
        }

        @Override
        NodeOrRemoved node() {
            return node;
        }
    }

    private final class Window {
        NodeOrRemoved cur, next;

        Window(NodeOrRemoved cur, NodeOrRemoved next) {
            this.cur = cur;
            this.next = next;
        }
    }

    private final NodeOrRemoved head = new Node(HEAD, new Node(TAIL, null));

    /**
     * Returns the {@link Window}, where cur.key < key <= next.key
     */
    private Window findWindow(int x) {
        unlucky:
        while (true) {
            NodeOrRemoved curr = head;
            NodeOrRemoved next = curr.next().getValue();
            while (next.key() < x) {
                NodeOrRemoved node = next.next().getValue();
                if (node instanceof Removed) {
                    if (next instanceof Node && !curr.compareAndSet(next, node.node())) {
                        continue unlucky;
                    }
                    next = node.node();
                } else {
                    curr = next;
                    next = curr.next().getValue();
                }
            }

            if (next.key() == TAIL) {
                return new Window(curr, next);
            }

            NodeOrRemoved node = next.next().getValue();
            if (node instanceof Node) {
                return new Window(curr, next);
            }
            curr.next().compareAndSet(next, node.node());
        }
    }

    @Override
    public boolean add(int x) {
        while (true) {
            Window w = findWindow(x);
            if (w.next.key() == x) {
                return false;
            }

            NodeOrRemoved node = new Node(x, w.next);
            if (w.next instanceof Node && w.cur.compareAndSet(w.next, node)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(int key) {
        while (true) {
            Window w = findWindow(key);
            if (w.next.key() != key) {
                return false;
            }

            NodeOrRemoved nextNext = w.next.next().getValue();
            if (nextNext instanceof Removed) {
                continue;
            }

            if (w.next.compareAndSet(nextNext, new Removed(nextNext))) {
                w.cur.compareAndSet(w.next, nextNext);
                return true;
            }
        }
    }

    @Override
    public boolean contains(int key) {
        return findWindow(key).next.key() == key;
    }
}