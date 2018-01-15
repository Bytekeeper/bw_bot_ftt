package org.fttbot

import bwem.BWEM
import bwta.BWTA
import org.fttbot.behavior.*
import org.fttbot.decision.StrategyUF
import org.fttbot.info.*
import org.fttbot.search.MCTS
import org.fttbot.sim.GameState
import org.fttbot.start.ProcessHelper
import org.fttbot.task.Task
import org.openbw.bwapi4j.*
import org.openbw.bwapi4j.type.*
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger

object FTTBot : BWEventListener {

    private val LOG = Logger.getLogger(this::class.java.simpleName)
    private lateinit var bwtaInitializer: Future<BWTA>
    private lateinit var bwemInitializer: Future<BWEM>
    val game = BW(this)
    lateinit var self: Player
    lateinit var enemy: Player

    val bwta: BWTA by lazy(LazyThreadSafetyMode.NONE) {
        bwtaInitializer.get()
    }
    val bwem: BWEM by lazy(LazyThreadSafetyMode.NONE) {
        bwemInitializer.get()
    }
    lateinit var render: MapDrawer
    var latency_frames = 0
    var frameCount = 0

    private val buildManager = BTree(
            Fallback(
                    SupplyProduction(),
                    DetectorProduction(),
                    BuildNextItemFromProductionQueue(),
                    ProduceAttacker(),
                    WorkerProduction()
            ),
            ProductionBoard)

//    private val combatManager = BehaviorTree(AttackEnemyBase())

    fun start() {
        game.startGame()
    }

    override fun onStart() {
        bwtaInitializer = CompletableFuture.supplyAsync {
            val bwta = BWTA()
            bwta.analyze()
            bwta
        }
        bwemInitializer = CompletableFuture.supplyAsync {
            val bwem = BWEM(game)
            bwem.initialize()
            bwem
        }
        self = game.interactionHandler.self()
        enemy = game.interactionHandler.enemy()
        render = game.mapDrawer

        Thread.sleep(100)
        game.interactionHandler.setLocalSpeed(0)

        val consoleHandler = ConsoleHandler()
        consoleHandler.level = Level.INFO
        Logger.getLogger("").addHandler(consoleHandler)
        Logger.getLogger("").level = Level.INFO

        val racePlayed = self.race
        when (racePlayed) {
            Race.Protoss -> FTTConfig.useConfigForProtoss()
            Race.Terran -> FTTConfig.useConfigForTerran()
            Race.Zerg -> FTTConfig.useConfigForZerg()
            else -> throw IllegalStateException("Can't handle race ${racePlayed}")
        }

        ProductionBoard.queue.addAll(listOf(
                ProductionBoard.UnitItem(UnitType.Terran_SCV),
                ProductionBoard.UnitItem(UnitType.Terran_SCV),
                ProductionBoard.UnitItem(UnitType.Terran_SCV),
                ProductionBoard.UnitItem(UnitType.Terran_SCV),
                ProductionBoard.UnitItem(UnitType.Terran_SCV),
                ProductionBoard.UnitItem(UnitType.Terran_Supply_Depot),
                ProductionBoard.UnitItem(UnitType.Terran_SCV),
                ProductionBoard.UnitItem(UnitType.Terran_SCV),
                ProductionBoard.UnitItem(UnitType.Terran_Barracks),
                ProductionBoard.UnitItem(UnitType.Terran_Refinery),
                ProductionBoard.UnitItem(UnitType.Terran_SCV),
                ProductionBoard.UnitItem(UnitType.Terran_SCV),
                ProductionBoard.UnitItem(UnitType.Terran_Marine)
        ))
        UnitQuery.update(emptyList())
    }

