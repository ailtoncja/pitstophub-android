package com.pitstophub.app.domain

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.model.RuleSection
import com.pitstophub.app.data.model.SessionKind
import kotlinx.serialization.Serializable
import java.time.Year
import java.time.ZoneOffset

enum class RuleKind { SCORING, BALANCE, CLASSES, CAR, FUN, FORMAT }

/** Regras vindas da api-motorsports / tabela points_rules para a temporada ativa. */
@Serializable
data class RemoteCategoryRules(
    val season: Int,
    val assumed: Boolean = false,
    val sections: List<RuleSection>? = null,
    val longDescription: String? = null,
    val enLongDescription: String? = null,
)

/**
 * Adapta automaticamente as secoes da pagina "Como funciona" e a descricao da categoria conforme o ano ativo do
 * calendario, os dados vivos (cidades de Sprint, tamanho do grid, pistas do calendario), as mudancas de
 * regulamento ja confirmadas (WRC27 em 2027, Porsche 992.2 em 2027) e eventuais regras novas vindas do banco.
 *
 * Porte de `src/lib/rules.ts`.
 */
object CategoryRules {

    private val PT_NUMBERS = mapOf(
        1 to "uma",
        2 to "duas",
        3 to "três",
        4 to "quatro",
        5 to "cinco",
        6 to "seis",
        7 to "sete",
        8 to "oito",
        9 to "nove",
        10 to "dez",
    )

    private val EN_NUMBERS = mapOf(
        1 to "one",
        2 to "two",
        3 to "three",
        4 to "four",
        5 to "five",
        6 to "six",
        7 to "seven",
        8 to "eight",
        9 to "nine",
        10 to "ten",
    )

    private val SCORING_RE = Regex("pontua|point|scoring|classifica|standing")
    private val BALANCE_RE = Regex("balance|\\bbop\\b")
    private val CLASSES_RE = Regex("classe|class|divis[oõ]es|divisions|piloto e|driver and")
    private val CAR_RE = Regex("carro|\\bcars?\\b|regulamento|rally1|wrc27|brido|hybrid")
    private val FUN_RE = Regex("curiosidade|trivia|fun fact|quirk")

    fun ruleKind(title: String): RuleKind {
        val t = title.lowercase()
        return when {
            SCORING_RE.containsMatchIn(t) -> RuleKind.SCORING
            BALANCE_RE.containsMatchIn(t) -> RuleKind.BALANCE
            CLASSES_RE.containsMatchIn(t) -> RuleKind.CLASSES
            CAR_RE.containsMatchIn(t) -> RuleKind.CAR
            FUN_RE.containsMatchIn(t) -> RuleKind.FUN
            else -> RuleKind.FORMAT
        }
    }

    fun activeSeasonOf(
        category: Category,
        remoteRules: RemoteCategoryRules? = null,
        fallbackYear: Int = Year.now(ZoneOffset.UTC).value,
    ): Int {
        val activeRaces = category.calendar.filter { it.status != RaceStatus.CANCELLED }
        val pool = activeRaces.ifEmpty { category.calendar }
        val counts = LinkedHashMap<Int, Int>()
        for (race in pool) {
            val year = race.date.take(4).toIntOrNull()
            if (year != null && year > 1900) {
                counts[year] = (counts[year] ?: 0) + 1
            }
        }
        if (counts.isNotEmpty()) {
            return counts.entries
                .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
                .first()
                .key
        }
        if (remoteRules != null && !remoteRules.assumed && remoteRules.season > 1900) {
            return remoteRules.season
        }
        return fallbackYear
    }

