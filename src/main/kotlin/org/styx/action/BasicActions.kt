package org.styx.action

import bwapi.Position
import bwapi.TilePosition
import bwapi.UnitType
import org.styx.*


object BasicActions {
    fun move(unit: SUnit, to: Position) {
        val waypoint =
                if (unit.flying) to
                else
                    Styx.map.getPath(unit.position, to)
                            .firstOrNull { it.center.getDistance(unit.walkPosition) > 16 }
                            ?.center?.toPosition()
        unit.moveTo(waypoint ?: to)
    }

    fun follow(unit: SUnit, other: SUnit) {
        if (other.visible)
            unit.follow(other)
        else
            move(unit, other.position)
    }

    fun gather(unit: SUnit, resource: SUnit) {
        if (unit.target == resource || unit.returningResource) return
        unit.gather(resource)
    }

    fun train(unit: SUnit, type: UnitType) {
        unit.train(type)
    }

    fun morph(unit: SUnit, type: UnitType) {
        unit.morph(type)
    }

    fun build(worker: SUnit, type: UnitType, at: TilePosition) {
        val targetPosition = at.toPosition() + type.center
        if (worker.distanceTo(targetPosition) < 5 * 32) {
            worker.build(type, at)
            return
        }
        move(worker, targetPosition)
    }

    fun attack(unit: SUnit, target: SUnit) {
        unit.attack(target)
    }

    fun attack(unit: SUnit, target: Position) {
        unit.attack(target)
    }

    fun returnCargo(unit: SUnit, base: SUnit) {
        unit.rightClick(base)
    }
}