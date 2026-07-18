package plus.wcj.jetbrains.plugins.serialconsole.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal fun sectionPanel(title: String, collapsed: Boolean = false, body: JPanel.() -> Unit): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = JBUI.Borders.empty(0, 0, 2, 0)
    panel.alignmentX = Component.LEFT_ALIGNMENT

    val label = JBLabel(title)
    label.alignmentX = Component.LEFT_ALIGNMENT
    label.border = JBUI.Borders.empty(0, 2, 4, 0)
    panel.add(label)

    if (!collapsed) {
        val content = JBPanel<JBPanel<*>>()
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
        content.background = JBColor.namedColor("Panel.background", content.background)
        content.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border()),
            JBUI.Borders.empty(4, 5),
        )
        content.alignmentX = Component.LEFT_ALIGNMENT
        content.body()
        content.maximumSize = JBUI.size(Int.MAX_VALUE, content.preferredSize.height)
        panel.add(content)
    }
    panel.maximumSize = JBUI.size(Int.MAX_VALUE, panel.preferredSize.height)
    return panel
}

internal fun labelValue(label: String, value: String): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BorderLayout(8, 0)
    panel.alignmentX = Component.LEFT_ALIGNMENT
    panel.border = JBUI.Borders.empty()

    val name = JBLabel(label)
    name.foreground = JBColor.GRAY
    val content = JBLabel(value)

    panel.add(name, BorderLayout.WEST)
    panel.add(content, BorderLayout.CENTER)
    panel.maximumSize = JBUI.size(Int.MAX_VALUE, panel.preferredSize.height)
    return panel
}

internal fun labelControl(label: String, component: JComponent): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BorderLayout(8, 0)
    panel.alignmentX = Component.LEFT_ALIGNMENT
    panel.border = JBUI.Borders.empty()

    val name = JBLabel(label)
    name.foreground = JBColor.GRAY
    val controlPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        add(component)
    }

    panel.add(name, BorderLayout.WEST)
    panel.add(controlPanel, BorderLayout.CENTER)
    panel.maximumSize = JBUI.size(Int.MAX_VALUE, panel.preferredSize.height)
    return panel
}

internal fun labelAboveControl(label: String, component: JComponent): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.alignmentX = Component.LEFT_ALIGNMENT
    panel.border = JBUI.Borders.empty()

    val name = JBLabel(label)
    name.foreground = JBColor.GRAY
    name.alignmentX = Component.LEFT_ALIGNMENT

    val controlPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        add(component)
    }

    panel.add(name)
    panel.add(controlPanel)
    panel.maximumSize = JBUI.size(Int.MAX_VALUE, panel.preferredSize.height)
    return panel
}

internal fun compactField(value: String): JBTextField {
    return JBTextField(value).apply {
        maximumSize = JBUI.size(Int.MAX_VALUE, preferredSize.height)
    }
}

internal fun borderedPanel(): JPanel {
    return JBPanel<JBPanel<*>>().apply {
        border = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border())
    }
}
