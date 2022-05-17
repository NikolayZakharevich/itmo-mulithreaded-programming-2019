package dijkstra

import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> o1!!.distance.compareTo(o2!!.distance)
}

// Returns `Integer.MAX_VALUE` if a path has not been found.
fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()

    val q = PriorityMultiQueue(workers)
    start.distance = 0
    q.add(start)
    val activeNodes = AtomicInteger(1)
    val onFinish = Phaser(workers + 1) // `arrive()` should be invoked at the end by each worker

    repeat(workers) {
        thread {
            while (true) {
                if (activeNodes.get() == 0) {
                    break
                }
                val cur: Node = q.poll() ?: continue
                for (e in cur.outgoingEdges) {
                    while (true) {
                        val oldDistance = e.to.distance
                        val relaxedDistance = cur.distance + e.weight
                        if (relaxedDistance >= oldDistance) {
                            break
                        }
                        if (e.to.casDistance(oldDistance, relaxedDistance)) {
                            q.add(e.to)
                            activeNodes.incrementAndGet()
                        }
                    }
                }
                activeNodes.decrementAndGet()
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}

class PriorityMultiQueue(workers: Int, private val comparator: Comparator<Node> = NODE_DISTANCE_COMPARATOR) {

    private val random: Random = Random()

    private var heaps: List<PriorityQueue<Node>> = ArrayList(
            Collections.nCopies(2 * workers, PriorityQueue(NODE_DISTANCE_COMPARATOR)))

    fun add(node: Node) {
        val index = random.nextInt(heaps.size)
        synchronized(heaps[index]) {
            heaps[index].add(node)
        }
    }

    fun poll(): Node? {
        var firstIndex = random.nextInt(heaps.size)
        var secondIndex: Int
        do {
            secondIndex = random.nextInt(heaps.size)
        } while (firstIndex == secondIndex)

        if (firstIndex > secondIndex) {
            firstIndex = secondIndex.also { secondIndex = firstIndex }
        }
        synchronized(heaps[firstIndex]) {
            synchronized(heaps[secondIndex]) {
                val firstElem: Node? = heaps[firstIndex].peek() ?: return heaps[secondIndex].poll()
                val secondElem: Node? = heaps[secondIndex].peek() ?: return heaps[firstIndex].poll()
                return if (comparator.compare(firstElem, secondElem) < 0)
                    heaps[firstIndex].poll() else heaps[secondIndex].poll()
            }
        }
    }
}