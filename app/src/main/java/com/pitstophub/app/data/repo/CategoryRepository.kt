package com.pitstophub.app.data.repo

import com.pitstophub.app.data.StaticCatalog
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.CategoryData
import com.pitstophub.app.data.model.CategoryStandings
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.Team
import com.pitstophub.app.data.model.TeamBio
import com.pitstophub.app.data.remote.ApiBootstrap
import com.pitstophub.app.data.remote.ApiStandings
import com.pitstophub.app.data.remote.JRace
import com.pitstophub.app.data.remote.JolpicaClient
import com.pitstophub.app.data.remote.MotorsportsClient
import com.pitstophub.app.data.remote.SupabaseRestClient
import com.pitstophub.app.domain.CategoryRules
import com.pitstophub.app.domain.DriverRound
import com.pitstophub.app.domain.RaceTime
import com.pitstophub.app.domain.RemoteCategoryRules
import com.pitstophub.app.domain.TeamRoundPoints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Uma linha do resultado final de uma corrida (classificacao da prova). */
data class RaceResultRow(
    val position: String,
    val driver: String,
    val code: String,
    val team: String,
    val grid: String?,
    val timeOrStatus: String,
    val points: Double,
    val driverNumber: String? = null,
    /** Posicao final numerica (a coluna [position] mostra "R"/"D" para quem nao terminou). */
    val finishPosition: Int? = null,
    /** 1 = volta mais rapida da prova. */
    val fastestLapRank: Int? = null,
    val fastestLapTime: String? = null,
)

/**
 * Entrega cada categoria ja combinada: catalogo estatico como base + dados ao vivo por cima.
 * Qualquer falha de rede cai no catalogo estatico (marcado como `offline`), nunca em tela vazia.
 */