    fun resolve(
        category: Category,
        remoteRules: RemoteCategoryRules? = null,
        fallbackYear: Int = Year.now(ZoneOffset.UTC).value,
    ): Category {
        val season = activeSeasonOf(category, remoteRules, fallbackYear)
        var sections = category.rulesFormat.map { it.copy() }
        var longDescription = category.longDescription
        var enLongDescription = category.enLongDescription

        when (category.id) {
            "f1" -> {
                val adapted = adaptF1Rules(category, season, sections)
                sections = adapted.sections
                longDescription = adapted.longDescription
                enLongDescription = adapted.enLongDescription
            }
            "wrc" -> {
                val adapted = adaptWrcRules(season, sections)
                sections = adapted.sections
                longDescription = adapted.longDescription
                enLongDescription = adapted.enLongDescription
            }
            "porsche-cup" -> {
                val adapted = adaptPorscheCupRules(category, season, sections)
                sections = adapted.sections
                longDescription = adapted.longDescription
                enLongDescription = adapted.enLongDescription
            }
            else -> sections = adaptOther2026Phrasing(category.id, season, sections)
        }

        // Sobrescritas confirmadas vindas do banco / api-motorsports especificamente para a temporada ativa.
        if (remoteRules != null && !remoteRules.assumed && remoteRules.season == season) {
            if (!remoteRules.sections.isNullOrEmpty()) {
                sections = mergeRemoteSections(sections, remoteRules.sections)
            }
            if (!remoteRules.longDescription.isNullOrBlank()) longDescription = remoteRules.longDescription
            if (!remoteRules.enLongDescription.isNullOrBlank()) enLongDescription = remoteRules.enLongDescription
        }

        return category.copy(
            rulesFormat = sections,
            longDescription = longDescription,
            enLongDescription = enLongDescription,
        )
    }

    private data class AdaptedRules(
        val sections: List<RuleSection>,
        val longDescription: String,
        val enLongDescription: String?,
    )

