package io.xol.chunkstories.graphics.vulkan.util

import io.xol.chunkstories.api.dsl.RenderGraphDeclarationScript

import io.xol.chunkstories.api.graphics.TextureFormat.*
import io.xol.chunkstories.api.graphics.rendergraph.DepthTestingConfiguration.DepthTestMode.*
import io.xol.chunkstories.api.graphics.rendergraph.PassOutput.BlendMode.*
import io.xol.chunkstories.api.graphics.ImageInput.SamplingMode.*
import io.xol.chunkstories.api.gui.GuiDrawer

object BuiltInRendergraphs {
    val onlyGuiRenderGraph : RenderGraphDeclarationScript = {
        renderBuffers {
            renderBuffer {
                name = "menuBackground"

                format = RGBA_8
                size = viewportSize
            }

            renderBuffer {
                name = "guiColorBuffer"

                format = RGBA_8
                size = viewportSize
            }
        }

        passes {
            pass {
                name = "menuBackground"

                draws {
                    fullscreenQuad()
                }

                outputs {
                    output {
                        name = "guiColorBuffer"
                    }
                }

                depth {
                    enabled = false
                }
            }

            pass {
                name = "gui"

                dependsOn("menuBackground")

                draws {
                    system(GuiDrawer::class)
                }

                outputs {
                    output {
                        name = "guiColorBuffer"

                        //clear = true
                        blending = MIX
                    }
                }

                default = true
                final = true

                depth {
                    enabled = false
                }
            }
        }
    }
}
