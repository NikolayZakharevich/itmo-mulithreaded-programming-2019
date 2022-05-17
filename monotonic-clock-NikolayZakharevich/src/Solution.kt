/**
 * В теле класса решения разрешено использовать только переменные делегированные в класс RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 */
class Solution : MonotonicClock {
    private var even1 by RegularInt(0)
    private var even2 by RegularInt(0)
    private var even3 by RegularInt(0)

    private var odd1 by RegularInt(0)
    private var odd2 by RegularInt(0)
    private var odd3 by RegularInt(0)

    private var version by RegularInt(0)

    override fun write(time: Time) {
        if (version % 2 == 0) {
            odd3 = time.d3
            odd2 = time.d2
            odd1 = time.d1
        } else {
            even3 = time.d3
            even2 = time.d2
            even1 = time.d1
        }
        version++
    }

    override fun read(): Time {
        while (true) {
            val localVersion = version
            var local1 by RegularInt(0)
            var local2 by RegularInt(0)
            var local3 by RegularInt(0)

            if (localVersion % 2 == 0) {
                local1 = even1
                local2 = even2
                local3 = even3
            } else {
                local1 = odd1
                local2 = odd2
                local3 = odd3
            }

            if (localVersion == version) {
                return Time(local1, local2, local3)
            }
        }
    }
}