    private fun adaptF1Rules(category: Category, season: Int, sections: List<RuleSection>): AdaptedRules {
        val activeRaces = category.calendar.filter { it.status != RaceStatus.CANCELLED }
        val hasAnySessions = activeRaces.any { it.sessions.isNotEmpty() }
        val sprintRaces = activeRaces.filter(::hasSprintSession)

        val teamCount = if (category.teams.size >= 10) category.teams.size else 11
        val carCount = if (category.drivers.size >= 20 && category.drivers.size % 2 == 0) category.drivers.size else teamCount * 2
        val cutCount = maxOf(5, (carCount - 10) / 2)
        val cutPt = PT_NUMBERS[cutCount] ?: cutCount.toString()
        val cutEn = EN_NUMBERS[cutCount] ?: cutCount.toString()

        val sprintSentencePt: String
        val sprintSentenceEn: String
        val scoringSprintPt: String
        val scoringSprintEn: String

        if (sprintRaces.isNotEmpty()) {
            val count = sprintRaces.size
            val countPt = PT_NUMBERS[count] ?: count.toString()
            val countEn = EN_NUMBERS[count] ?: count.toString()
            val venuesPt = formatListPt(sprintRaces.map { it.location.ifBlank { it.name } })
            val venuesEn = formatListEn(sprintRaces.map { it.enLocation ?: it.location.ifBlank { it.enName ?: it.name } })
            val nounPt = if (count == 1) "etapa" else "etapas"
            val nounEn = if (count == 1) "calendar round" else "calendar rounds"
            sprintSentencePt = "Em $countPt $nounPt do calendário ($venuesPt), o fim de semana ganha o formato Sprint"
            sprintSentenceEn = "On $countEn $nounEn ($venuesEn) the weekend switches to the Sprint format"
            scoringSprintPt = "Nas $countPt corridas Sprint do calendário"
            scoringSprintEn = "On the $countEn Sprint weekends"
        } else if (season == 2026 && !hasAnySessions) {
            sprintSentencePt = "Em seis etapas do calendário (Xangai, Miami, Montreal, Silverstone, Zandvoort e Singapura), o fim de semana ganha o formato Sprint"
            sprintSentenceEn = "On six calendar rounds (Shanghai, Miami, Montreal, Silverstone, Zandvoort and Singapore) the weekend switches to the Sprint format"
            scoringSprintPt = "Nas seis corridas Sprint do calendário"
            scoringSprintEn = "On the six Sprint weekends"
        } else {
            sprintSentencePt = "Em etapas selecionadas do calendário, o fim de semana ganha o formato Sprint"
            sprintSentenceEn = "On selected calendar rounds the weekend switches to the Sprint format"
            scoringSprintPt = "Nas corridas Sprint do calendário"
            scoringSprintEn = "On Sprint weekends"
        }

        val gridSentencePt = if (season == 2026) {
            "Com a chegada da Cadillac, o grid de 2026 chega a $teamCount equipes e $carCount carros — por isso $cutPt carros, e não mais cinco, caem em cada corte."
        } else {
            "Com o grid de $teamCount equipes e $carCount carros, $cutPt carros são eliminados em cada um dos dois primeiros cortes (Q1 e Q2)."
        }

        val gridSentenceEn = if (season == 2026) {
            "With Cadillac joining, the 2026 grid grows to $teamCount teams and $carCount cars — so $cutEn cars, not five, are cut at each stage."
        } else {
            "With a grid of $teamCount teams and $carCount cars, $cutEn cars are eliminated in each of the first two knockout segments (Q1 and Q2)."
        }

        val updated = sections.map { sec ->
            when (ruleKind("${sec.title} ${sec.enTitle.orEmpty()}")) {
                RuleKind.FORMAT -> sec.copy(
                    body = "Um fim de semana padrão tem três treinos livres (FP1, FP2, FP3) e uma classificação eliminatória em três partes: Q1, Q2 e Q3, em que os 10 mais rápidos decidem a pole. $gridSentencePt $sprintSentencePt: uma Sprint Qualifying própria (SQ1 de 12 min, SQ2 de 10 min, SQ3 de 8 min, com apenas um jogo de pneus por segmento) define o grid de uma corrida curta no sábado, que não interfere na pole do domingo. Depois da classificação, os carros entram em parc fermé e a configuração mecânica não pode mais ser alterada até a corrida.",
                    enBody = "A standard weekend has three free practice sessions (FP1, FP2, FP3) and a three-part knockout qualifying (Q1, Q2 and Q3), where the fastest 10 fight for pole. $gridSentenceEn $sprintSentenceEn: a separate Sprint Qualifying (SQ1 12 min, SQ2 10 min, SQ3 8 min, one tyre set per segment) sets the grid for a short Saturday race that has no bearing on Sunday's pole. Once qualifying ends, cars enter parc fermé and their mechanical setup is frozen until the race.",
                )
                RuleKind.SCORING -> sec.copy(
                    body = "No Grande Prêmio de domingo, os 10 primeiros pontuam 25-18-15-12-10-8-6-4-2-1, com um ponto extra para quem fizer a volta mais rápida da corrida estando entre os 10 primeiros. $scoringSprintPt, pontuam apenas os oito primeiros: 8-7-6-5-4-3-2-1, sem ponto de volta rápida. O campeonato de pilotos soma os pontos de Sprint e GP; o de construtores soma os pontos dos dois carros de cada equipe.",
                    enBody = "In Sunday's Grand Prix, the top 10 score 25-18-15-12-10-8-6-4-2-1, with a bonus point for the fastest race lap if set by a top-10 finisher. $scoringSprintEn, only the top eight score: 8-7-6-5-4-3-2-1, with no fastest-lap bonus. The Drivers' Championship adds up Sprint and GP points; the Constructors' Championship adds both cars from each team.",
                )
                RuleKind.CAR -> if (season >= 2027) {
                    sec.copy(
                        title = "Carros e regulamento técnico",
                        enTitle = "Cars and technical regulations",
                        body = sec.body.replace(
                            Regex("^A geração 2026 troca a base técnica da categoria\\."),
                            "A geração técnica introduzida em 2026 define a base atual da categoria.",
                        ),
                        enBody = sec.enBody?.replace(
                            Regex("^The 2026 generation overhauls the category's technical base\\."),
                            "The technical generation introduced in 2026 defines the category's current regulations.",
                        ),
                    )
                } else sec
                RuleKind.FUN -> if (season >= 2027) {
                    sec.copy(
                        body = sec.body.replace("O DRS deixou de existir em 2026:", "O DRS deixou de existir desde a reforma técnica de 2026:"),
                        enBody = sec.enBody?.replace("DRS is gone in 2026:", "DRS was retired in the 2026 technical overhaul:"),
                    )
                } else sec
                else -> sec
            }
        }

        val longDescription = if (season >= 2027) {
            category.longDescription.replace(
                "Em 2026, novas regras de motores e aerodinâmica entram em vigor, focando em combustíveis 100% sustentáveis e maior potência elétrica.",
                "Desde 2026, o regulamento técnico combina combustíveis 100% sustentáveis, aerodinâmica ativa e motores híbridos com maior potência elétrica.",
            )
        } else {
            category.longDescription
        }

        val enLongDescription = if (season >= 2027) {
            category.enLongDescription?.replace(
                "In 2026, new engine and aerodynamic rules come into force, focusing on 100% sustainable fuels and greater electric power.",
                "Since 2026, the technical regulations combine 100% sustainable fuels, active aerodynamics and hybrid power units with greater electric power.",
            )
        } else {
            category.enLongDescription
        }

        return AdaptedRules(updated, longDescription, enLongDescription)
    }

