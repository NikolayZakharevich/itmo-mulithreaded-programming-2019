class Solution : AtomicCounter {


    private val last: ThreadLocal<Node>
    private val root: Node

    init {
        root = Node(0)
        last = ThreadLocal.withInitial { root }
    }

    override fun getAndAdd(x: Int): Int {
        while (true) {
            val old = last.get().value
            val node = Node(old + x)
            last.set(last.get().next.decide(node))
            if (last.get() == node) {
               return old
            }
        }
    }

    private class Node(val value: Int) {
        val next: Consensus<Node> = Consensus()
    }
}
