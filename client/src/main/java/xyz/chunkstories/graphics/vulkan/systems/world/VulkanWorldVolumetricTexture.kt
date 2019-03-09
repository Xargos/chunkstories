package xyz.chunkstories.graphics.vulkan.systems.world

import org.joml.Vector3dc
import org.joml.Vector3i
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.memAlloc
import org.lwjgl.system.MemoryUtil.memFree
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferImageCopy
import org.lwjgl.vulkan.VkImageMemoryBarrier
import xyz.chunkstories.api.graphics.TextureFormat
import xyz.chunkstories.api.graphics.structs.InterfaceBlock
import xyz.chunkstories.api.util.kotlin.toVec3i
import xyz.chunkstories.api.voxel.VoxelFormat
import xyz.chunkstories.api.voxel.VoxelSide
import xyz.chunkstories.graphics.common.Cleanable
import xyz.chunkstories.graphics.vulkan.VulkanGraphicsBackend
import xyz.chunkstories.graphics.vulkan.buffers.VulkanBuffer
import xyz.chunkstories.graphics.vulkan.memory.MemoryUsagePattern
import xyz.chunkstories.graphics.vulkan.textures.VulkanTexture3D
import xyz.chunkstories.graphics.vulkan.util.createFence
import xyz.chunkstories.graphics.vulkan.util.waitFence
import xyz.chunkstories.world.WorldClientCommon
import xyz.chunkstories.world.chunk.CubicChunk
import java.nio.ByteBuffer

class VulkanWorldVolumetricTexture(val backend: VulkanGraphicsBackend, val world: WorldClientCommon) : Cleanable {
    val size = 128
    val texture = VulkanTexture3D(backend, TextureFormat.RGBA_8, size, size, size, VK_IMAGE_USAGE_SAMPLED_BIT or VK_IMAGE_USAGE_TRANSFER_DST_BIT)

    val chunksSidesCount = size / 32
    val singleChunkSizeInRam = 32 * 32 * 32 * 4
    val scratchByteBuffer = memAlloc(chunksSidesCount * chunksSidesCount * chunksSidesCount * singleChunkSizeInRam)

    val info = VolumetricTextureMetadata()