    private fun adaptWrcRules(season: Int, sections: List<RuleSection>): AdaptedRules {
        if (season >= 2027) {
            val updated = sections.map { sec ->
                if (ruleKind("${sec.title} ${sec.enTitle.orEmpty()}") != RuleKind.CAR) return@map sec
                RuleSection(
                    title = "Carros WRC27 (Novo regulamento)",
                    enTitle = "WRC27 cars (New regulations)",
                    body = "Com o regulamento técnico WRC27 (em vigor a partir de 2027 para um ciclo de 10 anos), os carros da categoria principal substituem a antiga geração Rally1 e passam a usar uma célula de segurança tubular padronizada de alta proteção, com motor 1.6 turbo movido a combustível 100% sustentável e potência em torno de 300 a 330 cv controlada por uma curva de torque de referência igual para todo o grid.\n\nMotor, câmbio, freios e suspensão utilizam componentes derivados da categoria Rally2, com teto de custo de € 345 mil por carro para baratear a competição e atrair novas equipes. A carroceria é flexível — podendo adotar formato de hatch, sedã compacto, SUV/crossover ou um conceito de rali próprio dentro dos limites de aerodinâmica e centro de gravidade da FIA — e a homologação é aberta tanto para montadoras oficiais quanto para preparadoras independentes (tuners).",
                    enBody = "Under the WRC27 technical regulations (in force from 2027 for a 10-year cycle), top-class cars replace the former Rally1 generation with a high-protection standardised tubular safety cell, a 1.6L turbo engine running on 100% sustainable fuel and output around 300–330 hp governed by a common reference torque curve across the entire grid.\n\nEngine, transmission, brakes and suspension use Rally2-derived components under a €345,000 per-car cost cap designed to cut costs and attract new teams. Bodywork is flexible — allowing hatchback, compact sedan, crossover/SUV or bespoke rally concept shapes within FIA aerodynamic and centre-of-gravity envelopes — and homologation is open to both OEM manufacturers and independent tuners.",
                )
            }
            return AdaptedRules(
                sections = updated,
                longDescription = "O WRC é a competição de rali de mais alto nível da FIA. Pilotos e navegadores competem nos carros da geração WRC27 (introduzida em 2027 com célula de segurança padronizada, ~300 a 330 cv, combustível 100% sustentável e mecânica derivada do Rally2) em superfícies que variam de asfalto liso a cascalho bruto, neve e gelo. Cada rali é dividido em \"estágios\" cronometrados em estradas fechadas ao público. O objetivo é completar os estágios no menor tempo possível. Entre os estágios, os carros circulam em estradas públicas, devendo obedecer às leis de trânsito. O WRC exige uma habilidade de condução incomparável e uma confiança cega entre o piloto e o navegador, que lê as \"notas de ritmo\" para descrever o que vem pela frente em alta velocidade.",
                enLongDescription = "The WRC is the highest level of rally competition from the FIA. Drivers and co-drivers compete in the WRC27 generation of cars (introduced in 2027 with a standardised safety cell, ~300–330 hp, 100% sustainable fuel and Rally2-derived running gear) on surfaces ranging from smooth asphalt to rough gravel, snow and ice. Each rally is divided into timed \"stages\" on roads closed to the public. The goal is to complete the stages in the shortest time possible. Between stages, cars circulate on public roads, having to obey traffic laws. The WRC requires unparalleled driving skill and blind trust between the driver and the co-driver, who reads the \"pace notes\" to describe what lies ahead at high speed.",
            )
        }

        val updated = sections.map { sec ->
            if (ruleKind("${sec.title} ${sec.enTitle.orEmpty()}") != RuleKind.CAR) return@map sec
            RuleSection(
                title = "Carros Rally1 e transição para 2027",
                enTitle = "Rally1 cars and 2027 transition",
                body = "Na fase final do ciclo Rally1 (até o fim de 2026), os carros correm sem a unidade híbrida plug-in que usaram entre 2022 e 2024: são protótipos tubulares de tração integral com motor 1.6 turbo de cerca de 380 cv movidos a combustível 100% sustentável, mais leves e simples de manter.\n\nA partir de 2027 entra em vigor o novo regulamento técnico WRC27 (ciclo de 10 anos): a potência passa a ficar em torno de 300 a 330 cv controlada por uma curva de torque de referência igual para todos, com motor, câmbio, freios e suspensão derivados da categoria Rally2, célula de segurança padronizada e teto de custo de € 345 mil por carro — permitindo carrocerias de hatch, sedã, SUV ou conceito próprio, abertas tanto a montadoras quanto a preparadoras independentes.",
                enBody = "In the final phase of the Rally1 cycle (through the end of 2026), cars run without the plug-in hybrid unit used from 2022 to 2024: they are all-wheel-drive spaceframe prototypes powered by a ~380 hp 1.6L turbo engine on 100% sustainable fuel, lighter and simpler to run.\n\nFrom 2027, the new WRC27 technical regulations take effect for a 10-year cycle: power is set around 300–330 hp governed by a common reference torque curve, with Rally2-derived engine, transmission, brakes and suspension, a standardised safety cell and a €345,000 cost cap per car — allowing hatchback, sedan, SUV or bespoke rally concept bodywork from both manufacturers and independent tuners.",
            )
        }
        return AdaptedRules(
            sections = updated,
            longDescription = "O WRC é a competição de rali de mais alto nível da FIA. Pilotos e navegadores competem em protótipos \"Rally1\" a combustão 100% sustentável (sem a unidade híbrida usada até 2024, antes da estreia da nova geração WRC27 em 2027) em superfícies que variam de asfalto liso a cascalho bruto, neve e gelo. Cada rali é dividido em \"estágios\" cronometrados em estradas fechadas ao público. O objetivo é completar os estágios no menor tempo possível. Entre os estágios, os carros circulam em estradas públicas, devendo obedecer às leis de trânsito. O WRC exige uma habilidade de condução incomparável e uma confiança cega entre o piloto e o navegador, que lê as \"notas de ritmo\" para descrever o que vem pela frente em alta velocidade.",
            enLongDescription = "The WRC is the highest level of rally competition from the FIA. Drivers and co-drivers compete in 100% sustainable-fuel \"Rally1\" prototypes (without the hybrid unit used through 2024, ahead of the new WRC27 generation debuting in 2027) on surfaces ranging from smooth asphalt to rough gravel, snow and ice. Each rally is divided into timed \"stages\" on roads closed to the public. The goal is to complete the stages in the shortest time possible. Between stages, cars circulate on public roads, having to obey traffic laws. The WRC requires unparalleled driving skill and blind trust between the driver and the co-driver, who reads the \"pace notes\" to describe what lies ahead at high speed.",
        )
    }

