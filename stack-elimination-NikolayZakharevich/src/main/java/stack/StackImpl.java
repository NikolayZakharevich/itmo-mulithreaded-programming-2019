package stack;

import kotlinx.atomicfu.AtomicRef;

import java.util.*;

public class StackImpl implements Stack {

    private static Random RANDOM = new Random();

    private final static int ELIMINATION_ARRAY_SIZE = 5;

    private final static int ELIMINATION_DONE = Integer.MIN_VALUE;

    private final static int ELIMINATION_SPIN_WAIT_ITERATIONS_COUNT = 20;

    private final static int ELIMINATION_SEEK_CELL_ITERATIONS_COUNT = 3;

    private static class Node {
        final AtomicRef<Node> next;
        final int x;

        Node(int x, Node next) {
            this.next = new AtomicRef<>(next);
            this.x = x;
        }
    }

    // head pointer
    private AtomicRef<Node> head = new AtomicRef<>(null);

    private List<AtomicRef<Integer>> eliminationArray = new ArrayList<>(
            Collections.nCopies(ELIMINATION_ARRAY_SIZE, new AtomicRef<Integer>(null)));

    @Override
    public void push(int x) {
        if (!tryEliminationPushOptimization(x)) {
            doDefaultPushImplementation(x);
        }
    }

    @Override
    public int pop() {
        Integer optimizationResult = tryEliminationPopOptimization();
        return optimizationResult != null
                ? optimizationResult
                : doDefaultPopImplementation();
    }

    private boolean tryEliminationPushOptimization(int x) {

        int size = this.eliminationArray.size();
        int position = RANDOM.nextInt(size);

        for (int nTries = 0; nTries < ELIMINATION_SEEK_CELL_ITERATIONS_COUNT; nTries++) {
            position = (position + 1) % size;

            if (this.eliminationArray.get(position).compareAndSet(null, x)) {
                for (int waitCount = 0; waitCount < ELIMINATION_SPIN_WAIT_ITERATIONS_COUNT; waitCount++) {
                    if (setEliminationPushSuccess(position)) {
                        return true;
                    }
                }
                return !setEliminationPushFail(position, x) && setEliminationPushSuccess(position);
            }
        }
        return false;
    }

    private Integer tryEliminationPopOptimization() {

        int size = this.eliminationArray.size();
        int position = RANDOM.nextInt(size);

        for (int nTries = 0; nTries < ELIMINATION_SEEK_CELL_ITERATIONS_COUNT; nTries++) {
            position = (position + 1) % size;
            Integer value = this.eliminationArray.get(position).getValue();
            if (value != null && value != ELIMINATION_DONE) {
                return setEliminationPopSuccess(position, value) ? value : null;
            }
        }
        return null;
    }

    private boolean setEliminationPushFail(int arrayPosition, int valueToPush) {
        return this.eliminationArray.get(arrayPosition).compareAndSet(valueToPush, null);
    }

    private boolean setEliminationPushSuccess(int arrayPosition) {
        Integer value = this.eliminationArray.get(arrayPosition).getValue();
        return value == ELIMINATION_DONE && this.eliminationArray.get(arrayPosition).compareAndSet(value, null);
    }

    private boolean setEliminationPopSuccess(int arrayPosition, int pickedValue) {
        return this.eliminationArray.get(arrayPosition).compareAndSet(pickedValue, ELIMINATION_DONE);
    }

    private void doDefaultPushImplementation(int x) {
        while (true) {
            Node headValue = head.getValue();
            if (head.compareAndSet(headValue, new Node(x, headValue))) {
                return;
            }
        }
    }

    private int doDefaultPopImplementation() {
        while (true) {
            Node headValue = head.getValue();
            if (headValue == null) {
                return Integer.MIN_VALUE;
            }
            if (head.compareAndSet(headValue, headValue.next.getValue())) {
                return headValue.x;
            }
        }
    }

}
