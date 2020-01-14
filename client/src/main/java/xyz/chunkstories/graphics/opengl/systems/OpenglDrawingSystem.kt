package xyz.chunkstories.graphics.opengl.systems

import xyz.chunkstories.api.graphics.rendergraph.SystemExecutionContext
import xyz.chunkstories.api.graphics.systems.drawing.DrawingSystem
import xyz.chunkstories.graphics.common.Cleanable
import xyz.chunkstories.graphics.opengl.OpenglFrame
import xyz.chunkstories.graphics.opengl.graph.OpenglPass

abstract class OpenglDrawingSystem(val pass: OpenglPass) : DrawingSystem, Cleanable {
    abstract fun executeDrawingCommands(frame: OpenglFrame, ctx: SystemExecutionContext)
}