    override fun onFrame() {
        latency_frames = game.interactionHandler.remainingLatencyFrames
        frameCount = game.interactionHandler.frameCount

        if (game.interactionHandler.frameCount % latency_frames == 0) {
            UnitQuery.update(game.allUnits)
//        Exporter.export()
            EnemyState.step()
            Cluster.step()

            Task.step()

            ProductionBoard.updateReserved()
            buildManager.tick()
            UnitBehaviors.step()

            val upgradesInProgress = UnitQuery.myUnits.filterIsInstance(ResearchingFacility::class.java).map {
                val upgrade = it.upgradeInProgress
                upgrade.upgradeType to GameState.UpgradeState(-1, upgrade.remainingUpgradeTime, self.getUpgradeLevel(upgrade.upgradeType) + 1)
            }.toMap()
            val upgrades = UpgradeType.values()
                    .map { Pair(it, upgradesInProgress[it] ?: GameState.UpgradeState(-1, 0, self.getUpgradeLevel(it))) }.toMap().toMutableMap()
            val units = UnitQuery.myUnits.map {
                it.initialType to GameState.UnitState(-1,
                        if (!it.isCompleted && it is Building) it.remainingBuildTime
                        else if (it is TrainingFacility) it.remainingTrainTime
                        else 0)
            }.groupBy { (k, v) -> k }
                    .mapValues { it.value.map { it.second }.toMutableList() }.toMutableMap()

            val state = GameState(0, self.race, self.supplyUsed(), self.supplyTotal(), self.minerals(), self.gas(),
                    units, mutableMapOf(TechType.None to 0), upgrades)

            if (ProductionBoard.queue.isEmpty()) {
                val buildPlan = MCTS(mapOf(UnitType.Terran_Marine to 2, UnitType.Terran_Vulture to 12), setOf(), mapOf(UpgradeType.Ion_Thrusters to 1), Race.Terran)
                try {
                    repeat(400) { buildPlan.step(state) }
                    var n = buildPlan.root.children?.minBy { it.frames }
                    if (n != null) {
                        val move = n.move ?: IllegalStateException()
                        when (move) {
                            is MCTS.UnitMove -> if (!move.unit.isRefinery || !state.hasRefinery) ProductionBoard.queue.add(ProductionBoard.UnitItem(move.unit))
                            is MCTS.UpgradeMove -> ProductionBoard.queue.add(ProductionBoard.UpgradeItem(move.upgrade))
                        }
                    }
                } catch (e: IllegalStateException) {
                    LOG.log(Level.SEVERE, "Couldn't determine build order, guess it's over", e)
                }
            }
        }

        if (bwtaInitializer.isDone) {
            bwta.getRegions().forEachIndexed { index, region ->
                val poly = region.polygon.points
                for (i in 0 until poly.size) {
                    val a = poly[i] //.toVector().scl(0.9f).mulAdd(region.polygon.center.toVector(), 0.1f).toPosition()
                    val b = poly[(i + 1) % poly.size] //.toVector().scl(0.9f).mulAdd(region.polygon.center.toVector(), 0.1f).toPosition()
                    render.drawLineMap(a, b,
                            when (index % 5) {
                                0 -> Color.GREEN
                                1 -> Color.BLUE
                                2 -> Color.BROWN
                                3 -> Color.YELLOW
                                else -> Color.GREY
                            })
                }
                region.chokepoints.forEach { chokepoint ->
                    render.drawLineMap(chokepoint.sides.first, chokepoint.sides.second, Color.RED)
                }
            }
        }
        ConstructionPosition.resourcePolygons.values.forEach { poly ->
            val v = poly.vertices
            for (i in 0 until v.size / 2) {
                var j = i * 2
                val a = Position(v[j].toInt() * 32, v[j + 1].toInt() * 32)
                val b = Position(v[(j + 2) % v.size].toInt() * 32, v[(j + 3) % v.size].toInt() * 32)
                render.drawLineMap(a, b, Color.CYAN)
            }
        }
        for (unit in UnitQuery.myUnits) {
            val b = unit.userData as? BBUnit ?: continue
            render.drawTextMap(unit.position, b.status)
//            render.drawTextMap(unit.position + Position(0, -10), "% ${b.combatSuccessProbability}")
            val util = b.utility
//            render.drawTextMap(unit.position + Position(0, -40), "a ${util.attack}, d ${util.defend}, f ${util.force}")
//            render.drawTextMap(unit.position + Position(0, -30), "t ${util.threat}, v ${util.value}, c ${util.construct}")
//            render.drawTextMap(unit.position + Position(0, -20), "g ${util.gather}")
            val goal = b.goal
            when (goal) {
                is Attacking -> if (b.target != null) render.drawLineMap(unit.position, b.target!!.position, Color.RED)
                is Construction -> render.drawLineMap(unit.position, goal.position.toPosition() + Position(16, 16), Color.GREY)
            }
            b.moveTarget?.let {
                render.drawLineMap(unit.position, it, Color.BLUE)
            }
        }
        for (unit in UnitQuery.enemyUnits.filter { !it.isVisible }) {
            if (unit.position == null) continue
            render.drawCircleMap(unit.position, unit.width(), Color.RED)
            render.drawTextMap(unit.position, unit.toString())
        }
        for (unit in EnemyState.seenUnits) {
            render.drawCircleMap(unit.position, unit.width(), Color.YELLOW)
            render.drawTextMap(unit.position, unit.toString())
        }
        Cluster.mobileCombatUnits.forEach {
            render.drawCircleMap(it.position, 300, Color.ORANGE)
        }

        val t = System.currentTimeMillis()
        render.drawTextScreen(0, 20, "${System.currentTimeMillis() - t} ms")
        render.drawTextScreen(0, 30, "needDetection: ${StrategyUF.needMobileDetection()}")
    }