    init {
        texture.transitionLayout(VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
    }

    val lastPos = Vector3i(0)

    fun updateArround(position: Vector3dc) {
        val operationsPool = backend.logicalDevice.graphicsQueue.threadSafePools.get()
        val commandBuffer = operationsPool.createOneUseCB()

        stackPush().use {
            scratchByteBuffer.clear()

            val positioni = position.toVec3i()
            val chunkPositionX = (positioni.x + 16) / 32
            val chunkPositionY = (positioni.y + 16) / 32
            val chunkPositionZ = (positioni.z + 16) / 32

            val chunkStartX = chunkPositionX - chunksSidesCount / 2
            val chunkStartY = chunkPositionY - chunksSidesCount / 2
            val chunkStartZ = chunkPositionZ - chunksSidesCount / 2

            info.baseChunkPos.x = chunkStartX
            info.baseChunkPos.y = chunkStartY
            info.baseChunkPos.z = chunkStartZ

            if(info.baseChunkPos == lastPos)
                return

            lastPos.set(info.baseChunkPos)

            info.size = size

            val preUpdateBarrier = VkImageMemoryBarrier.callocStack(1).apply {
                sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)

                oldLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)

                srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)

                image(texture.imageHandle)

                subresourceRange().apply {
                    aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    baseMipLevel(0)
                    levelCount(1)
                    baseArrayLayer(0)
                    layerCount(1)
                }

                srcAccessMask(0)
                dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
            }
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, preUpdateBarrier)

            val copies = VkBufferImageCopy.callocStack(chunksSidesCount * chunksSidesCount * chunksSidesCount)
            var copiesCount = 0
            for (x in 0 until chunksSidesCount)
                for (y in 0 until chunksSidesCount)
                    for (z in 0 until chunksSidesCount) {
                        val chunk = world.getChunk(chunkStartX + x, chunkStartY + y, chunkStartZ + z)
                        if (chunk == null)
                            continue

                        copies[copiesCount++].apply {
                            bufferOffset(scratchByteBuffer.position().toLong())

                            // tightly packed
                            bufferRowLength(0)
                            bufferImageHeight(0)

                            imageSubresource().apply {
                                aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                mipLevel(0)
                                baseArrayLayer(0)
                                layerCount(1)
                            }

                            imageOffset().apply {
                                x((chunk.chunkX % chunksSidesCount) * 32)
                                y((chunk.chunkY % chunksSidesCount) * 32)
                                z((chunk.chunkZ % chunksSidesCount) * 32)
                            }

                            imageExtent().apply {
                                width(32)
                                height(32)
                                depth(32)
                            }
                        }

                        extractChunkInBuffer(scratchByteBuffer, chunk)
                    }

            copies.position(0)
            copies.limit(copiesCount)

            scratchByteBuffer.flip()
            val scratchVkBuffer = VulkanBuffer(backend, scratchByteBuffer, VK_BUFFER_USAGE_TRANSFER_SRC_BIT or VK_BUFFER_USAGE_TRANSFER_DST_BIT, MemoryUsagePattern.SEMI_STATIC)
            vkCmdCopyBufferToImage(commandBuffer, scratchVkBuffer.handle, texture.imageHandle, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copies)

            val postUpdateBarrier = VkImageMemoryBarrier.callocStack(1).apply {
                sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)

                oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

                srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)

                image(texture.imageHandle)

                subresourceRange().apply {
                    aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    baseMipLevel(0)
                    levelCount(1)
                    baseArrayLayer(0)
                    layerCount(1)
                }

                srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
            }
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, postUpdateBarrier)


            val fence = backend.createFence(false)
            operationsPool.submitOneTimeCB(commandBuffer, backend.logicalDevice.graphicsQueue, fence)

            backend.waitFence(fence)

            vkDestroyFence(backend.logicalDevice.vkDevice, fence, null)
            vkFreeCommandBuffers(backend.logicalDevice.vkDevice, operationsPool.handle, commandBuffer)

            scratchVkBuffer.cleanup()
        }
    }

    private fun extractChunkInBuffer(byteBuffer: ByteBuffer, chunk: CubicChunk) {
        val voxelData = chunk.voxelDataArray

        if (voxelData == null) {
            for (i in 0 until 32 * 32 * 32) {
                byteBuffer.put(0)
                byteBuffer.put(0)
                byteBuffer.put(0)
                byteBuffer.put(0)
            }
        } else {
            for (z in 0..31)
                for (y in 0..31)
                    for (x in 0..31) {
                        val data = voxelData[x * 32 * 32 + y * 32 + z]
                        val voxel = world.contentTranslator.getVoxelForId(VoxelFormat.id(data)) ?: world.content.voxels().air()

                        if (voxel.isAir() || !voxel.solid) {
                            byteBuffer.put(0)
                            byteBuffer.put(0)
                            byteBuffer.put(0)
                            byteBuffer.put(0)
                        } else {
                            val topTexture = voxel.voxelTextures[VoxelSide.TOP.ordinal]
                            val color = topTexture.color

                            byteBuffer.put((color.x() * 255).toInt().toByte())
                            byteBuffer.put((color.y() * 255).toInt().toByte())
                            byteBuffer.put((color.z() * 255).toInt().toByte())
                            byteBuffer.put((color.w() * 255).toInt().toByte())
                        }
                    }
        }
    }

    override fun cleanup() {
        texture.cleanup()
        memFree(scratchByteBuffer)
    }
}

class VolumetricTextureMetadata : InterfaceBlock {
    val baseChunkPos = Vector3i(0)
    var size = 64
}