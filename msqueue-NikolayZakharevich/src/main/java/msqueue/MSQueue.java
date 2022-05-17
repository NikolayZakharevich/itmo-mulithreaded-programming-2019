package msqueue;

import kotlinx.atomicfu.AtomicRef;

public class MSQueue implements Queue {
    private AtomicRef<Node> head;
    private AtomicRef<Node> tail;

    public MSQueue() {
        Node dummy = new Node(Integer.MIN_VALUE, null);
        tail = new AtomicRef<>(dummy);
        head = new AtomicRef<>(dummy);
    }

    @Override
    public void enqueue(int x) {
        Node newTail = new Node(x, null);
        while (true) {
            Node oldTail = tail.getValue();
            Node nextTail = oldTail.next.getValue();
            if (nextTail != null) {
                tail.compareAndSet(oldTail, nextTail);
            }

            if (oldTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(oldTail, newTail);
                return;
            }
        }
    }

    @Override
    public int dequeue() {
        while (true) {
            Node oldTail = tail.getValue();
            Node nextTail = oldTail.next.getValue();
            if (nextTail != null) {
                tail.compareAndSet(oldTail, nextTail);
            }

            Node curHead = head.getValue();
            Node newHead = curHead.next.getValue();
            if (newHead == null) {
                return Integer.MIN_VALUE;
            }
            if (head.compareAndSet(curHead, newHead)) {
                return newHead.x;
            }
        }
    }

    @Override
    public int peek() {
        Node first = head.getValue().next.getValue();
        return first != null ? first.x : Integer.MIN_VALUE;
    }

    private class Node {
        final int x;
        AtomicRef<Node> next;

        Node(int x, Node next) {
            this.x = x;
            this.next = new AtomicRef<>(next);
        }
    }
}