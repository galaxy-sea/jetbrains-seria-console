package plus.wcj.jetbrains.plugins.serialconsole.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import plus.wcj.jetbrains.plugins.serialconsole.model.ConnectionStatus
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialPortDescriptor
import plus.wcj.jetbrains.plugins.serialconsole.session.SerialWorkspaceListener
import plus.wcj.jetbrains.plugins.serialconsole.session.SerialWorkspaceService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SerialToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val component = SerialToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

private class SerialToolWindowPanel(project: Project) : JBPanel<SerialToolWindowPanel>(BorderLayout()), SerialWorkspaceListener {
    private val workspace = SerialWorkspaceService.getInstance(project)
    private val portsModel = DefaultListModel<SerialPortDescriptor>()
    private val portsList = JBList(portsModel)
    private val detailPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val languageButton = JButton()
    private val searchField = JBTextField()
    private val customPortField = JBTextField()
    private val customPortButton = JButton()
    private val refreshButton = JButton()

    init {
        border = JBUI.Borders.empty(6)
        add(buildPortsPage(), BorderLayout.CENTER)

        workspace.addListener(this)
        updateLanguageText()
        workspaceChanged()
    }

    override fun workspaceChanged() {
        updateLanguageText()
        applyPortFilter()
    }

    private fun buildPortsPage(): JBPanel<JBPanel<*>> {
        portsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        portsList.cellRenderer = PortRenderer(workspace)
        portsList.addListSelectionListener {
            renderPortDetail(portsList.selectedValue)
        }
        portsList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    portsList.selectedValue?.let { workspace.openSession(it) }
                }
            }

            override fun mousePressed(event: MouseEvent) = showPopup(event)

            override fun mouseReleased(event: MouseEvent) = showPopup(event)

            private fun showPopup(event: MouseEvent) {
                if (!event.isPopupTrigger) return
                val index = portsList.locationToIndex(event.point)
                if (index >= 0) portsList.selectedIndex = index
                portPopup(portsList.selectedValue).show(event.component, event.x, event.y)
            }
        })
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = applyPortFilter()
            override fun removeUpdate(event: DocumentEvent) = applyPortFilter()
            override fun changedUpdate(event: DocumentEvent) = applyPortFilter()
        })
        customPortField.addActionListener { openCustomPort() }

        return JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
            add(JBPanel<JBPanel<*>>(BorderLayout(0, 6)).apply {
                add(JBPanel<JBPanel<*>>(BorderLayout(6, 0)).apply {
                    add(searchField, BorderLayout.CENTER)
                    add(languageButton.apply {
                        addActionListener {
                            workspace.nextLanguage()
                        }
                    }, BorderLayout.EAST)
                }, BorderLayout.NORTH)
                add(JBPanel<JBPanel<*>>(BorderLayout(6, 0)).apply {
                    add(customPortField, BorderLayout.CENTER)
                    add(customPortButton.apply {
                        addActionListener { openCustomPort() }
                    }, BorderLayout.EAST)
                }, BorderLayout.SOUTH)
            }, BorderLayout.NORTH)
            add(JBScrollPane(portsList), BorderLayout.CENTER)
            add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
                add(detailPanel, BorderLayout.CENTER)
                add(refreshButton.apply { addActionListener { workspace.refreshPorts() } }, BorderLayout.SOUTH)
            }, BorderLayout.SOUTH)
        }
    }

    private fun openCustomPort() {
        workspace.openCustomPort(customPortField.text)
    }

    private fun renderPortDetail(port: SerialPortDescriptor?) {
        detailPanel.removeAll()
        if (port == null) {
            detailPanel.revalidate()
            detailPanel.repaint()
            return
        }

        detailPanel.add(sectionPanel(port.name) {
            add(labelValue(t("vendor"), port.vendor))
            add(labelValue(t("vid"), port.vid))
            add(labelValue(t("pid"), port.pid))
            add(labelValue(t("description"), port.description))
            add(labelValue(t("serialNumber"), port.serialNumber))
        }, BorderLayout.CENTER)
        detailPanel.revalidate()
        detailPanel.repaint()
    }

    private fun portPopup(port: SerialPortDescriptor?): JPopupMenu {
        return JPopupMenu().apply {
            add(JMenuItem(t("open")).apply {
                isEnabled = port != null && port.name != "No Ports"
                addActionListener { port?.let { workspace.openSession(it) } }
            })
            add(JMenuItem(t("copyPortName")).apply {
                isEnabled = port != null
                addActionListener {
                    port?.let { CopyPasteManager.getInstance().setContents(StringSelection(it.name)) }
                }
            })
            add(JMenuItem(t("properties")).apply { isEnabled = false })
        }
    }

    private fun updateLanguageText() {
        searchField.emptyText.text = t("search")
        customPortField.emptyText.text = t("customPortPath")
        customPortButton.text = t("open")
        refreshButton.text = t("refresh")
        languageButton.text = "${t("language")}: ${workspace.language.displayName}"
    }

    private fun applyPortFilter() {
        val selectedName = portsList.selectedValue?.name
        val ports = filteredPorts()
        replaceItems(portsModel, ports)
        portsList.selectedIndex = ports.indexOfFirst { it.name == selectedName }.takeIf { it >= 0 } ?: -1
        renderPortDetail(portsList.selectedValue ?: ports.firstOrNull())
    }

    private fun filteredPorts(): List<SerialPortDescriptor> {
        val tokens = searchField.text.trim()
            .lowercase()
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return workspace.ports

        return workspace.ports.filter { port ->
            val searchableText = listOf(
                port.name,
                port.path,
                port.identityPath,
                port.description,
                port.vendor,
                port.vid,
                port.pid,
                port.serialNumber,
                port.status.name,
            ).joinToString(" ").lowercase()
            tokens.all(searchableText::contains)
        }
    }

    private fun t(key: String): String = SerialBundle.message(workspace.language, key)

    private fun <T> replaceItems(model: DefaultListModel<T>, items: List<T>) {
        model.clear()
        items.forEach(model::addElement)
    }
}

private class PortRenderer(private val workspace: SerialWorkspaceService) : DefaultListCellRenderer() {
    private val emptyIcon = EmptyIcon.create(AllIcons.Actions.Execute.iconWidth, AllIcons.Actions.Execute.iconHeight)

    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
        val port = value as? SerialPortDescriptor
        val session = port?.let { descriptor ->
            workspace.sessions.firstOrNull { it.port.key() == descriptor.key() }
        }
        component.icon = when (session?.status) {
            ConnectionStatus.Connected -> AllIcons.Actions.Execute
            ConnectionStatus.Disconnected -> AllIcons.Actions.Restart
            null -> emptyIcon
        }
        component.text = port?.let { renderPortText(it, component.foreground, isSelected) }.orEmpty()
        component.border = JBUI.Borders.empty(6, 4)
        return component
    }

    private fun renderPortText(port: SerialPortDescriptor, foreground: Color, isSelected: Boolean): String {
        val path = StringUtil.escapeXmlEntities(port.displayPath())
        val description = StringUtil.escapeXmlEntities(port.description)
        val descriptionStyle = if (isSelected) {
            ""
        } else {
            " color=\"${htmlColor(foreground.darker())}\""
        }
        return "<html>$path <font$descriptionStyle>$description</font></html>"
    }

    private fun htmlColor(color: Color): String {
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }
}