    private fun adaptPorscheCupRules(category: Category, season: Int, sections: List<RuleSection>): AdaptedRules {
        val activeRaces = category.calendar.filter { it.status != RaceStatus.CANCELLED }
        val venuesPt = activeRaces.mapNotNull { it.location.ifBlank { it.name }.trim().takeIf(String::isNotEmpty) }.distinct()
        val venuesEn = activeRaces.mapNotNull {
            (it.enLocation ?: it.location.ifBlank { it.enName ?: it.name }).trim().takeIf(String::isNotEmpty)
        }.distinct()

        val carreraModel = if (season >= 2027) "911 Cup 992.2" else "911 Cup 992.1"
        val calendarSentencePt = if (venuesPt.isNotEmpty()) {
            "O calendário de $season passa por ${formatListPt(venuesPt)}."
        } else {
            "O calendário combina autódromos brasileiros tradicionais e etapas internacionais."
        }
        val calendarSentenceEn = if (venuesEn.isNotEmpty()) {
            "The $season calendar visits ${formatListEn(venuesEn)}."
        } else {
            "The calendar combines traditional Brazilian circuits with international rounds."
        }

        val updated = sections.map { sec ->
            if (sec.title != "Divisões" && sec.enTitle != "Divisions") return@map sec
            if (season >= 2027) {
                sec.copy(
                    body = "Carrera é a divisão de elite e utiliza a partir de 2027 o novo Porsche 911 GT3 Cup (geração 992.2, 510 cv, com aerodinâmica dianteira revisada, eletrônica atualizada e freios otimizados). Challenge é a divisão intermediária, com o 991.2 (485 cv), e Trophy é a divisão de entrada, com o 991.1 (460 cv), largada em fila indiana e sem disputa no Endurance.",
                    enBody = "Carrera is the top division and from 2027 runs the new Porsche 911 GT3 Cup (992.2 generation, 510 hp, featuring revised front aerodynamics, updated electronics and optimised brakes). Challenge is the intermediate division on the 991.2 (485 hp), and Trophy is the entry division on the 991.1 (460 hp), with single-file starts and no Endurance championship.",
                )
            } else sec
        }

        return AdaptedRules(
            sections = updated,
            longDescription = "A Porsche Cup Brasil corre desde 2005 com carros rigorosamente iguais, para que a diferença fique só entre os pilotos. São dois campeonatos no mesmo ano: o Sprint, com duas corridas curtas por etapa, e o Endurance, disputado em duplas ou trios em provas de 300 km e 500 km. Dentro de cada um, o grid é dividido em três níveis — Carrera (experientes, $carreraModel), Challenge (intermediário, 991.2) e Trophy (iniciantes, 991.1, que não disputa o Endurance). $calendarSentencePt",
            enLongDescription = "Porsche Cup Brasil has run since 2005 with strictly identical cars, so the difference comes down to the drivers. There are two championships in the same year: the Sprint, with two short races per round, and the Endurance, run by two- or three-driver crews over 300 km and 500 km. Within each, the grid is split into three levels — Carrera (experienced, $carreraModel), Challenge (intermediate, 991.2) and Trophy (rookies, 991.1, which does not run the Endurance). $calendarSentenceEn",
        )
    }

