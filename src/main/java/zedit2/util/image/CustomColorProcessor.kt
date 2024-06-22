package zedit2.util.image

import com.github.weisj.jsvg.parser.DomProcessor
import com.github.weisj.jsvg.parser.ParsedElement
import java.awt.Color
import java.util.*
import java.util.function.Consumer


internal class CustomColorsProcessor(elementIds: List<String?>) : DomProcessor {
    private val customColorsFill: MutableMap<String?, DynamicAWTSvgPaint> = HashMap<String?, DynamicAWTSvgPaint>()
    private val customColorsStroke: MutableMap<String?, DynamicAWTSvgPaint> = HashMap<String?, DynamicAWTSvgPaint>()
    init {
        for (elementId in elementIds) {
            customColorsFill[elementId] = DynamicAWTSvgPaint(Color(0,0,0,0))
            customColorsStroke[elementId] = DynamicAWTSvgPaint(Color(0,0,0,0))
        }
    }

    fun customColorFillForId(id: String): DynamicAWTSvgPaint? {
        return customColorsFill[id]
    }

    fun customColorStrokeForId(id: String): DynamicAWTSvgPaint? {
        return customColorsStroke[id]
    }

    override fun process(root: ParsedElement) {
        processImpl(root)
        root.children().forEach(Consumer { root: ParsedElement ->
            this.process(
                root
            )
        })
    }

    private fun processImpl(element: ParsedElement) {
        // Obtain the id of the element
        // Note: There that Element also has a node() method to obtain the SVGNode. However during the pre-processing
        // phase the SVGNode is not yet fully parsed and doesn't contain any non-defaulted information.
        val nodeId = element.id()

        // Check if this element is one of the elements we want to change the color of
        if (customColorsStroke.containsKey(nodeId)) {
            // The attribute node contains all the attributes of the element specified in the markup
            // Even those which aren't valid for the element
            val attributeNode = element.attributeNode()
            val dynamicColorStroke: DynamicAWTSvgPaint = customColorsStroke[nodeId]!!
            // This assumed that the fill attribute is a color and not a gradient or pattern.
            val colorStroke = attributeNode.getColor("stroke")

            dynamicColorStroke.color = colorStroke
            // This can be anything as long as it's unique
            val uniqueIdForDynamicColorStroke = UUID.randomUUID().toString()
            // Register the dynamic color as a custom element
            element.registerNamedElement(uniqueIdForDynamicColorStroke, dynamicColorStroke)
            // Refer to the custom element as the fill attribute
            attributeNode.attributes()["stroke"] = uniqueIdForDynamicColorStroke
            // Note: This class can easily be adapted to also support changing the stroke color.
            // With a bit more work it could also support changing the color of gradients and patterns.
        }

        // Check if this element is one of the elements we want to change the color of
        if (customColorsFill.containsKey(nodeId)) {
            // The attribute node contains all the attributes of the element specified in the markup
            // Even those which aren't valid for the element
            val attributeNode = element.attributeNode()
            val dynamicColorFill: DynamicAWTSvgPaint = customColorsFill[nodeId]!!
            // This assumed that the fill attribute is a color and not a gradient or pattern.
            val colorFill = attributeNode.getColor("fill")

            dynamicColorFill.color = colorFill
            // This can be anything as long as it's unique
            val uniqueIdForDynamicColorFill = UUID.randomUUID().toString()

            // Register the dynamic color as a custom element
            element.registerNamedElement(uniqueIdForDynamicColorFill, dynamicColorFill)

            // Refer to the custom element as the fill attribute
            attributeNode.attributes()["fill"] = uniqueIdForDynamicColorFill
            // Note: This class can easily be adapted to also support changing the stroke color.
            // With a bit more work it could also support changing the color of gradients and patterns.
        }
    }
}
