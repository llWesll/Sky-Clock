package com.skyoverlay.app

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Eventos de cera do Sky. Os horarios seguem o fuso do jogo (Pacifico, onde o dia do Sky
 * comeca a meia-noite) e se repetem a cada 2 horas.
 *
 * >>> Se o Sky Clock mostrar minutos diferentes, e so ajustar offsetMin aqui. <<<
 */
data class SkyEvent(val name: String, val offsetMin: Int, val durationMin: Int)

object Events {
    val SKY_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")
    private const val PERIOD_SEC = 7200L

    val list = listOf(
        SkyEvent("Geyser", 5, 10),
        SkyEvent("Vovó", 35, 10),
    )

    /** active = acontecendo agora; seconds = tempo restante (se ativo) ou ate comecar. */
    data class State(val active: Boolean, val seconds: Long, val start: ZonedDateTime)

    /** O evento escolhido para aparecer no relogio, junto com o estado dele. */
    data class Pick(val event: SkyEvent, val state: State)

    fun state(e: SkyEvent, now: ZonedDateTime = ZonedDateTime.now(SKY_ZONE)): State {
        val midnight = now.toLocalDate().atStartOfDay(SKY_ZONE)
        val secs = Duration.between(midnight, now).seconds
        val o = e.offsetMin * 60L
        val d = e.durationMin * 60L
        val k = Math.floorDiv(secs - o, PERIOD_SEC)
        val startSecs = o + k * PERIOD_SEC
        return if (secs < startSecs + d) {
            State(true, startSecs + d - secs, midnight.plusSeconds(startSecs))
        } else {
            State(false, startSecs + PERIOD_SEC - secs, midnight.plusSeconds(startSecs + PERIOD_SEC))
        }
    }

    /** Se algum evento esta acontecendo, mostra ele; senao, o que esta mais perto de comecar. */
    fun pick(now: ZonedDateTime = ZonedDateTime.now(SKY_ZONE)): Pick {
        val all = list.map { Pick(it, state(it, now)) }
        return all.firstOrNull { it.state.active } ?: all.minByOrNull { it.state.seconds }!!
    }
}