    private fun adaptOther2026Phrasing(categoryId: String, season: Int, sections: List<RuleSection>): List<RuleSection> {
        if (season < 2027) return sections
        return when (categoryId) {
            "f1-academy" -> sections.map { sec ->
                sec.copy(
                    body = sec.body
                        .replace(
                            "mas em 2026 duas rodadas (Montreal e Austin) ganharam uma terceira corrida, a \"Opening Race\"",
                            "mas etapas selecionadas podem contar com uma terceira corrida, a \"Opening Race\" (introduzida em 2026)",
                        )
                        .replace("A \"Opening Race\", nova em 2026,", "A \"Opening Race\", introduzida em 2026,"),
                    enBody = sec.enBody
                        ?.replace(
                            "but in 2026 two rounds (Montreal and Austin) gained a third race, the \"Opening Race\"",
                            "but selected rounds can feature a third race, the \"Opening Race\" (introduced in 2026)",
                        )
                        ?.replace("The \"Opening Race\", new for 2026,", "The \"Opening Race\", introduced in 2026,"),
                )
            }
            "nascar" -> sections.map { sec ->
                sec.copy(
                    body = sec.body.replace("A NASCAR volta em 2026 ao formato \"Chase\":", "Desde 2026, a NASCAR adota novamente o formato \"Chase\":"),
                    enBody = sec.enBody?.replace("NASCAR returns to the \"Chase\" format in 2026:", "Since 2026, NASCAR uses the \"Chase\" format:"),
                )
            }
            "indy" -> sections.map { sec ->
                sec.copy(
                    body = sec.body.replace("Em 2026, a INDYCAR aumentou", "Desde 2026, a INDYCAR aumentou"),
                    enBody = sec.enBody?.replace("For 2026, INDYCAR raised", "Since 2026, INDYCAR raised"),
                )
            }
            else -> sections
        }
    }

