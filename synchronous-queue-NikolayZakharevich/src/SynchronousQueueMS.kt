import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SynchronousQueueMS<E>() : SynchronousQueue<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(AtomicReference(null))
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override suspend fun send(element: E) {

        val offer = Data(AtomicReference(element))

        while (true) {
            val t = tail.get()
            var h = head.get()
            if (t == h || t is Data) {
                val n = t.next.get()
                if (t == tail.get()) {
                    if (n != null) {
                        casTail(t, n)
                    } else if (t.casNext(n, offer)) {
                        casTail(t, offer)
                        suspendCoroutine<Unit> { cont ->
                            offer.cont = cont
                        }
                        h = head.get()
                        if (offer == h.next.get()) {
                            casHead(h, offer)
                        }
                        return
                    }
                }
            } else {
                val n = h.next.get()
                if (t != tail.get() || h != head.get() || n == null) {
                    continue
                }
                if (n.cont == null) continue
                val success = n.casData(null, element)
                casHead(h, n)
                if (success) {
                    n.cont!!.resume(Unit)
                    return
                }
            }
        }
    }

    override suspend fun receive(): E {

        val receiver = Fulfilling<E>()

        while (true) {

            val t = tail.get()
            var h = head.get()
            if (t == h || t is Fulfilling) {
                val n = t.next.get()
                if (t == tail.get()) {
                    if (n != null) {
                        casTail(t, n)
                    } else if (t.casNext(n, receiver)) {
                        casTail(t, receiver)
                        suspendCoroutine<Unit> { cont ->
                            receiver.cont = cont
                        }
                        val elem = receiver.data.get()!!
                        h = head.get()
                        if (receiver == h.next.get()) {
                            casHead(h, receiver)
                        }
                        return elem
                    }
                }
            } else {
                val n = h.next.get()
                if (t != tail.get() || h != head.get() || n == null) {
                    continue
                }

                val elem = n.data.get()
                if (elem == null) continue
                if (n.cont == null) continue

                val success = n.casData(elem, null)
                casHead(h, n)
                if (success) {
                    n.cont!!.resume(Unit)
                    return elem
                }
            }
        }
    }

    private fun casHead(expected: Node<E>, new: Node<E>): Boolean {
        return head.compareAndSet(expected, new)
    }

    private fun casTail(expected: Node<E>, new: Node<E>): Boolean {
        return tail.compareAndSet(expected, new)
    }


    open class Node<E>(val data: AtomicReference<E?>, val next: AtomicReference<Node<E>?> = AtomicReference(null)) {

        var cont: Continuation<Unit>? = null

        fun casData(expected: E?, new: E?): Boolean {
            return data.compareAndSet(expected, new)
        }

        fun casNext(expected: Node<E>?, new: Node<E>): Boolean {
            return next.compareAndSet(expected, new)
        }
    }

    class Data<E>(data: AtomicReference<E?>, next: AtomicReference<Node<E>?> = AtomicReference(null)) : Node<E>(data, next)
    class Fulfilling<E>(next: AtomicReference<Node<E>?> = AtomicReference(null)) : Node<E>(AtomicReference(null), next)
}
