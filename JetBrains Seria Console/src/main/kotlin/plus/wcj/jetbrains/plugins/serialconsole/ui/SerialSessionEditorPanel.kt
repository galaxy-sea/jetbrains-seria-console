package plus.wcj.jetbrains.plugins.serialconsole.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ColorPanel
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.RegionPainter
import com.intellij.util.ui.UIUtil
import plus.wcj.jetbrains.plugins.serialconsole.model.ConnectionStatus
import plus.wcj.jetbrains.plugins.serialconsole.model.AppendMode
import plus.wcj.jetbrains.plugins.serialconsole.model.ByteOrderMode
import plus.wcj.jetbrains.plugins.serialconsole.model.FlowControl
import plus.wcj.jetbrains.plugins.serialconsole.model.PacketMode
import plus.wcj.jetbrains.plugins.serialconsole.model.ParityMode
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialMessage
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialMessageDirection
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialProjectInfo
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialSession
import plus.wcj.jetbrains.plugins.serialconsole.model.StopBits
import plus.wcj.jetbrains.plugins.serialconsole.serial.CrcAlgorithm
import plus.wcj.jetbrains.plugins.serialconsole.serial.CrcAlgorithms
import plus.wcj.jetbrains.plugins.serialconsole.serial.PayloadCodec
import plus.wcj.jetbrains.plugins.serialconsole.session.SerialWorkspaceListener
import plus.wcj.jetbrains.plugins.serialconsole.session.SerialWorkspaceSettings
import plus.wcj.jetbrains.plugins.serialconsole.session.SerialWorkspaceService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.FlowLayout
import java.awt.Graphics2D
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.geom.Rectangle2D
import java.nio.charset.Charset
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JSplitPane
import javax.swing.SwingConstants
import javax.swing.plaf.basic.BasicSplitPaneUI

