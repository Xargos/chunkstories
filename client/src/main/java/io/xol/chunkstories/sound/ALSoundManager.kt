//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.chunkstories.sound

import org.lwjgl.openal.AL11.*
import org.lwjgl.openal.ALC11.*

import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import io.xol.chunkstories.client.ClientImplementation
import org.joml.Vector3dc
import org.joml.Vector3fc
import org.lwjgl.openal.AL
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALUtil
import org.lwjgl.system.MemoryUtil
import org.slf4j.LoggerFactory

import io.xol.chunkstories.api.client.ClientSoundManager
import io.xol.chunkstories.api.exceptions.SoundEffectNotFoundException
import io.xol.chunkstories.api.sound.SoundSource
import io.xol.chunkstories.api.sound.SoundSource.Mode
import io.xol.chunkstories.sound.ogg.SoundDataOggSample
import io.xol.chunkstories.sound.source.ALBufferedSoundSource
import io.xol.chunkstories.sound.source.ALSoundSource
import io.xol.chunkstories.sound.source.DummySoundSource

class ALSoundManager(private val client: ClientImplementation) : ClientSoundManager {
    private val library: SoundsLibrary = SoundsLibrary(client)
    private val playingSoundSources = ConcurrentLinkedQueue<ALSoundSource>()

    private val shutdownState = AtomicBoolean(false)

    private var device: Long = 0
    private var context: Long = 0

    init {
        try {
            device = alcOpenDevice(null as ByteBuffer?)
            if (device == MemoryUtil.NULL) {
                throw IllegalStateException("Failed to open the default device.")
            }

            val deviceCaps = ALC.createCapabilities(device)

            logger.info("OpenALC10: " + deviceCaps.OpenALC10)
            logger.info("OpenALC11: " + deviceCaps.OpenALC11)
            logger.info("caps.ALC_EXT_EFX = " + deviceCaps.ALC_EXT_EFX)

            if (deviceCaps.OpenALC11) {
                val devices = ALUtil.getStringList(MemoryUtil.NULL, ALC_ALL_DEVICES_SPECIFIER)
                if (devices!!.size == 0) {
                    // checkALCError(MemoryUtil.NULL);
                } else {
                    for (i in devices.indices) {
                        logger.debug(i.toString() + ": " + devices[i])
                    }
                }
            }

            val defaultDeviceSpecifier = alcGetString(MemoryUtil.NULL, ALC_DEFAULT_DEVICE_SPECIFIER)
            logger.info("Default device: " + defaultDeviceSpecifier!!)

            context = alcCreateContext(device, null as IntBuffer?)
            alcMakeContextCurrent(context)

            AL.createCapabilities(deviceCaps)

            alDistanceModel(AL_LINEAR_DISTANCE_CLAMPED)
            val alVersion = alGetString(AL_VERSION)
            val alExtensions = alGetString(AL_EXTENSIONS)

            logger.info("OpenAL context successfully created, version = " + alVersion!!)
            logger.info("OpenAL Extensions avaible : " + alExtensions!!)

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    cleanup()
                }
            })
        } catch (e: Exception) {
            logger.error("Failed to start sound system !")
            e.printStackTrace()
        }
    }

    override fun playSoundEffect(soundEffect: String, mode: Mode, position: Vector3dc?, pitch: Float, gain: Float, attenuationStart: Float, attenuationEnd: Float): SoundSource {
        try {
            val soundSource = when(mode) {
                Mode.STREAMED -> {
                    val streamingData = library.obtainBufferedSample(soundEffect) ?: throw SoundEffectNotFoundException()
                    ALBufferedSoundSource(streamingData, position, pitch, gain, attenuationStart, attenuationEnd)
                }
                else -> {
                    val sampleData = library.obtainSample(soundEffect) ?: throw SoundEffectNotFoundException()
                    ALSoundSource(sampleData, position, mode, pitch, gain, attenuationStart, attenuationEnd)
                }
            }

            addSoundSource(soundSource)
            return soundSource
        } catch (e: SoundEffectNotFoundException) {
            logger.warn("Sound not found $soundEffect")
        }

        return DummySoundSource()
    }

    override fun replicateServerSoundSource(soundEffect: String, mode: Mode, position: Vector3dc, pitch: Float,
                                            gain: Float, attenuationStart: Float, attenuationEnd: Float, UUID: Long): SoundSource? {
        try {
            val soundSource = when(mode) {
                Mode.STREAMED -> {
                    val streamingData = library.obtainBufferedSample(soundEffect) ?: throw SoundEffectNotFoundException()
                    ALBufferedSoundSource(streamingData, position, pitch, gain, attenuationStart, attenuationEnd)
                }
                else -> {
                    val sampleData = library.obtainSample(soundEffect) ?: throw SoundEffectNotFoundException()
                    ALSoundSource(sampleData, position, mode, pitch, gain, attenuationStart, attenuationEnd)
                }
            }

            // Match the UUIDs
            soundSource.uuid = UUID
            addSoundSource(soundSource)

            return soundSource
        } catch (e: SoundEffectNotFoundException) {
            logger.warn("Sound not found $soundEffect")
            return null
        }
    }

    private fun addSoundSource(soundSource: ALSoundSource) {
        soundSource.play()
        playingSoundSources.add(soundSource)
    }

    override fun getAllPlayingSounds(): List<SoundSource> {
        return playingSoundSources.toList()
    }

    fun updateAllSoundSources() {
        val result= alGetError()
        if (result != AL_NO_ERROR)
            logger.error("Error while iterating:" + SoundDataOggSample.getALErrorString(result))

        removeUnplayingSources()
        for (soundSource in playingSoundSources) {
            soundSource.update(this)
        }
    }

    override fun setListenerPosition(position: Vector3fc, lookAt: Vector3fc, up: Vector3fc) {
        val posScratch = MemoryUtil.memAllocFloat(3).put(floatArrayOf(position.x(), position.y(), position.z()))
        posScratch.flip()
        alListenerfv(AL_POSITION, posScratch)

        val rotScratch = MemoryUtil.memAllocFloat(6).put(floatArrayOf(lookAt.x(), lookAt.y(), lookAt.z(), up.x(), up.y(), up.z()))
        rotScratch.flip()
        alListenerfv(AL_ORIENTATION, rotScratch)
    }

    private fun removeUnplayingSources(): Int {
        var j = 0
        val i = playingSoundSources.iterator()
        while (i.hasNext()) {
            val soundSource = i.next()
            if (soundSource.isDonePlaying) {
                soundSource.stop()
                i.remove()
                j++
            }
        }
        return j
    }

    override fun stopAnySound(sfx: String) {
        val i = playingSoundSources.iterator()
        while (i.hasNext()) {
            val soundSource = i.next()
            if (soundSource.name.contains(sfx)) {
                soundSource.stop()
                i.remove()
            }
        }
    }

    override fun stopAnySound() {
        for (source in playingSoundSources)
            source.stop()
        playingSoundSources.clear()
    }

    fun destroy() {
        stopAnySound()
        cleanup()
    }

    private fun cleanup() {
        if (shutdownState.compareAndSet(false, true)) {
            library.cleanup()

            alcDestroyContext(context)
            alcCloseDevice(device)
            logger.info("OpenAL properly shut down.")
        }
    }

    companion object {
        val logger = LoggerFactory.getLogger("sound")!!
    }
}