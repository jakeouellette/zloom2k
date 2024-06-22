package zedit2.util.image

import com.github.weisj.jsvg.attributes.paint.SimplePaintSVGPaint
import com.github.weisj.jsvg.renderer.Output
import com.github.weisj.jsvg.renderer.RenderContext
import java.awt.Color
import java.awt.Paint
import java.awt.Shape
import java.awt.geom.Rectangle2D


internal class DynamicAWTSvgPaint(var color: Color) : SimplePaintSVGPaint {

    override fun fillShape(output: Output, context: RenderContext, shape: Shape, bounds: Rectangle2D?) {
        output.setPaint(this.paint())
        output.applyOpacity(color.alpha / 255.0f)
        output.fillShape(shape)
    }

    override fun drawShape(output: Output, context: RenderContext, shape: Shape, bounds: Rectangle2D?) {
        output.setPaint(this.paint())
        output.applyOpacity(color.alpha / 255.0f)
        output.drawShape(shape)
    }
    override fun paint(): Paint {
        return color
    }
}