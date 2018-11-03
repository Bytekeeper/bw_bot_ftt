package org.fttbot

import bwem.area.Area
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import org.locationtech.jts.geom.Coordinate
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.WalkPosition

fun TilePosition.translated(x: Int, y: Int) = TilePosition(this.x + x, this.y + y)
fun Position.translated(x: Int, y: Int) = Position(this.x + x, this.y + y)
fun TilePosition.toVector() = Vector2(this.x.toFloat(), this.y.toFloat())
operator fun TilePosition.div(value: Int): TilePosition = TilePosition(this.x / value, this.y / value)
fun Position.toVector() = Vector2(this.x.toFloat(), this.y.toFloat())
fun Vector2.toPosition() = Position(this.x.toInt(), this.y.toInt())
operator fun Position.plus(other: Position) = Position(other.x + this.x, other.y + this.y)
operator fun Position.minus(other: Position) = Position(this.x - other.x, this.y - other.y)
infix fun Position.cross(other: Position) = x * other.y - y * other.x
operator fun Position.div(value: Int): Position = Position(this.x / value, this.y / value)


fun Position.asValidPosition() = Position(MathUtils.clamp(x, 0, FTTBot.game.bwMap.mapWidth() * 32),
        MathUtils.clamp(y, 0, FTTBot.game.bwMap.mapHeight() * 32))

fun Coordinate.toPosition() = Position(x.toInt(), y.toInt())
fun Position.toCoordinate() = Coordinate(x.toDouble(), y.toDouble())

val WalkPosition.isWalkable: Boolean
    get() = FTTBot.game.bwMap.isWalkable(this)
val Position.isValidPosition: Boolean
    get() = FTTBot.game.bwMap.isValidPosition(this)
val WalkPosition.isValidPosition: Boolean
    get() = FTTBot.game.bwMap.isValidPosition(this)
val WalkPosition.area: Area?
    get() = FTTBot.bwem.getArea(this)

operator fun Position.component1() = x
operator fun Position.component2() = y

val POSITION_ZERO = Position(0, 0)