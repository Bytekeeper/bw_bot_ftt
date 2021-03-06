package org.styx.task

import bwapi.UnitType
import org.bk.ass.bt.TreeNode
import org.bk.ass.manage.GMS
import org.styx.*
import org.styx.Styx.buildPlan
import org.styx.Styx.resources
import org.styx.Styx.units
import org.styx.action.BasicActions
import kotlin.math.min

class Gathering : TreeNode() {
    private val workersLock = UnitLocks { UnitReservation.availableItems.filter { it.unitType.isWorker } }
    private val assignment = mutableMapOf<SUnit, SUnit>()

    override fun exec() {
        running()
        workersLock.reacquire()
        if (workersLock.item.isEmpty()) {// No workers left? We have serious problems
            return
        }

        val availableGMS = ResourceReservation.gms - buildPlan.unrealized.fold(GMS.ZERO) { acc, item -> acc + GMS.unitCost(item.type) * item.amount }
        val gatherGas = availableGMS.gas < 0 && units.myWorkers.count() > 6
        val workers = workersLock.item

        val extractors = units.myCompleted(UnitType.Zerg_Extractor)
        val gasWorkers = workers.count { it.gatheringGas }
        val missingGasWorkers = min((if (gatherGas) extractors.size * 3 else 0) - gasWorkers,
                if (availableGMS.gas < 0)
                    workers.size
                else
                    -gasWorkers
        )
        if (missingGasWorkers > 0) {
            workers.sortedBy { it.carrying || it.gatheringGas }
                    .take(missingGasWorkers)
                    .forEach { worker ->
                        val target = extractors.nearest(worker.x, worker.y) { ex ->
                            assignment.values.count { it == ex } < 4
                        } ?: return@forEach
                        if (worker.target != target) {
                            assignment[worker] = target
                        }
                    }
        } else if (missingGasWorkers < 0) {
            assignment.entries.filter { it.value.unitType.isRefinery }
                    .take(-missingGasWorkers)
                    .forEach {
                        assignment -= it.key
                    }
        }
        assignment.entries.removeIf { (u, m) -> !u.exists || !workers.contains(u) || !m.exists }
        workers.forEach { worker ->
            if (worker.orderTarget?.unitType?.isResourceDepot == true) {
                if (worker.gatheringMinerals)
                    assignment -= worker // reassign
                return@forEach
            }
            if (assignment[worker] == null) {
                val target = units.minerals.inRadius(worker.x, worker.y, 300) { mineral ->
                    assignment.values.count { it == mineral } == 0
                }.minBy { it.distanceTo(worker) } ?: units.minerals.nearest(worker.x, worker.y) { mineral ->
                    assignment.values.count { it == mineral } < 2
                } ?: return@forEach
                assignment[worker] = target
            }
            val assigned = assignment[worker] ?: return@forEach
            if (assigned != worker.orderTarget) {
                BasicActions.gather(worker, assigned)
            }
        }
        return
    }
}