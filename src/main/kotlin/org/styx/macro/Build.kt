package org.styx.macro

import bwapi.TilePosition
import bwapi.UnitType
import org.bk.ass.bt.*
import org.bk.ass.manage.GMS
import org.bk.ass.manage.Lock
import org.styx.*
import org.styx.Styx.buildPlan
import org.styx.Styx.resources
import org.styx.Styx.units
import org.styx.action.BasicActions
import org.styx.global.PlannedUnit


data class BuildBoard(
        val type: UnitType,
        val provideLocation: () -> TilePosition? = { ConstructionPosition.findPositionFor(type) },
        var building: SUnit? = null,
        var framesBeforeStartable: Int? = null
) {
    val workerLock: UnitLock = UnitLock({ it.unitType.isWorker && UnitReservation.isAvailable(it) }) {
        val eval = closeTo(positionLock.item.toPosition())
        UnitReservation.availableItems.filter { it.unitType.isWorker }.minBy { eval(it) }
    }
    val costLock: Lock<GMS> = costLocks.unitCostLock(type)
    val positionLock = TileLock({ workerLock.item?.canBuildHere(it, type) != false }, provideLocation)

    init {
        require(type.isBuilding)
    }
}

class CancelBuild(private val board: BuildBoard) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Selector(
                    Condition { board.building?.exists != true },
                    NodeStatus.RUNNING.after {
                        board.building?.cancelBuild()
                    }
            )
}

class PrepareBuild(private val board: BuildBoard) : BehaviorTree() {
    private var hysteresisFrames = 0

    override fun getRoot(): TreeNode =
            Selector(
                    LambdaNode(this::checkForExistingBuilding),
                    Parallel(Parallel.Policy.SEQUENCE,
                            EnsureDependenciesFor(board.type),
                            Sequence(
                                    Selector(
                                            AcquireLock(board.positionLock),
                                            LambdaNode {
                                                buildPlan.plannedUnits += PlannedUnit(board.type, consumedUnit = Styx.self.race.worker)
                                                NodeStatus.RUNNING
                                            }
                                    ),
                                    LambdaNode {
                                        //                                        val oldWorker = board.workerLock.item
                                        board.workerLock.acquire()
//                                        if (oldWorker != null && board.workerLock.item != oldWorker) {
//                                            println("HOLY MOLY")
//                                        }
                                        val buildPosition = board.positionLock.item.toPosition() + board.type.center
                                        val worker = board.workerLock.item
                                        board.framesBeforeStartable = worker?.framesToTravelTo(buildPosition)
                                        board.costLock.setFutureFrames(
                                                board.framesBeforeStartable ?: 0
                                                + hysteresisFrames)
                                        board.costLock.acquire()
                                        if (worker != null && board.costLock.isSatisfiedLater) {
                                            if (worker.unitType == UnitType.Zerg_Drone) {
                                                // Add one supply for the lost worker
                                                ResourceReservation.release(null, GMS(0, 0, UnitType.Zerg_Drone.supplyRequired()))
                                            }
                                            hysteresisFrames = Config.productionHysteresisFrames
                                            NodeStatus.SUCCESS
                                        } else {
                                            hysteresisFrames = 0
                                            buildPlan.plannedUnits += PlannedUnit(board.type, consumedUnit = Styx.self.race.worker)
                                            board.workerLock.release()
                                            NodeStatus.RUNNING
                                        }
                                    }
                            )
                    )
            )

    private fun checkForExistingBuilding(): NodeStatus {
        if (board.positionLock.isSatisfied) {
            val targetLocation = board.positionLock.item
            val targetPos = targetLocation.toPosition() + board.type.center
            val candidate = units.mine.nearest(targetPos.x, targetPos.y, 128) { it.unitType == board.type}
            if (board.workerLock.item != null && candidate?.tilePosition == targetLocation && candidate.unitType == board.type) {
                board.building = candidate
                return NodeStatus.SUCCESS
            }
//            if (board.workerLock.item?.unitType == board.type) {
//                error("No building candidate found, although my old worker is ${board.workerLock.item}")
//            }
            board.building = null
        }
        return NodeStatus.FAILURE
    }

    override fun reset() {
        super.reset()
        board.workerLock.reset()
        board.building = null
        hysteresisFrames = 0
    }

}

class OrderBuild(private val board: BuildBoard) : TreeNode() {
    override fun exec(executionContext: ExecutionContext) {
        if (board.building?.exists == true) {
            success()
            return
        }
        val worker = board.workerLock.item ?: error("Worker must be locked")
        when {
            board.costLock.isSatisfied -> {
                if (!board.type.isResourceDepot && !board.type.isResourceContainer && units.my(board.type).isNotEmpty()) {
                    println("Building a second ${board.type} for whatever reasons!")
                }
                if (worker.unitType == UnitType.Zerg_Drone) {
                    // Add one supply for the lost worker
                    ResourceReservation.release(null, GMS(0, 0, UnitType.Zerg_Drone.supplyRequired()))
                }
                buildPlan.plannedUnits += PlannedUnit(board.type, 0, consumedUnit = Styx.self.race.worker)
                BasicActions.build(worker, board.type, board.positionLock.item)
            }
            board.costLock.isSatisfiedLater -> {
                buildPlan.plannedUnits += PlannedUnit(board.type, board.framesBeforeStartable, consumedUnit = Styx.self.race.worker)
                BasicActions.move(worker, board.positionLock.item.toPosition() + board.type.center)
            }
            else -> {
                buildPlan.plannedUnits += PlannedUnit(board.type, board.framesBeforeStartable, consumedUnit = Styx.self.race.worker)
                board.workerLock.release()
            }
        }
        running()
    }

    override fun exec() {
    }
}

class StartBuild(private val board: BuildBoard) : BehaviorTree() {
    constructor(type: UnitType) : this(BuildBoard(type))

    override fun getRoot() =
            Sequence(
                    PrepareBuild(board),
                    OrderBuild(board)
            )

    override fun toString(): String = "Start building $board.type at ${board.positionLock.item}"
}

class Build(private val board: BuildBoard) : BehaviorTree() {
    constructor(type: UnitType) : this(BuildBoard(type))

    override fun getRoot() = Memo(
            Selector(
                    Condition { board.building?.isCompleted == true },
                    Sequence(
                            StartBuild(board),
                            Wait
                    )
            )
    )
}