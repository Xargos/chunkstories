//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.chunkstories.client.ingame

import io.xol.chunkstories.api.GameContext
import io.xol.chunkstories.api.Location
import io.xol.chunkstories.api.client.ClientInputsManager
import io.xol.chunkstories.api.client.IngameClient
import io.xol.chunkstories.api.client.LocalPlayer
import io.xol.chunkstories.api.entity.Entity
import io.xol.chunkstories.api.entity.traits.serializable.TraitControllable
import io.xol.chunkstories.api.entity.traits.serializable.TraitInventory
import io.xol.chunkstories.api.graphics.Window
import io.xol.chunkstories.api.graphics.systems.dispatching.DecalsManager
import io.xol.chunkstories.api.item.inventory.Inventory
import io.xol.chunkstories.api.net.Packet
import io.xol.chunkstories.api.particles.ParticlesManager
import io.xol.chunkstories.api.sound.SoundManager
import io.xol.chunkstories.api.world.World
import io.xol.chunkstories.api.world.WorldClientNetworkedRemote
import io.xol.chunkstories.api.world.WorldMaster
import io.xol.chunkstories.sound.ALSoundManager
import io.xol.chunkstories.world.WorldClientCommon

class LocalPlayerImplementation(internal val client: IngameClientImplementation, internal val world: WorldClientCommon) : LocalPlayer {

    private var controlledEntity: Entity? = null

    val loadingAgent: LocalClientLoadingAgent = LocalClientLoadingAgent(client, this, world)

    override fun getInputsManager(): ClientInputsManager {
        return client.inputsManager
    }

    override fun getControlledEntity(): Entity? {
        return controlledEntity
    }

    override fun setControlledEntity(entity: Entity?): Boolean {
        val ec = entity?.traits?.get(TraitControllable::class.java)
        if (entity != null && ec != null) {
            this.subscribe(entity)

            // If a world master, directly set the entity's controller
            if (world is WorldMaster)
                ec.controller = this
            else if (entity.getWorld() is WorldClientNetworkedRemote) {
                // When changing controlled entity, first unsubscribe the remote server from the
                // one we no longer own
                if (controlledEntity != null && entity !== controlledEntity)
                    (controlledEntity!!.getWorld() as WorldClientNetworkedRemote).remoteServer
                            .unsubscribe(controlledEntity)

                // Let know the server of new changes
                (entity.getWorld() as WorldClientNetworkedRemote).remoteServer.subscribe(entity)
            }// In remote networked worlds, we need to subscribe the server to our player
            // actions to the controlled entity so he gets updates

            controlledEntity = entity
        } else if (entity == null && controlledEntity != null) {
            // Directly unset it
            if (world is WorldMaster)
                controlledEntity!!.traits[TraitControllable::class]?.let { it.controller = null }
            else if (controlledEntity!!.getWorld() is WorldClientNetworkedRemote)
                (controlledEntity!!.getWorld() as WorldClientNetworkedRemote).remoteServer
                        .unsubscribe(controlledEntity)// When loosing control over an entity, stop sending the server updates about it

            controlledEntity = null
        }

        return true
    }

    fun update() {
        loadingAgent.updateUsedWorldBits()

        controlledEntity?.let { entity ->
            val camera = entity.traits[TraitControllable::class]?.camera
            camera?.let {
                (client.soundManager as ALSoundManager).setListenerPosition(camera.position, camera.lookingAt, camera.up)
            }
        }
    }

    override fun getSoundManager(): SoundManager {
        return client.soundManager
    }

    override fun getParticlesManager(): ParticlesManager {
        return world.particlesManager
    }

    override fun getDecalsManager(): DecalsManager {
        return world.decalsManager
    }

    override fun getUUID(): Long {
        return client.user.name.hashCode().toLong()
    }

    override fun getSubscribedToList(): Iterator<Entity>? {
        // TODO Auto-generated method stub
        return null
    }

    override fun subscribe(entity: Entity): Boolean {
        // TODO Auto-generated method stub
        return false
    }

    override fun unsubscribe(entity: Entity): Boolean {
        // TODO Auto-generated method stub
        return false
    }

    override fun unsubscribeAll() {
        // TODO Auto-generated method stub

    }

    override fun pushPacket(packet: Packet) {
        // TODO Auto-generated method stub

    }

    fun isSubscribedTo(entity: Entity): Boolean {
        // TODO Auto-generated method stub
        return false
    }

    override fun hasFocus(): Boolean {
        return client.gui.hasFocus()
    }

    override fun getName(): String {
        return client.user.name
    }

    override fun getDisplayName(): String {
        return name
    }

    override fun sendMessage(msg: String) {
        client.print(msg)
    }

    override fun getLocation(): Location? {
        val controlledEntity = this.controlledEntity
        return controlledEntity?.location
    }

    override fun setLocation(l: Location) {
        val controlledEntity = this.controlledEntity
        controlledEntity?.traitLocation?.set(l)
    }

    override fun isConnected(): Boolean {
        return true
    }

    override fun hasSpawned(): Boolean {
        return controlledEntity != null
    }

    override fun getContext(): GameContext {
        return this.client
    }

    override fun getWorld(): World? {
        val controlledEntity = this.controlledEntity
        return controlledEntity?.getWorld()
    }

    override fun hasPermission(permissionNode: String): Boolean {
        return true
    }

    override fun flush() {
        // TODO Auto-generated method stub

    }

    override fun disconnect() {

    }

    override fun disconnect(disconnectionReason: String) {

    }

    override fun getWindow(): Window {
        return client.gameWindow
    }

    override fun openInventory(inventory: Inventory) {
        val entity = this.getControlledEntity()
        if (entity != null && inventory.isAccessibleTo(entity)) {
            // Directly open it without further concern
            // client.openInventories(inventory);

            val TraitInventory = entity.traits[TraitInventory::class.java]

            if (TraitInventory != null)
                client.gui.openInventories(TraitInventory, inventory)
            else
                client.gui.openInventories(inventory)
        }
        // else
        // this.sendMessage("Notice: You don't have access to this inventory.");
    }

    override fun getClient(): IngameClient {
        return client
    }
}