    override fun onUnitDestroy(unit: Unit) {
        if (unit !is PlayerUnit) return
        UnitBehaviors.removeBehavior(unit)
        EnemyState.onUnitDestroy(unit)
    }

    override fun onUnitCreate(unit: Unit) {
        if (unit !is PlayerUnit) return
        checkForStartedConstruction(unit)
    }

    private fun checkForStartedConstruction(unit: Unit) {
        if (unit is Building && unit.isMyUnit) {
            val workerWhoStartedIt = UnitQuery.myWorkers.firstOrNull {
                val construction = it.board.goal as? Construction ?: return@firstOrNull false
                construction.commissioned && construction.position == unit.tilePosition && unit.isA(construction.type)
            }
            if (workerWhoStartedIt != null) {
                val construction = workerWhoStartedIt.board.goal as Construction
                construction.building = unit
                construction.started = true
            } else {
                LOG.severe("Can't find worker associated with building ${unit}!")
            }
        }
    }

    override fun onUnitMorph(unit: Unit) {
        if (unit !is PlayerUnit) return
        checkForStartedConstruction(unit)
    }

    override fun onUnitComplete(unit: Unit) {
        if (unit !is PlayerUnit) return
        LOG.info("Completed: ${unit}")
        if (unit.isMyUnit && !UnitBehaviors.hasBehaviorFor(unit)) {
            UnitBehaviors.createTreeFor(unit)
        }
    }

    override fun onUnitShow(unit: Unit) {
        if (unit is PlayerUnit && unit.isEnemyUnit) EnemyState.onUnitShow(unit)
    }

    override fun onUnitHide(unit: Unit) {
        if (unit is PlayerUnit && unit.isEnemyUnit) EnemyState.onUnitHide(unit)
    }

    override fun onUnitRenegade(unit: Unit?) {
        // Unit changed owner
    }

    override fun onUnitDiscover(unit: Unit?) {
    }

    override fun onPlayerLeft(player: Player?) {
    }

    override fun onSendText(text: String?) {
    }

    override fun onReceiveText(player: Player?, text: String?) {
    }

    override fun onNukeDetect(target: Position?) {
    }

    override fun onSaveGame(gameName: String?) {
    }

    override fun onUnitEvade(unit: Unit?) {
    }

    override fun onEnd(isWinner: Boolean) {
        if (isWinner) LOG.info("Hurray, I won!")
        else LOG.info("Sad, I lost!")
        ProcessHelper.killStarcraftProcess()
        ProcessHelper.killChaosLauncherProcess()
        println()
        println("Exiting...")
        System.exit(0)
    }
}

fun Double.or(other: Double) = if (isNaN()) other else this