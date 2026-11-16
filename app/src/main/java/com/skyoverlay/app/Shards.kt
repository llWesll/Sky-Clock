package com.skyoverlay.app

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Previsao de fragmentos (regra da comunidade, nao oficial; veja ShardPredictionRule.md).
 * Tudo no fuso do jogo (Pacifico): o dia do fragmento vai de 00:00 a 23:59 la.
 */
enum class ShardKind(val label: String) {
    BLACK("Preto"),
    RED("Vermelho"),
}

/** Um dos 5 "tipos" de dia: horario do 1o evento, intervalo entre eventos e dias sem fragmento. */
data class ShardDay(
    val kind: ShardKind,
    val startMin: Int,                 // minutos desde 00:00 (hora do Sky) do 1o evento
    val noShardDays: Set<DayOfWeek>,
    /** Local do fragmento em cada reino, na ordem de Shards.REALMS (fonte: wiki). */
    val places: List<String>,
) {
    /** Preto: 3 eventos a cada 8h. Vermelho: 3 eventos a cada 6h. */
    val intervalMin: Int get() = if (kind == ShardKind.BLACK) 8 * 60 else 6 * 60
}

object Shards {
    /** O fragmento aparece no portal e "pousa" 8min40s depois; o alerta dura esse tempo. */
    const val LAND_SEC = 8 * 60 + 40L

    /** O reino muda a cada dia do mes, nesta ordem, recomecando no dia 1 (e de novo apos o 5). */
    val REALMS = listOf("Campina", "Floresta", "Vale", "Sertão", "Relicário")

    // Locais por reino: Campina, Floresta, Vale, Sertão, Relicário (nomes da wiki pt-BR)
    private val B150 = ShardDay(ShardKind.BLACK, 1 * 60 + 50, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        listOf("Campo de Borboletas", "Riacho da Floresta", "Lago Congelado", "A Muralha Externa", "Deserto da Luz Estelar"))
    private val B210 = ShardDay(ShardKind.BLACK, 2 * 60 + 10, setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY),
        listOf("Vilarejo", "Cemitério da Floresta", "Lago Congelado", "Campo de Batalha", "Deserto da Luz Estelar"))
    private val R740 = ShardDay(ShardKind.RED, 7 * 60 + 40, setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
        listOf("Cavernas da Campina", "Lagoa Sagrada", "Aldeia dos Sonhos", "Cemitério", "Enseada das Águas-Vivas"))
    private val R220 = ShardDay(ShardKind.RED, 2 * 60 + 20, setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
        listOf("Ninho do Pássaro", "Casa na Árvore", "Aldeia dos Sonhos", "Campos de Caranguejo", "Enseada das Águas-Vivas"))
    private val R330 = ShardDay(ShardKind.RED, 3 * 60 + 30, setOf(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
        listOf("Ilhas do Santuário", "Clareira Elevada", "Desfiladeiro do Iéti", "Arca Esquecida", "Enseada das Águas-Vivas"))

    /** Ciclo de 12 dias, recomecando no dia 1 de cada mes. */
    private val CYCLE = listOf(R740, B210, R220, B150, R330, B210, R740, B150, R220, B210, R330, B150)

    /** Tipo de fragmento do dia, ou null se for um dia sem fragmento. */
    fun forDate(date: LocalDate): ShardDay? {
        val d = CYCLE[(date.dayOfMonth - 1) % CYCLE.size]
        return if (date.dayOfWeek in d.noShardDays) null else d
    }

    /** Horarios (hora do Sky) em que o fragmento aparece no portal nesse dia. */
    fun starts(date: LocalDate): List<ZonedDateTime> {
        val d = forDate(date) ?: return emptyList()
        return (0..2).map { i ->
            date.atStartOfDay().plusMinutes((d.startMin + i * d.intervalMin).toLong()).atZone(Events.SKY_ZONE)
        }
    }

    /** Fragmento que comecou e ainda nao pousou. */
    data class Alert(val kind: ShardKind, val start: ZonedDateTime, val realm: String = "", val place: String = "") {
        fun secondsToLand(now: ZonedDateTime = ZonedDateTime.now(Events.SKY_ZONE)): Long =
            LAND_SEC - Duration.between(start, now).seconds
    }

    /** Alerta ativo agora (do inicio ate o pouso), ou null. */
    fun alert(now: ZonedDateTime = ZonedDateTime.now(Events.SKY_ZONE)): Alert? {
        val date = now.withZoneSameInstant(Events.SKY_ZONE).toLocalDate()
        val day = forDate(date) ?: return null
        val realmIdx = (date.dayOfMonth - 1) % REALMS.size
        val start = starts(date).firstOrNull {
            !now.isBefore(it) && now.isBefore(it.plusSeconds(LAND_SEC))
        } ?: return null
        return Alert(day.kind, start, REALMS[realmIdx], day.places[realmIdx])
    }
}
