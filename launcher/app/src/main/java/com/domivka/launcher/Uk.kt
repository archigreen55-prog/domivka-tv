package com.domivka.launcher

/** Ukrainian names, hardcoded: the TV system locale is Spanish. Indexed by Calendar.DAY_OF_WEEK - 1 (Sunday first). */
object Uk {
    val daysShort = arrayOf("Нд", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
    val daysFull = arrayOf("Неділя", "Понеділок", "Вівторок", "Середа", "Четвер", "П'ятниця", "Субота")
    val monthsNom = arrayOf(
        "Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
        "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень"
    )
    val monthsGen = arrayOf(
        "січня", "лютого", "березня", "квітня", "травня", "червня",
        "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"
    )

    fun greeting(hour: Int) = when (hour) {
        in 5..10 -> "Доброго ранку"
        in 11..16 -> "Добрий день"
        in 17..22 -> "Добрий вечір"
        else -> "Доброї ночі"
    }

    fun temp(v: Int) = when {
        v > 0 -> "+$v°"
        v < 0 -> "−${-v}°"
        else -> "0°"
    }

    fun hm(millis: Long): String {
        val c = java.util.GregorianCalendar().apply { timeInMillis = millis }
        return "%02d:%02d".format(java.util.Locale.US, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }
}
