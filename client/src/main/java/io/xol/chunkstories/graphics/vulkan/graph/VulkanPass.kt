package io.xol.chunkstories.graphics.vulkan.graph

import io.xol.chunkstories.api.graphics.rendergraph.Pass
import io.xol.chunkstories.graphics.vulkan.CommandPool
import io.xol.chunkstories.graphics.vulkan.VulkanGraphicsBackend
import io.xol.chunkstories.graphics.vulkan.resources.Cleanable
import io.xol.chunkstories.graphics.vulkan.resources.PerFrameResource
import io.xol.chunkstories.graphics.vulkan.swapchain.Frame
import io.xol.chunkstories.graphics.vulkan.systems.VulkanDrawingSystem
import io.xol.chunkstories.graphics.vulkan.textures.vulkanFormat
import io.xol.chunkstories.graphics.vulkan.util.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.LoggerFactory

class VulkanPass(val backend: VulkanGraphicsBackend, val graph: VulkanRenderGraph, config: Pass.() -> Unit) : Pass(), Cleanable {

    val renderBuffers: List<VulkanRenderBuffer>

    val renderPass: VkRenderPass
    val framebuffer: VkFramebuffer

    val drawingSystems: List<VulkanDrawingSystem>

    val passDoneSemaphore: VkSemaphore

    val commandPool = CommandPool(backend, backend.logicalDevice.graphicsQueue.family, VK_COMMAND_POOL_CREATE_TRANSIENT_BIT or VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
    val commandBuffers: PerFrameResource<VkCommandBuffer>

    init {
        this.apply(config)

        MemoryStack.stackPush()

        passDoneSemaphore = backend.createSemaphore()

        renderBuffers = outputs.map { output ->
            graph.buffers[output.outputBuffer ?: output.name] ?: throw Exception("Buffer ${output.outputBuffer} isn't declared !")
        }

        renderPass = createRenderPass()
        framebuffer = createFramebuffer()

        drawingSystems = mutableListOf()
        for (declaredDrawingSystem in this.declaredDrawingSystems) {
            val drawingSystem = backend.createDrawingSystem(this, declaredDrawingSystem)
            drawingSystems.add(drawingSystem)
        }

        commandBuffers = PerFrameResource(backend) {
            val commandBufferAllocateInfo = VkCommandBufferAllocateInfo.callocStack().sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO).apply {
                commandPool(commandPool.handle)
                level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                commandBufferCount(1)
            }

            val pCmdBuffers = MemoryStack.stackMallocPointer(1)
            vkAllocateCommandBuffers(backend.logicalDevice.vkDevice, commandBufferAllocateInfo, pCmdBuffers)

            val commandBuffer = VkCommandBuffer(pCmdBuffers.get(0), backend.logicalDevice.vkDevice)

            commandBuffer
        }

        MemoryStack.stackPop()
    }

    private fun createRenderPass(): VkRenderPass {
        val attachmentDescription = VkAttachmentDescription.callocStack(outputs.size)
        outputs.mapIndexed { index, output ->
            attachmentDescription[index].apply {
                val renderbuffer = renderBuffers[index]

                format(renderbuffer.format.vulkanFormat.ordinal)
                samples(VK_SAMPLE_COUNT_1_BIT)

                if (output.clear)
                    loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                else
                    loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                //TODO use DONT_CARE when it can be determined we won't be needing the data
                storeOp(VK_ATTACHMENT_STORE_OP_STORE)

                //TODO we don't even use stencil why is this here
                stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)

                //TODO use a smarter layout
                initialLayout(VK_IMAGE_LAYOUT_GENERAL)
                finalLayout(VK_IMAGE_LAYOUT_GENERAL)
            }
        }

        val colorAttachmentReference = VkAttachmentReference.callocStack(outputs.size)
        outputs.mapIndexed { index, output ->
            colorAttachmentReference[index].apply {
                attachment(index)
                //layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                layout(VK_IMAGE_LAYOUT_GENERAL)
            }
        }

        val subpassDescription = VkSubpassDescription.callocStack(1).apply {
            pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)