    private fun mergeRemoteSections(base: List<RuleSection>, remote: List<RuleSection>): List<RuleSection> {
        val result = base.toMutableList()
        for (incoming in remote) {
            val incomingTitle = incoming.title.trim().lowercase()
            val incomingEnTitle = incoming.enTitle.orEmpty().trim().lowercase()
            val incomingKind = ruleKind("${incoming.title} ${incoming.enTitle.orEmpty()}")

            var idx = result.indexOfFirst { s ->
                s.title.trim().lowercase() == incomingTitle ||
                    (incomingEnTitle.isNotEmpty() && s.enTitle.orEmpty().trim().lowercase() == incomingEnTitle)
            }
            if (idx == -1) {
                val sameKind = result.indices.filter { i ->
                    ruleKind("${result[i].title} ${result[i].enTitle.orEmpty()}") == incomingKind
                }
                if (sameKind.size == 1) idx = sameKind.single()
            }

            if (idx >= 0) {
                result[idx] = RuleSection(
                    title = incoming.title,
                    enTitle = incoming.enTitle ?: result[idx].enTitle,
                    body = incoming.body,
                    enBody = incoming.enBody ?: result[idx].enBody,
                )
            } else {
                result += RuleSection(
                    title = incoming.title,
                    enTitle = incoming.enTitle ?: incoming.title,
                    body = incoming.body,
                    enBody = incoming.enBody ?: incoming.body,
                )
            }
        }
        return result
    }

    private fun hasSprintSession(race: Race): Boolean = race.sessions.any { it.kind == SessionKind.SPRINT }

    private fun formatListPt(items: List<String>): String =
        if (items.size <= 1) items.firstOrNull().orEmpty()
        else "${items.dropLast(1).joinToString(", ")} e ${items.last()}"

    private fun formatListEn(items: List<String>): String =
        if (items.size <= 1) items.firstOrNull().orEmpty()
        else "${items.dropLast(1).joinToString(", ")} and ${items.last()}"
}
