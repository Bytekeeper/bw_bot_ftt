package org.fttbot.search

import org.fttbot.GameState
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openbw.bwapi4j.test.BWDataProvider
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import java.util.*

internal class MCTSTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            BWDataProvider.injectValues();
        }
    }

    @Test
    fun shouldFindBestBOForTerran() {
        val state = GameState(0, Race.Terran, 16, 20, 50, 0,
                mutableMapOf(UnitType.Terran_SCV to mutableListOf(
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0)
                ), UnitType.Terran_Command_Center to mutableListOf(GameState.UnitState(0, 0, 0))),
                mutableMapOf(TechType.None to 0), mutableMapOf(UpgradeType.None to GameState.UpgradeState(0, 0, 0)))
        val search = MCTS(mapOf(UnitType.Terran_Vulture to 10), setOf(), mapOf(), Race.Terran)
        val start = System.currentTimeMillis()
        repeat(100) { search.step(state) }
        val prng = Random()
        var n = search.root.children!!.minBy { it.frames }
        println("${n!!.frames} in ${System.currentTimeMillis() - start} ms");
        while (n != null) {
            println(n.move)
            n = n.children?.minBy { it.frames }
        }
    }

    @Test
    fun shouldFindBestBOForZerg() {
        val state = GameState(0, Race.Zerg, 16, 20, 50, 0,
                mutableMapOf(UnitType.Zerg_Drone to mutableListOf(
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0)
                ), UnitType.Zerg_Hatchery to mutableListOf(GameState.UnitState(0, 0, 0))),
                mutableMapOf(TechType.None to 0), mutableMapOf(UpgradeType.None to GameState.UpgradeState(0, 0, 0)))
        val search = MCTS(mapOf(UnitType.Zerg_Hydralisk to 10), setOf(), mapOf(), Race.Zerg)
        val start = System.currentTimeMillis()
        repeat(100) { search.step(state) }
        val prng = Random()
        var n = search.root.children!!.minBy { it.frames }
        println("${n!!.frames} in ${System.currentTimeMillis() - start} ms");
        while (n != null) {
            println(n.move)
            n = n.children?.minBy { it.frames }
        }
    }
}