package mindustry.client.antigrief

import arc.*
import arc.math.Mathf
import arc.util.Time
import mindustry.*
import mindustry.ai.types.*
import mindustry.client.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.world.*
import mindustry.world.blocks.*
import kotlin.math.abs

object TileRecords {
    private var records: Array<Array<TileRecord>> = arrayOf(arrayOf())

    fun initialize() {
        Events.on(EventType.WorldLoadEvent::class.java) {
            val startTime = Time.globalTime / 60.0 - Vars.state.tick / 60.0
            var sameMap = abs(ClientVars.lastServerStartTime - startTime) < 10 // if start time of map is within 10s of the previous start time
            sameMap = sameMap && records.isNotEmpty() && Vars.state.map.name() == ClientVars.lastServerName &&
                    Vars.world.width() == records.size && Vars.world.height() == records[0].size

            ClientVars.lastServerStartTime = startTime
            ClientVars.lastServerName = Vars.state.map.name()
            if (!ClientVars.syncing && !sameMap) records = Array(Vars.world.width()) { x -> Array(Vars.world.height()) Tile@ { y ->
                val record = TileRecord(x, y)
                val tile = Vars.world.tile(x, y)
                record.add(PreexistingTileLog(tile, tile.block(), tile.build?.rotation ?: 0, tile.build?.config(), isOrigin(tile)), tile)
                return@Tile record
            } }
        }

        Events.on(EventType.BlockBuildBeginEventBefore::class.java) {
            if (it.newBlock == null || it.newBlock == Blocks.air) {
                it.tile.getLinkedTiles { tile ->
                    addLog(tile, TileBreakLog(tile, it.unit.toInteractor(), tile.block()))
                }
            }
        }

        Events.on(EventType.BlockBuildBeginEvent::class.java) {
            if (!it.breaking && it.tile.build != null) { // TODO: Find out why the hell is tile.build sometimes null. This SHOULD NOT happen.
                it.tile.getLinkedTilesAs(it.tile.block()) { tile ->
                    addLog(tile, TilePlacedLog(tile, it.unit.toInteractor(),
                        (it.tile.build as? ConstructBlock.ConstructBuild)?.current ?: it.tile.block(),
                        it.tile.build.rotation, it.tile.build.config(), isOrigin(tile)))
                }
            }
        }

        Events.on(EventType.BlockBuildEndEvent::class.java) {
            if (it.breaking) return@on
            val record = this[it.tile] ?: return@on
            (record.logs!!.last().logs.last() as? TilePlacedLog)?.configuration = it.tile.build?.config()
        }

        Events.on(EventType.ConfigEventBefore::class.java) {
            if (it.player != null) Seer.blockConfig(it.player, it.tile.tile, it.value)
            it.tile.tile.getLinkedTiles { tile ->
                addLog(tile, ConfigureTileLog(tile, it.player.toInteractor(), tile.block(), it.tile.rotation, it.value, isOrigin(tile)))
            }
        }

        Events.on(EventType.BuildPayloadPickup::class.java) {
            it.tile.getLinkedTiles { tile ->
                addLog(tile, BlockPayloadPickupLog(tile, it.unit.toInteractor(), it.building.block))
            }
        }

        Events.on(EventType.BuildPayloadDrop::class.java) {
            it.tile.getLinkedTilesAs(it.building.block) { tile ->
                addLog(tile, BlockPayloadDropLog(tile, it.unit.toInteractor(), it.building.block, it.building.rotation, it.building.config(), isOrigin(tile)))
            }
        }

        Events.on(EventType.BlockDestroyEvent::class.java) {
            if (it.tile.team() != Vars.player.team()) return@on // Couldn't care less about enemies, especially in flood
            it.tile.getLinkedTiles { tile ->
                addLog(tile, TileDestroyedLog(tile,
                    if (tile.build is ConstructBlock.ConstructBuild) (tile.build as ConstructBlock.ConstructBuild).current ?:
                    (tile.build as ConstructBlock.ConstructBuild).previous
                    else tile.block() ?: Blocks.air))
            }
        }

        Events.on(EventType.UnitDeadEvent::class.java) {
            if(it.unit == null || it.unit.team() != Vars.player.team() || it.unit.tileOn() == null) return@on
            val controller = it.unit.controller()
            if(controller !is LogicAI && controller !is Player) return@on

            val threshold = it.unit.type.hitSize * it.unit.type.hitSize + 0.01f
            for (point in TileLog.linkedArea(it.unit.tileOn(), Mathf.ceil(it.unit.type.hitSize / Vars.tilesize))) {
                if (point in Vars.world && it.unit.within(Vars.world[point], threshold)) {
                    val tile = Vars.world[point]
                    addLog(tile, UnitDestroyedLog(tile, it.unit.toInteractor(), it.unit, controller is Player))
                }
            }
        }
    }

    operator fun get(x: Int, y: Int): TileRecord? = records.getOrNull(x)?.getOrNull(y)

    operator fun get(tile: Tile): TileRecord? = this[tile.x.toInt(), tile.y.toInt()]

    private fun addLog(tile: Tile, log: TileLog) {
        val logs = this[tile] ?: return
        logs.add(log, tile)
    }

    fun show(tile: Tile) {
        dialog("Logs") {
            cont.add(TileRecords[tile]?.toElement())
            addCloseButton()
        }.show()
    }

    private fun isOrigin(tile: Tile): Boolean {
//        return tile.build?.pos() == tile.pos()
        return tile.build?.tile == tile // Ahem. Why did this break everything
    }
}