            pColorAttachments(colorAttachmentReference)
            colorAttachmentCount(colorAttachmentReference.capacity())
        }

        val dependencies = VkSubpassDependency.calloc(1).apply {
            srcSubpass(VK_SUBPASS_EXTERNAL)
            dstSubpass(0)

            srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            srcAccessMask(0)

            dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
        }

        val renderPassCreateInfo = VkRenderPassCreateInfo.callocStack().sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO).apply {
            pAttachments(attachmentDescription)
            pSubpasses(subpassDescription)

            pDependencies(dependencies)
        }

        val pRenderPass = MemoryStack.stackMallocLong(1)
        vkCreateRenderPass(backend.logicalDevice.vkDevice, renderPassCreateInfo, null, pRenderPass).ensureIs("Failed to create render pass", VK_SUCCESS)
        return pRenderPass.get(0)
    }

    private fun createFramebuffer(): VkFramebuffer {
        val pAttachments = stackMallocLong(outputs.size)

        renderBuffers.forEach { renderBuffer -> pAttachments.put(renderBuffer.texture.imageView) }
        pAttachments.flip()

        val framebufferCreateInfo = VkFramebufferCreateInfo.callocStack().sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO).apply {
            renderPass(renderPass)
            pAttachments(pAttachments)
            width(backend.window.width)
            height(backend.window.height)
            layers(1)
        }

        val pFramebuffer = stackMallocLong(1)
        vkCreateFramebuffer(backend.logicalDevice.vkDevice, framebufferCreateInfo, null, pFramebuffer).ensureIs("Failed to create framebuffer", VK_SUCCESS)
        return pFramebuffer.get(0)
    }

    //TODO for now let's assume there is only one pass so we can use head/tail semaphores from the frame object
    fun render(frame: Frame, inSemaphore: VkSemaphore) {
        stackPush().use {
            commandBuffers[frame].apply {
                val beginInfo = VkCommandBufferBeginInfo.callocStack().sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO).apply {
                    flags(VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT)
                    pInheritanceInfo(null)
                }

                vkBeginCommandBuffer(this, beginInfo)

                val viewport = VkViewport.callocStack(1).apply {
                    x(0.0F)
                    y(0.0F)
                    width(backend.window.width.toFloat())
                    height(backend.window.height.toFloat())
                    minDepth(0.0F)
                    maxDepth(1.0F)
                }

                val zeroZero = VkOffset2D.callocStack().apply {
                    x(0)
                    y(0)
                }
                val scissor = VkRect2D.callocStack(1).apply {
                    offset(zeroZero)
                    extent().width(backend.window.width)
                    extent().height(backend.window.height)
                }

                vkCmdSetViewport(this, 0, viewport)
                vkCmdSetScissor(this, 0, scissor)

                val renderPassBeginInfo = VkRenderPassBeginInfo.callocStack().sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO).apply {

                    renderPass(renderPass)
                    framebuffer(framebuffer)
                    //renderPass(backend.renderToBackbuffer.handle)
                    //framebuffer(frame.swapchainFramebuffer)

                    renderArea().offset().x(0)
                    renderArea().offset().y(0)
                    renderArea().extent().width(backend.window.width)
                    renderArea().extent().height(backend.window.height)

                    val clearColor = VkClearValue.callocStack(1).apply {
                        color().float32().apply {
                            this.put(0, 0.0F)
                            this.put(1, 0.5F)
                            this.put(2, 0.0F)
                            this.put(3, 1.0F)
                        }
                    }
                    pClearValues(clearColor)
                }

                vkCmdBeginRenderPass(this, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)

                for (drawingSystem in drawingSystems) {
                    drawingSystem.registerDrawingCommands(frame, this)
                }

                vkCmdEndRenderPass(this)
                vkEndCommandBuffer(this)
            }

            val submitInfo = VkSubmitInfo.callocStack().sType(VK_STRUCTURE_TYPE_SUBMIT_INFO).apply {
                val waitOnSemaphores = MemoryStack.stackMallocLong(1)
                waitOnSemaphores.put(0, inSemaphore)
                pWaitSemaphores(waitOnSemaphores)
                waitSemaphoreCount(1)

                val waitStages = MemoryStack.stackMallocInt(1)
                waitStages.put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                pWaitDstStageMask(waitStages)

                val commandBuffers = MemoryStack.stackMallocPointer(1)
                commandBuffers.put(0, this@VulkanPass.commandBuffers[frame])
                pCommandBuffers(commandBuffers)

                val semaphoresToSignal = MemoryStack.stackLongs(passDoneSemaphore)
                pSignalSemaphores(semaphoresToSignal)
            }

            vkQueueSubmit(backend.logicalDevice.graphicsQueue.handle, submitInfo, /*frame.renderFinishedFence*/ VK_NULL_HANDLE).ensureIs("Failed to submit command buffer", VK_SUCCESS)
        }
    }

    override fun cleanup() {
        vkDestroyFramebuffer(backend.logicalDevice.vkDevice, framebuffer, null)
        vkDestroyRenderPass(backend.logicalDevice.vkDevice, renderPass, null)

        commandPool.cleanup()
        //commandBuffers.cleanup() // useless, cleaning the commandpool cleans those implicitely

        vkDestroySemaphore(backend.logicalDevice.vkDevice, passDoneSemaphore, null)

        drawingSystems.forEach(Cleanable::cleanup)
    }

    companion object {
        val logger = LoggerFactory.getLogger("client.vulkan")
    }
}