class CategoryRepository(
    private val catalog: StaticCatalog,
    private val jolpica: JolpicaClient,
    private val motorsports: MotorsportsClient,
    private val supabase: SupabaseRestClient,
    private val clock: () -> Instant = { Instant.now() },
    private val ttlMillis: Long = 10 * 60_000L,
) {
    private class Entry(val loadedAt: Instant, val data: CategoryData)
    private class BootstrapEntry(val loadedAt: Instant, val data: ApiBootstrap)

    private val mutex = Mutex()
    private val cache = HashMap<String, Entry>()
    private val resultsCache = HashMap<String, List<RaceResultRow>>()
    private val driverSeasonCache = HashMap<String, List<DriverRound>>()
    private val previousStandingsCache = HashMap<String, CategoryStandings?>()
    private val teamPointsCache = HashMap<String, List<TeamRoundPoints>>()

    private val bootstrapMutex = Mutex()
    private var bootstrapEntry: BootstrapEntry? = null
    private var inFlightBootstrap: CompletableDeferred<ApiBootstrap?>? = null

    suspend fun categories(): List<Category> = catalog.categories()

    /** Ultimo dado ao vivo em memoria (mesmo vencido), para pintar a tela na hora ao voltar a uma categoria. */
    suspend fun cached(id: String): CategoryData? = mutex.withLock { cache[id]?.data }

    /** Catalogo estatico, sem rede: serve para a primeira pintura enquanto o dado ao vivo carrega. */
    suspend fun staticData(id: String): CategoryData? =
        catalog.category(id)?.let { toData(it, live = false, liveStandings = false, offline = false).copy(catalogCalendar = it.calendar) }

    suspend fun load(id: String, force: Boolean = false): CategoryData {
        val now = clock()
        if (!force) {
            mutex.withLock { cache[id] }?.takeIf { now.toEpochMilli() - it.loadedAt.toEpochMilli() < ttlMillis }?.let { return it.data }
        }
        val base = catalog.category(id) ?: throw NoSuchElementException("Categoria desconhecida: $id")
        // O calendario do catalogo acompanha o dado ao vivo: etapas canceladas, ids do catalogo e pares de substituicao.
        val data = loadLive(base, now, force).copy(catalogCalendar = base.calendar)
        mutex.withLock { cache[id] = Entry(now, data) }
        return data
    }

    private suspend fun bootstrap(force: Boolean): ApiBootstrap? {
        val now = clock()
        val waiter: CompletableDeferred<ApiBootstrap?>
        val owner: Boolean
        bootstrapMutex.withLock {
            if (!force) {
                bootstrapEntry?.takeIf { now.toEpochMilli() - it.loadedAt.toEpochMilli() < ttlMillis }?.let { return it.data }
            }
            val current = inFlightBootstrap
            if (current != null && !force) {
                waiter = current
                owner = false
            } else {
                waiter = CompletableDeferred()
                inFlightBootstrap = waiter
                owner = true
            }
        }
        if (!owner) return waiter.await()

        val fetched = try {
            motorsports.bootstrap()
        } catch (e: CancellationException) {
            bootstrapMutex.withLock {
                if (inFlightBootstrap === waiter) inFlightBootstrap = null
            }
            waiter.cancel(e)
            throw e
        } catch (_: Exception) {
            null
        }
        val resolved = bootstrapMutex.withLock {
            if (fetched != null) bootstrapEntry = BootstrapEntry(clock(), fetched)
            if (inFlightBootstrap === waiter) inFlightBootstrap = null
            fetched ?: bootstrapEntry?.data
        }
        waiter.complete(resolved)
        return resolved
    }

    private suspend fun loadLive(base: Category, now: Instant, force: Boolean): CategoryData = try {
        if (base.id == "f1") return loadF1(base, now)
        val bs = bootstrap(force)
        val remoteRules = bs?.categories?.get(base.id)?.rules ?: bs?.rulesByCategory?.get(base.id)
        when {
            SyncedMerger.isSynced(base.id) -> loadSynced(base, bs, remoteRules)
            base.id in LiveSeriesMapper.gtwcSeriesByCategory -> loadGtwc(base, now, bs, remoteRules)
            base.id == LiveSeriesMapper.ENDURANCE_BRASIL -> loadEnduranceBrasil(base, bs, remoteRules)
            base.id in LiveSeriesMapper.brasilCategories -> loadBrasil(base, bs, remoteRules)
            else -> toData(base, live = false, liveStandings = false, offline = false, remoteRules = remoteRules)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        toData(base, live = false, liveStandings = false, offline = true)
    }

    // --- F1 (Jolpica) --------------------------------------------------------------------------

    private suspend fun resolveF1Season(base: Category, now: Instant): Pair<String, Result<List<JRace>>> {
        val currentYear = now.atZone(ZoneOffset.UTC).year
        val catalogYear = base.calendar.firstOrNull()?.date?.take(4)?.toIntOrNull() ?: currentYear
        val targetYear = maxOf(currentYear, catalogYear)
        var year = targetYear.toString()
        var racesResult = attempt { jolpica.races(year) }

        if (racesResult.getOrNull().isNullOrEmpty() && targetYear > catalogYear) {
            year = (targetYear - 1).toString()
            racesResult = attempt { jolpica.races(year) }
        }

        val races = racesResult.getOrNull().orEmpty()
        if (races.isNotEmpty()) {
            val lastDate = races.maxOf { it.date }
            val lastEnd = runCatching { Instant.parse("${lastDate}T23:59:59Z") }.getOrNull()
            if (lastEnd != null && Duration.between(lastEnd, now) > ROLLOVER_GRACE) {
                val nextYear = (year.toInt() + 1).toString()
                val nextResult = attempt { jolpica.races(nextYear) }
                if (!nextResult.getOrNull().isNullOrEmpty()) {
                    return nextYear to nextResult
                }
            }
        }
        return year to racesResult
    }

    private suspend fun loadF1(base: Category, now: Instant): CategoryData = coroutineScope {
        val (year, racesResult) = resolveF1Season(base, now)
        val winners = async { attempt { jolpica.winners(year) } }
        val drivers = async { attempt { jolpica.driverStandings(year) } }
        val constructors = async { attempt { jolpica.constructorStandings(year) } }

        val winnersResult = winners.await()
        val driversResult = drivers.await()
        val constructorsResult = constructors.await()

        val merged = F1Merger.merge(
            base = base,
            races = racesResult.getOrElse { emptyList() },
            winners = winnersResult.getOrElse { emptyMap() },
            driverStandings = driversResult.getOrElse { emptyList() },
            constructorStandings = constructorsResult.getOrElse { emptyList() },
            now = now,
        )
        val liveCalendar = racesResult.getOrNull().orEmpty().isNotEmpty()
        val liveStandings = driversResult.getOrNull().orEmpty().isNotEmpty() || constructorsResult.getOrNull().orEmpty().isNotEmpty()
        toData(
            merged,
            live = liveCalendar,
            liveStandings = liveStandings,
            offline = racesResult.isFailure || driversResult.isFailure,
        )
    }

    // --- categorias sincronizadas (/bootstrap + fallback Supabase) ------------------------------

    private suspend fun loadSynced(base: Category, bs: ApiBootstrap?, remoteRules: RemoteCategoryRules?): CategoryData = coroutineScope {
        val bsCat = bs?.categories?.get(base.id)
        val bsRaces = bsCat?.races.orEmpty().takeIf { it.isNotEmpty() }?.let(LiveSeriesMapper::engineRaces)
        val bsStandings = bsCat?.standings?.takeIf {
            it.drivers.orEmpty().any { g -> g.entries.isNotEmpty() } || it.teams.orEmpty().any { g -> g.entries.isNotEmpty() }
        }

        val racesDeferred = if (bsRaces == null && supabase.enabled) async { attempt { supabase.races(base.id) } } else null
        val standingsDeferred = if (bsStandings == null && supabase.enabled) async { attempt { supabase.standings(base.id) } } else null
        val racesResult = racesDeferred?.await()
        val standingsResult = standingsDeferred?.await()

        var merged = base
        var hasLiveCalendar = false
        if (bsRaces != null) {
            merged = SyncedMerger.mergeCalendarRaces(merged, bsRaces)
            hasLiveCalendar = true
        } else {
            val raceRows = racesResult?.getOrNull().orEmpty()
            if (raceRows.isNotEmpty()) {
                merged = SyncedMerger.mergeCalendar(merged, raceRows)
                hasLiveCalendar = true
            }
        }

        var hasLiveStandings = false
        if (bsStandings != null) {
            val prevStandings = merged.standings
            merged = SyncedMerger.mergeEngineStandings(merged, bsStandings)
            hasLiveStandings = merged.standings != null && merged.standings != prevStandings
        } else {
            val standingRows = standingsResult?.getOrNull().orEmpty()
            if (standingRows.isNotEmpty()) {
                merged = SyncedMerger.mergeStandings(merged, standingRows)
                hasLiveStandings = SyncedMerger.matchingStandings(merged, standingRows).isNotEmpty()
            }
        }

        val failed = bsRaces == null && supabase.enabled && (racesResult?.isFailure == true && standingsResult?.isFailure == true)
        toData(
            merged,
            live = hasLiveCalendar,
            liveStandings = hasLiveStandings,
            offline = failed,
            remoteRules = remoteRules,
        )
    }

    // --- GT World Challenge (api-motorsports) ---------------------------------------------------

    private suspend fun loadGtwc(base: Category, now: Instant, bs: ApiBootstrap?, remoteRules: RemoteCategoryRules?): CategoryData = coroutineScope {
        val seriesId = LiveSeriesMapper.gtwcSeriesByCategory.getValue(base.id)
        val bsData = bs?.gtwc?.get(seriesId)

        val racesResult = bsData?.races?.let { Result.success(it) } ?: attempt { motorsports.gtwcRaces(seriesId) }
        val teamsResult = bsData?.teams?.let { Result.success(it) } ?: attempt { motorsports.gtwcTeams(seriesId) }
        val standingsResult = bsData?.standings?.let { Result.success(it) } ?: attempt { motorsports.gtwcStandings(seriesId) }

        var merged = base
        var seasonChanged = false
        racesResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
            val liveCal = LiveSeriesMapper.gtwcRaces(seriesId, it, now)
            val liveYears = liveCal.map { r -> r.date.take(4) }.toSet()
            seasonChanged = merged.calendar.any { r -> r.date.take(4) !in liveYears }
            merged = merged.copy(calendar = liveCal, standings = if (seasonChanged) null else merged.standings)
        }
        teamsResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let { api ->
            val (teamList, driverList) = LiveSeriesMapper.gtwcRoster(api)
            merged = merged.copy(
                teams = teamList,
                drivers = driverList,
                calendar = merged.calendar.map { race -> race.copy(winner = race.winner?.let { matchWinner(it, driverList) }) },
            )
        }
        val liveStandings = standingsResult.getOrNull()?.let(LiveSeriesMapper::gtwcStandings)
        if (liveStandings != null) merged = merged.copy(standings = liveStandings)

        toData(
            merged,
            live = racesResult.getOrNull().orEmpty().isNotEmpty(),
            liveStandings = liveStandings != null,
            offline = racesResult.isFailure && teamsResult.isFailure,
            remoteRules = remoteRules,
        )
    }

    // --- Endurance Brasil (api-motorsports) -----------------------------------------------------

    private suspend fun loadEnduranceBrasil(base: Category, bs: ApiBootstrap?, remoteRules: RemoteCategoryRules?): CategoryData = coroutineScope {
        val bsEb = bs?.enduranceBrasil
        val races = async { bsEb?.races?.let { Result.success(it) } ?: attempt { motorsports.ebRaces() } }
        val teams = async { bsEb?.teams?.let { Result.success(it) } ?: attempt { motorsports.ebTeams() } }
        val drivers = async { bsEb?.drivers?.let { Result.success(it) } ?: attempt { motorsports.ebDrivers() } }
        val standings = async { bsEb?.standings?.let { Result.success(it) } ?: attempt { motorsports.ebStandings() } }

        var merged = base
        val racesResult = races.await()
        val teamsResult = teams.await()
        val driversResult = drivers.await()
        val standingsResult = standings.await()

        racesResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
            val liveCal = LiveSeriesMapper.ebRaces(it)
            val liveYears = liveCal.map { r -> r.date.take(4) }.toSet()
            val seasonChanged = merged.calendar.any { r -> r.date.take(4) !in liveYears }
            merged = merged.copy(calendar = liveCal, standings = if (seasonChanged) null else merged.standings)
        }
        val apiTeams = teamsResult.getOrNull().orEmpty()
        val apiDrivers = driversResult.getOrNull().orEmpty()
        if (apiTeams.isNotEmpty() || apiDrivers.isNotEmpty()) {
            val (teamList, driverList) = LiveSeriesMapper.ebRoster(apiTeams, apiDrivers)
            merged = merged.copy(teams = teamList, drivers = driverList)
        }
        val liveStandings = standingsResult.getOrNull()?.let { LiveSeriesMapper.ebStandings(it, merged) }
        if (liveStandings != null) merged = merged.copy(standings = liveStandings)

        toData(
            merged,
            live = racesResult.getOrNull().orEmpty().isNotEmpty(),
            liveStandings = liveStandings != null,
            offline = racesResult.isFailure && teamsResult.isFailure,
            remoteRules = remoteRules,
        )
    }

    // --- NASCAR Brasil e Porsche Cup Brasil (api-motorsports) -----------------------------------

    private suspend fun loadBrasil(base: Category, bs: ApiBootstrap?, remoteRules: RemoteCategoryRules?): CategoryData = coroutineScope {
        val bsCat = bs?.categories?.get(base.id)
        val races = async { bsCat?.races?.let { Result.success(it) } ?: attempt { motorsports.brasilRaces(base.id) } }
        val drivers = async { bsCat?.drivers?.let { Result.success(it) } ?: attempt { motorsports.brasilDrivers(base.id) } }
        val standings = async { bsCat?.standings?.let { Result.success(it) } ?: attempt { motorsports.brasilStandings(base.id) } }

        var merged = base
        val racesResult = races.await()
        val driversResult = drivers.await()
        val standingsResult = standings.await()

        racesResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
            val liveCal = LiveSeriesMapper.brasilRaces(it)
            val liveYears = liveCal.map { r -> r.date.take(4) }.toSet()
            val seasonChanged = merged.calendar.any { r -> r.date.take(4) !in liveYears }
            merged = merged.copy(calendar = liveCal, standings = if (seasonChanged) null else merged.standings)
        }
        driversResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let { merged = merged.copy(drivers = LiveSeriesMapper.brasilDrivers(it)) }
        val liveStandings = standingsResult.getOrNull()?.let(LiveSeriesMapper::brasilStandings)
        if (liveStandings != null) merged = merged.copy(standings = liveStandings)

        toData(
            merged,
            live = racesResult.getOrNull().orEmpty().isNotEmpty(),
            liveStandings = liveStandings != null,
            offline = racesResult.isFailure && standingsResult.isFailure,
            remoteRules = remoteRules,
        )
    }

    // --- resultados de corrida (F1 e api-motorsports) -------------------------------------------

    /** Resultado da sessao principal gravada para uma etapa das categorias servidas pela api-motorsports. */
    suspend fun engineRaceResults(categoryId: String, raceId: String): List<RaceResultRow> {
        val key = "$categoryId:$raceId"
        mutex.withLock { resultsCache[key] }?.let { return it }
        val rows = LiveSeriesMapper.pickMainSession(motorsports.engineRaceDetail(categoryId, raceId).sessions)
        if (rows.isNotEmpty()) mutex.withLock { resultsCache[key] = rows }
        return rows
    }

    suspend fun f1Results(season: String, round: Int): List<RaceResultRow> {
        val key = "$season:$round"
        mutex.withLock { resultsCache[key] }?.let { return it }
        val rows = jolpica.raceResults(season, round).map { result ->
            RaceResultRow(
                position = result.positionText?.takeIf { it.isNotBlank() } ?: result.position,
                driver = result.driver.fullName,
                code = result.driver.code ?: result.driver.familyName.take(3).uppercase(),
                team = result.constructor.name,
                grid = result.grid,
                timeOrStatus = result.time?.time ?: result.status.orEmpty(),
                points = result.points.toDoubleOrNull() ?: 0.0,
                driverNumber = result.number ?: result.driver.permanentNumber,
                finishPosition = result.position.toIntOrNull(),
                fastestLapRank = result.fastestLap?.rank?.toIntOrNull(),
                fastestLapTime = result.fastestLap?.time?.time,
            )
        }
        if (rows.isNotEmpty()) mutex.withLock { resultsCache[key] = rows }
        return rows
    }

    // --- piloto e equipe (F1) -------------------------------------------------------------------

    /** Resultado de cada etapa do piloto na temporada; vazio se a fonte nao conhece o piloto. */
    suspend fun driverSeason(driverId: String, season: String): List<DriverRound> {
        val key = "$driverId:$season"
        mutex.withLock { driverSeasonCache[key] }?.let { return it }
        val rounds = jolpica.driverSeasonResults(driverId, season).mapNotNull { race ->
            val round = race.round.toIntOrNull() ?: return@mapNotNull null
            val result = race.results?.firstOrNull()
            DriverRound(
                round = round,
                raceName = race.raceName,
                date = race.date,
                grid = result?.grid?.toIntOrNull(),
                finish = result?.position?.toIntOrNull(),
                points = result?.points?.toDoubleOrNull() ?: 0.0,
                status = result?.status.orEmpty(),
                fastestLapRank = result?.fastestLap?.rank?.toIntOrNull(),
            )
        }.sortedBy { it.round }
        if (rounds.isNotEmpty()) mutex.withLock { driverSeasonCache[key] = rounds }
        return rounds
    }

    /** Classificacao do campeonato logo apos [round] (nomes ja casados com o catalogo), ou null sem dado. */
    suspend fun standingsAfterRound(category: Category, season: String, round: Int): CategoryStandings? {
        val key = "${category.id}:$season:$round"
        mutex.withLock { if (previousStandingsCache.containsKey(key)) return previousStandingsCache[key] }
        val (drivers, constructors) = jolpica.standingsAfterRound(season, round)
        val standings = F1Merger.buildStandings(category, drivers, constructors)
        mutex.withLock { previousStandingsCache[key] = standings }
        return standings
    }

    /** Pontos da equipe em cada rodada (corrida + sprint), para a curva de evolucao. */
    suspend fun teamSeasonPoints(season: String, teamId: String): List<TeamRoundPoints> {
        val key = "$season:$teamId"
        mutex.withLock { teamPointsCache[key] }?.let { return it }
        for (constructorId in F1Merger.constructorIds(teamId)) {
            val (races, sprints) = try {
                jolpica.constructorSeason(constructorId, season)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            if (races.isEmpty()) continue
            val byRound = linkedMapOf<Int, TeamRoundPoints>()
            for (race in races) {
                val round = race.round.toIntOrNull() ?: continue
                byRound[round] = TeamRoundPoints(round, race.raceName, race.results.orEmpty().sumOf { it.points.toDoubleOrNull() ?: 0.0 })
            }
            for (sprint in sprints) {
                val round = sprint.round.toIntOrNull() ?: continue
                val extra = sprint.sprintResults.orEmpty().sumOf { it.points.toDoubleOrNull() ?: 0.0 }
                byRound[round]?.let { byRound[round] = it.copy(points = it.points + extra) }
            }
            val rounds = byRound.values.sortedBy { it.round }
            mutex.withLock { teamPointsCache[key] = rounds }
            return rounds
        }
        return emptyList()
    }

    // --- biografia ------------------------------------------------------------------------------

    suspend fun teamBio(categoryId: String, team: Team): TeamBio? =
        catalog.teamBio(categoryId, team.id) ?: TeamBios.fallback(categoryId, team)

    // --- helpers --------------------------------------------------------------------------------

    private fun toData(
        category: Category,
        live: Boolean,
        liveStandings: Boolean,
        offline: Boolean,
        remoteRules: RemoteCategoryRules? = null,
    ): CategoryData {
        val now = clock()
        val settled = category.copy(calendar = RaceTime.settle(category.calendar, now))
        val nowYear = now.atZone(ZoneOffset.UTC).year
        val resolved = CategoryRules.resolve(settled, remoteRules, fallbackYear = nowYear)
        val season = resolved.calendar.firstOrNull()?.date?.take(4) ?: nowYear.toString()
        return CategoryData(resolved, season, live, liveStandings, offline)
    }

    companion object {
        private val ROLLOVER_GRACE: Duration = Duration.ofDays(3)
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // Os sites asiaticos invertem nome/sobrenome ("Wei Lu" x "Lu Wei"); casa dos dois jeitos.
    private fun matchWinner(winner: String, drivers: List<Driver>): String =
        winner.split(Regex("\\s*/\\s*")).joinToString(" / ") { part ->
            val normalized = TextUtil.normalize(part.trim())
            val exact = drivers.firstOrNull { TextUtil.normalize(it.name) == normalized }
            val swapped = normalized.split(' ').reversed().joinToString(" ")
            (exact ?: drivers.firstOrNull { TextUtil.normalize(it.name) == swapped })?.name ?: part.trim()
        }
}
