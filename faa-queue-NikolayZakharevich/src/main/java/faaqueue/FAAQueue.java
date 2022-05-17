package faaqueue;

import kotlinx.atomicfu.*;

import static faaqueue.FAAQueue.Node.NODE_SIZE;
import static kotlinx.atomicfu.AtomicFU.atomic;


public class FAAQueue<T> implements Queue<T> {
    private static final Object DONE = new Object(); // Marker for the "DONE" slot state; to avoid memory leaks

    private AtomicRef<Node> head; // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private AtomicRef<Node> tail; // Tail pointer, similarly to the Michael-Scott queue

    public FAAQueue() {
        Node dummy = new Node();
        head = new AtomicRef<>(dummy);
        tail = new AtomicRef<>(dummy);
    }

    @Override
    public void enqueue(T x) {
        while (true) {
            Node tail = this.tail.getValue();
            int enqIdx = tail.enqIdx.getAndIncrement();
            if (enqIdx > NODE_SIZE) continue;

            if (enqIdx >= NODE_SIZE) {
                Node newTail = new Node(x);
                while (true) {
                    Node oldTail = this.tail.getValue();
                    Node nextTail = oldTail.next.getValue();
                    if (nextTail != null) {
                        this.tail.compareAndSet(oldTail, nextTail);
                    }
                    if (oldTail.next.compareAndSet(null, newTail)) {
                        this.tail.compareAndSet(oldTail, newTail);
                        return;
                    }
                }
            }
            if (tail.data.get(enqIdx).compareAndSet(null, x)) return;
        }
    }

    @Override
    public T dequeue() {
        while (true) {
            Node curHead = this.head.getValue();

            if (curHead.isEmpty()) {
                Node nextHead = curHead.next.getValue();
                if (nextHead == null) return null;
                this.head.compareAndSet(curHead, nextHead);
            } else {
                int deqIdx = curHead.deqIdx.getAndIncrement();
                if (deqIdx >= NODE_SIZE) continue;
                Object res = curHead.data.get(deqIdx).getAndSet(DONE);
                if (res == null) continue;
                return (T) res;
            }
        }
    }

    static class Node {
        static final int NODE_SIZE = 2; // CHANGE ME FOR BENCHMARKING ONLY

        private AtomicRef<Node> next = new AtomicRef<>(null);
        private AtomicInt enqIdx = atomic(0); // index for the next enqueue operation
        private AtomicInt deqIdx = atomic(0); // index for the next dequeue operation
        private final AtomicArray<Object> data = new AtomicArray<>(NODE_SIZE);

        Node() {
            data.get(0).setValue(null);
        }

        Node(Object x) {
            enqIdx = atomic(1);
            data.get(0).setValue(x);
        }

        private boolean isEmpty() {
            return deqIdx.getValue() >= enqIdx.getValue() || deqIdx.getValue() >= NODE_SIZE;
        }
    }
}