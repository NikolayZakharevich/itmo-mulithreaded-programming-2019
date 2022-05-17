import java.util.concurrent.atomic.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class BlockingStackImpl<E> : BlockingStack<E> {

    // ==========================
    // Segment Queue Synchronizer
    // ==========================

    companion object {
        private val SEGMENT_SIZE = 4
        private val RESUMED = Any()
        private val SUSPENDED = Any()
    }

    class SegmentQueueSynchronizer<E> {

        class Segment<E>() {
            val data = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
            val next = AtomicReference<Segment<E>?>(null)
            val enqIdx = AtomicInteger(1)
            val deqIdx = AtomicInteger(0)

            constructor(cont: Continuation<E>) : this() {
                data.set(0, cont)
            }

            fun isEmpty() = deqIdx.get() >= enqIdx.get()
        }

        private val head: AtomicReference<Segment<E>>
        private val tail: AtomicReference<Segment<E>>

        init {
            val dummy = Segment<E>()
            head = AtomicReference(dummy)
            tail = AtomicReference(dummy)
        }

        suspend fun suspend() = suspendCoroutine<E> sc@{ cont ->
            while (true) {
                var tail = this.tail.get()
                val enqIdx = tail.enqIdx.getAndIncrement()
                if (enqIdx > SEGMENT_SIZE) continue
                if (enqIdx < SEGMENT_SIZE) {
                    if (tail.data.get(enqIdx) !== RESUMED && tail.data.compareAndSet(enqIdx, null, cont)) return@sc
                } else {
                    val newTail = Segment(cont)
                    while (true) {
                        tail = this.tail.get()
                        val next = tail.next.get()
                        if (next == null && tail.next.compareAndSet(null, newTail)) break
                        this.tail.compareAndSet(tail, next)
                    }
                    this.tail.compareAndSet(tail, newTail)
                    return@sc
                }
            }
        }

        fun resume(element: E) {
            while (true) {
                val head = this.head.get()
                if (head.isEmpty()) this.head.compareAndSet(head, head.next.get() ?: continue)
                else {
                    val deqIdx = head.deqIdx.getAndIncrement()
                    if (deqIdx >= SEGMENT_SIZE) continue;
                    ((head.data.getAndSet(deqIdx, RESUMED) ?: continue) as Continuation<E>).resume(element)
                    return
                }
            }
        }
    }

    // ==============
    // Blocking Stack
    // ==============

    private class Node<E>(val element: Any?, val next: Node<E>?)

    private val q = SegmentQueueSynchronizer<E>()
    private val head = AtomicReference<Node<E>?>(null)
    private val elements = AtomicInteger()

    override fun push(element: E) {
        if (this.elements.getAndIncrement() < 0) {
            q.resume(element)
            return
        }

        while (true) {
            val head = this.head.get()
            val newHead = Node(element, head)
            if (head?.element === SUSPENDED && this.head.compareAndSet(head, head.next)) {
                q.resume(element)
                return
            }
            if (this.head.compareAndSet(head, newHead)) return
        }
    }

    override suspend fun pop(): E {
        if (this.elements.getAndDecrement() <= 0) return q.suspend()
        while (true) {
            val head = this.head.get()
            if (head != null && this.head.compareAndSet(head, head.next)) return head.element as E
            if (head?.element === SUSPENDED && this.head.compareAndSet(head, Node(SUSPENDED, head))) return q.suspend()
            if (this.head.compareAndSet(null, Node(SUSPENDED, null))) return q.suspend()
        }
    }

}