class SerialSessionEditorPanel(
    private val project: Project,
    private val sessionId: String,
) : JBPanel<SerialSessionEditorPanel>(BorderLayout()), SerialWorkspaceListener {
    private val workspace = SerialWorkspaceService.getInstance(project)
    private val settings = SerialWorkspaceSettings.getInstance(project)
    private val session: SerialSession = requireNotNull(workspace.findSession(sessionId)) {
        "Serial session not found: $sessionId"
    }
    private val messageDocument = EditorFactory.getInstance().createDocument("")
    private val messageEditor: Editor = EditorFactory.getInstance().createViewer(messageDocument, project).apply {
        settings.setLineNumbersShown(true)
        settings.setUseSoftWraps(true)
        settings.setAdditionalPageAtBottom(false)
        settings.setAdditionalLinesCount(0)
    }
    private val payloadDocument = EditorFactory.getInstance().createDocument("06 81 19 96 12 22 85")
    private val payloadEditor: Editor = EditorFactory.getInstance().createEditor(payloadDocument, project).apply {
        settings.setLineNumbersShown(false)
        settings.setUseSoftWraps(true)
        settings.setAdditionalPageAtBottom(false)
        settings.setAdditionalLinesCount(0)
    }
    private val payloadDocumentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            updatePayloadCrcPreview()
        }
    }
    private val messageFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    private val sessionPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val connectionButton = JButton()
    private val sendButton = JButton()
    private val sessionToggleButton = JButton()
    private val hexFormatButton = JButton()
    private lateinit var rootSplitPane: JSplitPane
    private var sessionPanelVisible = true
    private var payloadCrcInlay: Inlay<*>? = null

    val preferredFocus = payloadEditor.contentComponent

    init {
        border = JBUI.Borders.empty()
        connectionButton.addActionListener {
            if (session.status == ConnectionStatus.Connected) {
                workspace.disconnectSession(sessionId)
            } else {
                workspace.connectSession(sessionId)
            }
        }
        payloadDocument.addDocumentListener(payloadDocumentListener)
        configureEditorScrollbars(messageEditor)
        configureEditorScrollbars(payloadEditor)
        rootSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildSessionPanel(), buildWorkPanel()).apply {
            resizeWeight = 0.18
            dividerLocation = 230
            dividerSize = 0
            border = JBUI.Borders.empty()
            removeDividerBorder()
        }
        add(rootSplitPane, BorderLayout.CENTER)
        workspace.addListener(this)
        connectOnOpen()
        workspaceChanged()
    }

    private fun configureEditorScrollbars(editor: Editor) {
        clearScrollbarTrack(editor.component)
    }

    private fun clearScrollbarTrack(component: Component) {
        if (component is JBScrollBar) {
            component.putClientProperty(
                JBScrollBar.TRACK,
                RegionPainter<Any?> { _, _, _, _, _, _ -> },
            )
            component.isOpaque = false
        } else if (component is JScrollBar) {
            component.isOpaque = false
        }

        if (component is Container) {
            component.components.forEach(::clearScrollbarTrack)
        }
    }

    fun dispose() {
        workspace.removeListener(this)
        payloadDocument.removeDocumentListener(payloadDocumentListener)
        payloadCrcInlay?.dispose()
        EditorFactory.getInstance().releaseEditor(messageEditor)
        EditorFactory.getInstance().releaseEditor(payloadEditor)
    }

    override fun workspaceChanged() {
        rebuildSessionPanel()
        updateLanguageText()
        updateMessageTextIfNotPaused()
        updatePayloadCrcPreview()
        updateConnectionButton()
        revalidate()
        repaint()
    }

    private fun connectOnOpen() {
        if (session.status == ConnectionStatus.Disconnected) {
            workspace.connectSession(sessionId)
        }
    }

    private fun buildSessionPanel(): JPanel {
        return sessionPanel.apply {
            preferredSize = Dimension(215, 600)
            minimumSize = Dimension(0, 0)
            rebuildSessionPanel()
        }
    }

    private fun rebuildSessionPanel() {
        sessionPanel.removeAll()
        sessionPanel.add(JBScrollPane(JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10)
            add(sectionPanel(t("serialSettings")) {
                add(connectionButton.apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                    updateConnectionButton()
                })
                add(labelValue(t("port"), session.serialConfig.portName))
                add(labelControl(t("baudRate"), baudRateCombo()))
                add(labelControl(t("dataBits"), dataBitsCombo()))
                add(labelControl(t("stopBits"), stopBitsCombo()))
                add(labelControl(t("parity"), parityCombo()))
                add(labelControl(t("flowControl"), flowControlCombo()))
                add(lineControlRow(rtsCheckBox(), dtrCheckBox()))
                add(lineStatusRow(
                    lineStatusLabel(t("cts"), session.lineState.cts),
                    lineStatusLabel(t("dsr"), session.lineState.dsr),
                ))
                add(lineStatusRow(
                    lineStatusLabel(t("dcd"), session.lineState.dcd),
                    lineStatusLabel(t("ri"), session.lineState.ri),
                ))
            })
            add(sectionPanel(t("receiveSettings")) {
                add(labelAboveControl(t("textEncoding"), receiveTextEncodingControl()))
                add(labelControl(t("packetMode"), packetModeCombo()))
                add(labelControl(t("packetTimeout"), packetTimeoutField()))
                add(timestampCheckBox())
            })
            add(sectionPanel(t("sendSettings")) {
                add(labelAboveControl(t("textEncoding"), sendTextEncodingControl()))
                add(labelControl(t("lineEnding"), appendModeCombo()))
                add(labelControl(t("crcAlgorithm"), crcModeCombo()))
                add(labelControl(t("byteOrder"), byteOrderCombo()))
            })
            add(sectionPanel(t("statistics"), collapsed = true) {
                add(labelValue(t("rxBytes"), session.statistics.rxBytes.toString()))
                add(labelValue(t("txBytes"), session.statistics.txBytes.toString()))
                add(labelValue(t("rxPackets"), session.statistics.rxPackets.toString()))
                add(labelValue(t("txPackets"), session.statistics.txPackets.toString()))
                add(labelValue(t("duration"), formatDuration()))
            })
        }), BorderLayout.CENTER)
        sessionPanel.revalidate()
        sessionPanel.repaint()
    }

    private fun updateConnectionButton() {
        connectionButton.text = when (session.status) {
            ConnectionStatus.Connected -> t("disconnect")
            ConnectionStatus.Disconnected -> t("connect")
        }
        connectionButton.icon = when (session.status) {
            ConnectionStatus.Connected -> AllIcons.Actions.Suspend
            ConnectionStatus.Disconnected -> AllIcons.Actions.Execute
        }
        connectionButton.foreground = when (session.status) {
            ConnectionStatus.Connected -> JBColor.RED
            ConnectionStatus.Disconnected -> JBColor.GREEN
        }
        connectionButton.toolTipText = t("statusTooltip", session.status.name)
    }

    private fun baudRateCombo(): ComboBox<String> {
        val comboBox = smallComboBox(
            arrayOf("9600", "19200", "38400", "57600", "115200", "230400", "460800", "921600"),
            "115200",
        ).apply {
            isEditable = true
            selectedItem = session.serialConfig.baudRate.toString()
        }
        comboBox.addActionListener {
            val value = comboBox.selectedItem?.toString()?.trim()?.toIntOrNull()
            if (value != null && value > 0) {
                session.serialConfig.baudRate = value
                saveSettings()
            }
        }
        return comboBox
    }

    private fun dataBitsCombo(): ComboBox<Int> {
        return smallComboBox(arrayOf(5, 6, 7, 8), 8).apply {
            selectedItem = session.serialConfig.dataBits
            addActionListener {
                (selectedItem as? Int)?.let {
                    session.serialConfig.dataBits = it
                    saveSettings()
                }
            }
        }
    }

    private fun stopBitsCombo(): ComboBox<StopBits> {
        return smallComboBox(StopBits.values(), StopBits.One).apply {
            renderer = stopBitsRenderer()
            selectedItem = session.serialConfig.stopBits
            addActionListener {
                (selectedItem as? StopBits)?.let {
                    session.serialConfig.stopBits = it
                    saveSettings()
                }
            }
        }
    }

    private fun stopBitsRenderer(): DefaultListCellRenderer {
        return object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val stopBits = value as? StopBits
                component.text = stopBits?.let { t("stopBits.${it.name}") }.orEmpty()
                return component
            }
        }
    }

    private fun parityCombo(): ComboBox<ParityMode> {
        return smallComboBox(ParityMode.values(), ParityMode.None).apply {
            selectedItem = session.serialConfig.parity
            addActionListener {
                (selectedItem as? ParityMode)?.let {
                    session.serialConfig.parity = it
                    saveSettings()
                }
            }
        }
    }

    private fun flowControlCombo(): ComboBox<FlowControl> {
        return smallComboBox(FlowControl.values(), FlowControl.None).apply {
            renderer = flowControlRenderer()
            selectedItem = session.serialConfig.flowControl
            addActionListener {
                (selectedItem as? FlowControl)?.let {
                    session.serialConfig.flowControl = it
                    saveSettings()
                    workspace.applySessionFlowControl(sessionId)
                    rebuildSessionPanel()
                }
            }
        }
    }

    private fun flowControlRenderer(): DefaultListCellRenderer {
        return object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val flowControl = value as? FlowControl
                component.text = flowControl?.let { t("flowControl.${it.name}") }.orEmpty()
                return component
            }
        }
    }

    private fun rtsCheckBox(): JBCheckBox {
        return lineCheckBox(
            selected = session.lineState.rts,
            enabled = session.status == ConnectionStatus.Connected && !session.serialConfig.flowControl.controlsRts(),
        ) { selected ->
            workspace.setSessionRts(sessionId, selected)
        }.apply {
            text = t("rts")
        }
    }

    private fun dtrCheckBox(): JBCheckBox {
        return lineCheckBox(
            selected = session.lineState.dtr,
            enabled = session.status == ConnectionStatus.Connected && !session.serialConfig.flowControl.controlsDtr(),
        ) { selected ->
            workspace.setSessionDtr(sessionId, selected)
        }.apply {
            text = t("dtr")
        }
    }

    private fun lineCheckBox(selected: Boolean, enabled: Boolean, onChanged: (Boolean) -> Unit): JBCheckBox {
        return JBCheckBox().apply {
            isSelected = selected
            isEnabled = enabled
            addActionListener {
                onChanged(isSelected)
            }
        }
    }

    private fun lineControlRow(vararg controls: JComponent): JPanel {
        return JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            controls.forEachIndexed { index, control ->
                if (index > 0) {
                    control.border = JBUI.Borders.emptyLeft(12)
                }
                add(control)
            }
            maximumSize = JBUI.size(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun lineStatusRow(vararg labels: JBLabel): JPanel {
        return JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            labels.forEachIndexed { index, label ->
                if (index > 0) {
                    label.border = JBUI.Borders.emptyLeft(8)
                }
                add(label)
            }
            maximumSize = JBUI.size(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun lineStatusLabel(label: String, value: Boolean): JBLabel {
        return JBLabel(label).apply {
            icon = if (value) AllIcons.General.SuccessLogin else AllIcons.General.BalloonInformation
            horizontalTextPosition = SwingConstants.RIGHT
            toolTipText = "$label ${onOff(value)}"
        }
    }

    private fun FlowControl.controlsRts(): Boolean {
        return this == FlowControl.RtsCts || this == FlowControl.RtsCtsXonXoff
    }

    private fun FlowControl.controlsDtr(): Boolean {
        return this == FlowControl.DtrDsr || this == FlowControl.DtrDsrXonXoff
    }

    private fun onOff(value: Boolean): String {
        return t(if (value) "on" else "off")
    }

    private fun receiveTextEncodingCombo(): ComboBox<String> {
        return smallComboBox(charsetNames(), "windows-1252").withSpeedSearch(
            selected = session.receiveConfig.textEncoding,
        ) { selected ->
            session.receiveConfig.textEncoding = selected
            saveSettings()
            forceUpdateMessageText()
        }
    }

    private fun receiveTextEncodingControl(): JPanel {
        return textEncodingControl(receiveTextEncodingCombo(), receiveTextColorPanel())
    }

    private fun receiveTextColorPanel(): ColorPanel {
        return ColorPanel().apply {
            selectedColor = Color(session.receiveConfig.textColor)
            toolTipText = t("textColor")
            makeSquareColorSwatch()
            addActionListener {
                selectedColor?.let { color ->
                    session.receiveConfig.textColor = color.rgb and 0x00FFFFFF
                    saveSettings()
                    forceUpdateMessageText()
                }
            }
        }
    }

    private fun packetModeCombo(): ComboBox<PacketModeItem> {
        val items = PacketMode.values().map { PacketModeItem(it) }.toTypedArray()
        return smallComboBox(items, items.first()).apply {
            selectedItem = items.firstOrNull { it.mode == session.receiveConfig.packetMode }
            addActionListener {
                (selectedItem as? PacketModeItem)?.let {
                    session.receiveConfig.packetMode = it.mode
                    saveSettings()
                    rebuildSessionPanel()
                }
            }
        }
    }

    private fun sendTextEncodingCombo(): ComboBox<String> {
        return smallComboBox(charsetNames(), "windows-1252").withSpeedSearch(
            selected = session.sendConfig.textEncoding,
        ) { selected ->
            session.sendConfig.textEncoding = selected
            saveSettings()
            forceUpdateMessageText()
            updatePayloadCrcPreview()
            updateHexFormatButton()
        }
    }

    private fun sendTextEncodingControl(): JPanel {
        return textEncodingControl(sendTextEncodingCombo(), sendTextColorPanel())
    }

    private fun sendTextColorPanel(): ColorPanel {
        return ColorPanel().apply {
            selectedColor = Color(session.sendConfig.textColor)
            toolTipText = t("textColor")
            makeSquareColorSwatch()
            addActionListener {
                selectedColor?.let { color ->
                    session.sendConfig.textColor = color.rgb and 0x00FFFFFF
                    saveSettings()
                    forceUpdateMessageText()
                }
            }
        }
    }

    private fun textEncodingControl(comboBox: ComboBox<String>, colorPanel: ColorPanel): JPanel {
        return JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            add(comboBox)
            add(colorPanel)
        }
    }

    private fun appendModeCombo(): ComboBox<AppendModeItem> {
        val items = AppendMode.values().map { AppendModeItem(it) }.toTypedArray()
        return smallComboBox(items, items.first()).apply {
            selectedItem = items.firstOrNull { it.mode == session.sendConfig.appendMode }
            addActionListener {
                (selectedItem as? AppendModeItem)?.let {
                    session.sendConfig.appendMode = it.mode
                    saveSettings()
                    updatePayloadCrcPreview()
                }
            }
        }
    }

    private fun crcModeCombo(): ComboBox<CrcModeItem> {
        val items = CrcAlgorithms.all().map { CrcModeItem(it) }.toTypedArray()
        val selected = CrcAlgorithms.find(session.sendConfig.crcAlgorithm)
        return smallComboBox(items, crcPrototypeItem(items)).withSpeedSearch(
            selected = items.firstOrNull { it.algorithm.id == selected?.id } ?: items.first(),
        ) { item ->
            session.sendConfig.crcAlgorithm = item.algorithm.id
            saveSettings()
            updatePayloadCrcPreview()
            rebuildSessionPanel()
        }.apply {
            setPreferredWidth(145)
        }
    }

    private fun byteOrderCombo(): ComboBox<ByteOrderModeItem> {
        val items = ByteOrderMode.values().map { ByteOrderModeItem(it) }.toTypedArray()
        return smallComboBox(items, items.first()).apply {
            isEnabled = session.sendConfig.crcAlgorithm != CrcAlgorithms.NONE_ID
            selectedItem = items.firstOrNull { it.mode == session.sendConfig.crcByteOrder }
            addActionListener {
                (selectedItem as? ByteOrderModeItem)?.let {
                    session.sendConfig.crcByteOrder = it.mode
                    saveSettings()
                    updatePayloadCrcPreview()
                }
            }
        }
    }

    private fun packetTimeoutField(): JBTextField {
        val field = compactField("${session.receiveConfig.packetTimeoutMs}")
        field.isEnabled = session.receiveConfig.packetMode == PacketMode.Timeout
        field.toolTipText = t("packetTimeoutTooltip")
        field.addActionListener {
            applyPacketTimeout(field)
        }
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(event: FocusEvent) {
                applyPacketTimeout(field)
            }
        })
        return field
    }

    private fun applyPacketTimeout(field: JBTextField) {
        if (!field.isEnabled) return
        val value = field.text.trim().toIntOrNull()
        if (value != null && value > 0) {
            session.receiveConfig.packetTimeoutMs = value
            saveSettings()
        } else {
            field.text = session.receiveConfig.packetTimeoutMs.toString()
        }
    }

    private fun timestampCheckBox(): JBCheckBox {
        return JBCheckBox(t("timestamp"), session.receiveConfig.showTimestamp).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = JBUI.size(Int.MAX_VALUE, preferredSize.height)
            addActionListener {
                session.receiveConfig.showTimestamp = isSelected
                saveSettings()
                forceUpdateMessageText()
            }
        }
    }

    private fun saveSettings() {
        settings.saveSessionSettings(session)
    }

    private fun buildWorkPanel(): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val split = JSplitPane(JSplitPane.VERTICAL_SPLIT, buildMessagePanel(), buildSendPanel()).apply {
                resizeWeight = 0.8
                setDividerLocation(0.8)
                dividerSize = 3
                border = JBUI.Borders.empty()
                removeDividerBorder()
                setDividerCursorAndColor()
            }
            add(split, BorderLayout.CENTER)
        }
    }

    private fun JSplitPane.removeDividerBorder() {
        (ui as? BasicSplitPaneUI)?.divider?.border = JBUI.Borders.empty()
    }

    private fun JSplitPane.setDividerCursorAndColor() {
        (ui as? BasicSplitPaneUI)?.divider?.apply {
            background = JBColor.border()
            border = BorderFactory.createMatteBorder(3, 0, 0, 0, JBColor.border())
            cursor = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
        }
    }

    private fun buildMessagePanel(): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(messageToolbar(), BorderLayout.NORTH)

            add(messageEditor.component, BorderLayout.CENTER)
        }
    }

    private fun buildSendPanel(): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)

            add(JBPanel<JBPanel<*>>(BorderLayout(0, 6)).apply {
                add(payloadEditor.component, BorderLayout.CENTER)
                add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                        add(sessionToggleButton.apply {
                            addActionListener { toggleSessionPanel() }
                        })
                    }, BorderLayout.WEST)
                    add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
                        add(hexFormatButton.apply {
                            addActionListener { toggleHexPayloadFormat() }
                        })
                        add(sendButton.apply {
                            addActionListener { sendPayload() }
                        })
                    }, BorderLayout.EAST)
                }, BorderLayout.SOUTH)
            }, BorderLayout.CENTER)
        }
    }

    private fun messageToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(PauseMessagesAction())
            add(ClearMessagesAction())
            add(ScrollToTheEndToolbarAction(messageEditor))
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("SerialConsole.Message", group, true)
        toolbar.targetComponent = messageEditor.component
        return toolbar.component
    }

    private fun toggleSessionPanel() {
        sessionPanelVisible = !sessionPanelVisible
        sessionPanel.isVisible = sessionPanelVisible
        rootSplitPane.dividerLocation = if (sessionPanelVisible) JBUI.scale(230) else 0
        updateSessionToggleButton()
        rootSplitPane.revalidate()
        rootSplitPane.repaint()
    }

    private fun toggleHexPayloadFormat() {
        if (!isSendHexEncoding()) return

        val normalized = normalizeHexPayload(payloadDocument.text)
        val next = if (payloadDocument.text.trim().uppercase() == formatHexPayload(normalized)) {
            normalized
        } else {
            formatHexPayload(normalized)
        }

        ApplicationManager.getApplication().runWriteAction {
            payloadDocument.setText(next)
        }
        payloadEditor.caretModel.moveToOffset(payloadDocument.textLength)
    }

    private fun normalizeHexPayload(text: String): String {
        return PayloadCodec.normalizeHexInput(text)
    }

    private fun formatHexPayload(text: String): String {
        return text.chunked(2).joinToString(" ")
    }

    private fun isSendHexEncoding(): Boolean {
        return session.sendConfig.textEncoding.equals(PayloadCodec.HEX_ENCODING, ignoreCase = true)
    }

    private fun updateHexFormatButton() {
        hexFormatButton.isEnabled = isSendHexEncoding()
        hexFormatButton.toolTipText = if (hexFormatButton.isEnabled) t("hexFormatTooltip") else null
    }

    private fun sendPayload() {
        try {
            val bytes = PayloadCodec.encode(payloadDocument.text, session.sendConfig)
            if (session.status == ConnectionStatus.Connected) {
                workspace.writeSession(sessionId, bytes)
            }
            session.messages.removeIf { it.direction == SerialMessageDirection.Log }
            if (!session.messagePaused) {
                session.messages += SerialMessage(
                    time = java.time.Instant.now(),
                    direction = SerialMessageDirection.Output,
                    data = bytes,
                )
            }
            session.statistics.txBytes += bytes.size
            session.statistics.txPackets += 1
            updateMessageTextIfNotPaused()
        } catch (_: IllegalArgumentException) {
            session.messages.removeIf { it.direction == SerialMessageDirection.Log }
            if (!session.messagePaused) {
                session.messages += SerialMessage(
                    time = java.time.Instant.now(),
                    direction = SerialMessageDirection.Output,
                    data = byteArrayOf(),
                )
            }
            updateMessageTextIfNotPaused()
        }
    }

    private fun updatePayloadCrcPreview() {
        payloadCrcInlay?.dispose()
        payloadCrcInlay = null

        val text = runCatching {
            val parts = mutableListOf<String>()
            val crc = PayloadCodec.calculateCrc(payloadDocument.text, session.sendConfig)
            if (crc.isNotEmpty()) {
                parts += PayloadCodec.toHex(crc)
            }
            eolPreviewText()?.let { parts += it }
            parts.takeIf { it.isNotEmpty() }?.joinToString(" ", prefix = " ")
        }.getOrElse {
            " Invalid"
        } ?: return

        payloadCrcInlay = payloadEditor.inlayModel.addAfterLineEndElement(
            payloadDocument.textLength,
            true,
            CrcPreviewRenderer(text),
        )
    }

    private fun eolPreviewText(): String? {
        return when (session.sendConfig.appendMode) {
            AppendMode.None -> null
            AppendMode.Cr -> "\\r"
            AppendMode.Lf -> "\\n"
            AppendMode.Crlf -> "\\r\\n"
        }
    }

    private fun updateMessageText() {
        val rendered = renderMessageText()
        val scrollToEnd = isMessageScrolledToEnd()
        ApplicationManager.getApplication().runWriteAction {
            messageEditor.markupModel.removeAllHighlighters()
            messageDocument.setText(rendered.text)
            rendered.ranges.forEach { range ->
                if (range.start < range.end) {
                    messageEditor.markupModel.addRangeHighlighter(
                        range.start,
                        range.end,
                        HighlighterLayer.SYNTAX,
                        TextAttributes(range.color, null, null, null, Font.PLAIN),
                        HighlighterTargetArea.EXACT_RANGE,
                    )
                }
            }
        }
        if (scrollToEnd) {
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed && !messageEditor.isDisposed) {
                    EditorUtil.scrollToTheEnd(messageEditor)
                }
            }
        }
    }

    private fun updateMessageTextIfNotPaused() {
        if (!session.messagePaused) {
            updateMessageText()
        }
    }

    private fun forceUpdateMessageText() {
        updateMessageText()
    }

    private fun isMessageScrolledToEnd(): Boolean {
        val visibleArea = messageEditor.scrollingModel.visibleArea
        val contentHeight = messageEditor.contentComponent.preferredSize.height
        return visibleArea.y + visibleArea.height >= contentHeight - messageEditor.lineHeight
    }

    private fun renderMessageText(): RenderedMessages {
        if (session.messages.isEmpty()) {
            val text = SerialProjectInfo.message()
            return RenderedMessages(text, listOf(MessageTextRange(0, text.length, JBColor.RED)))
        }

        val text = StringBuilder()
        val ranges = mutableListOf<MessageTextRange>()
        session.messages.forEachIndexed { index, message ->
            if (index > 0) text.append('\n')
            val start = text.length
            text.append(normalizeLineSeparators(formatMessageLine(message)))
            ranges += MessageTextRange(start, text.length, Color(messageTextColor(message)))
        }
        return RenderedMessages(text.toString(), ranges)
    }

    private fun formatMessageLine(message: SerialMessage): String {
        val data = decodeMessageText(message)
        if (session.receiveConfig.showTimestamp) {
            return "[${messageFormatter.format(message.time)}] $data"
        }
        return data
    }

    private fun normalizeLineSeparators(text: String): String {
        return text.replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun decodeMessageText(message: SerialMessage): String {
        if (message.direction == SerialMessageDirection.Log) {
            return message.data.toString(Charsets.UTF_8)
        }
        if (session.receiveConfig.textEncoding.equals(PayloadCodec.HEX_ENCODING, ignoreCase = true)) {
            return PayloadCodec.toHex(message.data)
        }
        return runCatching {
            message.data.toString(Charset.forName(session.receiveConfig.textEncoding))
        }.getOrElse {
            message.data.toString(Charsets.UTF_8)
        }
    }

    private fun messageTextColor(message: SerialMessage): Int {
        return when (message.direction) {
            SerialMessageDirection.Input -> session.receiveConfig.textColor
            SerialMessageDirection.Output -> session.sendConfig.textColor
            SerialMessageDirection.Log -> JBColor.RED.rgb
        }
    }

    private fun charsetNames(): Array<String> {
        val preferred = listOf(PayloadCodec.HEX_ENCODING, "US-ASCII", Charsets.UTF_8.name(), "GBK")
        return (preferred + Charset.availableCharsets().keys)
            .distinctBy { it.uppercase() }
            .toTypedArray()
    }

    private fun ColorPanel.makeSquareColorSwatch() {
        val size = JBUI.size(22, 22)
        preferredSize = size
        minimumSize = size
        maximumSize = size
    }

    private fun crcPrototypeItem(items: Array<CrcModeItem>): CrcModeItem {
        return items.firstOrNull { it.algorithm.label.equals("CRC-32/MPEG-2", ignoreCase = true) }
            ?: items.firstOrNull { it.algorithm.label.contains("CRC-32", ignoreCase = true) }
            ?: items.first()
    }

    private fun JComponent.setPreferredWidth(width: Int) {
        preferredSize = Dimension(JBUI.scale(width), preferredSize.height)
    }

    private fun <T> smallComboBox(items: Array<T>, prototypeDisplayValue: T): ComboBox<T> {
        return object : ComboBox<T>(items) {
            override fun getMinimumPopupWidth(): Int {
                val metrics = getFontMetrics(font)
                val itemWidth = items.maxOfOrNull { metrics.stringWidth(it.toString()) } ?: 0
                return maxOf(super.getMinimumPopupWidth(), itemWidth + JBUI.scale(56))
            }
        }.apply {
            this.prototypeDisplayValue = prototypeDisplayValue
            UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, this)
        }
    }

    private fun <T> ComboBox<T>.withSpeedSearch(
        selected: T,
        onSelected: (T) -> Unit,
    ): ComboBox<T> {
        selectedItem = selected
        ComboboxSpeedSearch.installSpeedSearch(this) { item -> item.toString() }
        addActionListener {
            @Suppress("UNCHECKED_CAST")
            (selectedItem as? T)?.let(onSelected)
        }
        return this
    }

    private fun formatDuration(): String {
        val duration = Duration.between(session.statistics.startedAt, java.time.Instant.now())
        return t("minuteSecond", duration.toMinutes(), duration.toSecondsPart())
    }

    private fun updateLanguageText() {
        sendButton.text = t("send")
        hexFormatButton.text = t("hexFormat")
        updateSessionToggleButton()
        updateHexFormatButton()
    }

    private fun updateSessionToggleButton() {
        sessionToggleButton.text = if (sessionPanelVisible) {
            t("hideSettings")
        } else {
            t("showSettings")
        }
    }

    private fun t(key: String, vararg params: Any): String {
        return SerialBundle.message(workspace.language, key, *params)
    }

    private inner class PacketModeItem(val mode: PacketMode) {
        override fun toString(): String = t("packetMode.${mode.name}")
    }

    private inner class AppendModeItem(val mode: AppendMode) {
        override fun toString(): String = t("appendMode.${mode.name}")
    }

    private inner class CrcModeItem(val algorithm: CrcAlgorithm) {
        override fun toString(): String {
            if (algorithm.id == CrcAlgorithms.NONE_ID) return CrcAlgorithms.NONE_ID
            return algorithm.label.removePrefix("CRC-").removePrefix("CRC_")
        }
    }

    private inner class ByteOrderModeItem(val mode: ByteOrderMode) {
        override fun toString(): String = t("byteOrderMode.${mode.name}")
    }

    private inner class PauseMessagesAction : DumbAwareAction() {
        override fun displayTextInToolbar(): Boolean = false

        override fun update(event: AnActionEvent) {
            val key = if (session.messagePaused) "resume" else "pause"
            event.presentation.text = t(key)
            event.presentation.description = t(key)
            event.presentation.icon = if (session.messagePaused) {
                AllIcons.Actions.Resume
            } else {
                AllIcons.Actions.Pause
            }
        }

        override fun actionPerformed(event: AnActionEvent) {
            session.messagePaused = !session.messagePaused
            if (!session.messagePaused) {
                forceUpdateMessageText()
            }
        }
    }

    private inner class ClearMessagesAction : DumbAwareAction() {
        override fun displayTextInToolbar(): Boolean = false

        override fun update(event: AnActionEvent) {
            event.presentation.text = t("clear")
            event.presentation.description = t("clear")
            event.presentation.icon = AllIcons.Actions.GC
        }

        override fun actionPerformed(event: AnActionEvent) {
            workspace.clearSessionMessages(sessionId)
            forceUpdateMessageText()
        }
    }

    private class CrcPreviewRenderer(private val text: String) : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val component = inlay.editor.contentComponent
            return component.getFontMetrics(component.font).stringWidth(text)
        }

        override fun paint(
            inlay: Inlay<*>,
            graphics: Graphics2D,
            targetRegion: Rectangle2D,
            textAttributes: TextAttributes,
        ) {
            val component = inlay.editor.contentComponent
            graphics.font = component.font
            graphics.color = JBColor.GRAY
            graphics.drawString(text, targetRegion.x.toFloat(), (targetRegion.y + inlay.editor.ascent).toFloat())
        }
    }

    private data class RenderedMessages(
        val text: String,
        val ranges: List<MessageTextRange>,
    )

    private data class MessageTextRange(
        val start: Int,
        val end: Int,
        val color: Color,
    )
}
