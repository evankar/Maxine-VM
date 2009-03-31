/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.ins;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

import javax.swing.*;

import com.sun.max.collect.*;
import com.sun.max.ins.InspectionSettings.*;
import com.sun.max.ins.InspectorKeyBindings.*;
import com.sun.max.ins.gui.*;
import com.sun.max.program.*;
import com.sun.max.program.option.*;
import com.sun.max.tele.*;
import com.sun.max.tele.debug.*;
import com.sun.max.tele.debug.TeleProcess.*;
import com.sun.max.util.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.classfile.*;

/**
 * Holds the user interaction state for the inspection of a Maxine VM, which is accessed via a {@link TeleVM} surrogate.
 *
 * @author Bernd Mathiske
 * @author Michael Van De Vanter
 */
public class Inspection extends JFrame {

    private static final int TRACE_VALUE = 2;

    /**
     * @return a string suitable for tagging all trace lines; mention the thread if it isn't the AWT event handler.
     */
    private String tracePrefix() {
        if (java.awt.EventQueue.isDispatchThread()) {
            return "[Inspector] ";
        }
        return "[Inspector: " + Thread.currentThread().getName() + "] ";
    }

    private static final String _inspectorName = "Maxine Inspector";

    /**
     * Constants specifying the state of the Inspection and {@link TeleVM}, if present. These are a superset of
     * {@link TeleProcess.State}.
     */
    public enum InspectionState {

        /**
         * Inspection state when there is and has been no process associated with the session.
         */
        NO_PROCESS,

        /**
         * Inspection state when there is a {@link TeleVM} process and it is currently stopped, but not in a GC.
         */
        STOPPED,

        /**
         * Inspection state when there is a {@link TeleVM} process and it is currently stopped in a GC.
         */
        STOPPED_IN_GC,

        /**
         * Inspection state when there is a {@link TeleVM} process and it is currently running.
         */
        RUNNING,

        /**
         * Inspection state when there was a {@link TeleVM} process that has terminated.
         */
        TERMINATED;
    }

    private InspectionState _inspectionState = InspectionState.NO_PROCESS;

    private final TeleVM _teleVM;

    /**
     * @return the Maxine VM being inspected
     */
    public TeleVM teleVM() {
        return _teleVM;
    }

    private final String _bootImageFileName;

    /**
     * @return Is the Inspector in debugging mode with a legitimate process?
     */
    public boolean hasProcess() {
        return !(_inspectionState == InspectionState.NO_PROCESS || _inspectionState == InspectionState.TERMINATED);
    }

    private boolean _investigateWordValues = true;

    /**
     * @return Does the Inspector attempt to discover proactively what word values might point to in the {@link TeleVM}.
     */
    public boolean investigateWordValues() {
        return _investigateWordValues;
    }

    private final TeleVMTrace _teleVMTrace;

    public TeleVMTrace teleVMTrace() {
        return _teleVMTrace;
    }

    private final InspectorNameDisplay _nameDisplay;

    /**
     * @return Inspection utility for generating standard, human-intelligible names for entities in the inspection
     *         environment.
     */
    public InspectorNameDisplay nameDisplay() {
        return _nameDisplay;
    }

    private final InspectorStyleFactory _styleFactory;
    private InspectorStyle _style;

    /**
     * The current configuration for visual style.
     */
    public InspectorStyle style() {
        return _style;
    }

    private void setStyle(InspectorStyle style) {
        _style = style;
        updateViewConfiguration();
    }

    private InspectorGeometry _geometry;

    /**
     * Layout configurations, generally only the defaults.
     */
    public InspectorGeometry geometry() {
        return _geometry;
    }

    private final InspectionFocus _focus;

    /**
     * User oriented focus on particular items in the environment; View state.
     */
    public InspectionFocus focus() {
        return _focus;
    }

    /**
     * Gets the current key binding map for this inspection.
     */
    public KeyBindingMap keyBindingMap() {
        return _keyBindingMap;
    }

    /**
     * Updates the current key binding map for this inspection.
     *
     * @param keyBindingMap a key binding map. If this value differs from the {@linkplain #keyBindingMap() current key
     *            binding map}, then the accelerator keys of all the relevant inspector actions are updated.
     */
    public void setKeyBindingMap(KeyBindingMap keyBindingMap) {
        if (keyBindingMap != _keyBindingMap) {
            _keyBindingMap = keyBindingMap;
            for (InspectorAction inspectorAction : _actionsWithKeyBindings) {
                final KeyStroke keyStroke = keyBindingMap.get(inspectorAction.getClass());
                Trace.line(TRACE_VALUE, "Binding " + keyStroke + " to " + inspectorAction);
                inspectorAction.putValue(Action.ACCELERATOR_KEY, keyStroke);
            }
        }
    }

    private KeyBindingMap _keyBindingMap = InspectorKeyBindings.DEFAULT_KEY_BINDINGS;

    private final AppendableSequence<InspectorAction> _actionsWithKeyBindings = new ArrayListSequence<InspectorAction>();

    /**
     * Informs this inspection of a new action that can operate on this inspection.
     */
    public void registerAction(InspectorAction inspectorAction) {
        final Class<? extends InspectorAction> actionClass = inspectorAction.getClass();
        if (InspectorKeyBindings.KEY_BINDABLE_ACTIONS.contains(actionClass)) {
            _actionsWithKeyBindings.append(inspectorAction);
            final KeyStroke keyStroke = _keyBindingMap.get(actionClass);
            inspectorAction.putValue(Action.ACCELERATOR_KEY, keyStroke);
        }
    }

    private final InspectionPreferences _preferences = new InspectionPreferences();

    public InspectionPreferences preferences() {
        return _preferences;
    }

    private static final String KEY_BINDINGS_PREFERENCE = "keyBindings";
    private static final String DISPLAY_STYLE_PREFERENCE = "displayStyle";
    private static final String INVESTIGATE_WORD_VALUES_PREFERENCE = "investigateWordValues";
    private static final String EXTERNAL_VIEWER_PREFERENCE = "externalViewer";

    public class InspectionPreferences extends AbstractSaveSettingsListener {

        public InspectionPreferences() {
            super("inspection", Inspection.this);
        }

        public void saveSettings(SaveSettingsEvent settings) {
            settings.save(KEY_BINDINGS_PREFERENCE, keyBindingMap().name());
            settings.save(DISPLAY_STYLE_PREFERENCE, style().name());
            settings.save(INVESTIGATE_WORD_VALUES_PREFERENCE, _investigateWordValues);
            settings.save(EXTERNAL_VIEWER_PREFERENCE, _externalViewerType.name());
            for (ExternalViewerType externalViewerType : ExternalViewerType.VALUES) {
                final String config = _externalViewerConfig.get(externalViewerType);
                if (config != null) {
                    settings.save(EXTERNAL_VIEWER_PREFERENCE + "." + externalViewerType.name(), config);
                }
            }
        }

        void initialize() {
            try {
                _settings.addSaveSettingsListener(this);
                if (_settings.containsKey(this, KEY_BINDINGS_PREFERENCE)) {
                    final String keyBindingsName = _settings.get(this, KEY_BINDINGS_PREFERENCE, OptionTypes.STRING_TYPE, null);

                    final KeyBindingMap keyBindingMap = KeyBindingMap.ALL.get(keyBindingsName);
                    if (keyBindingMap != null) {
                        setKeyBindingMap(keyBindingMap);
                    } else {
                        ProgramWarning.message("Unknown key bindings name ignored: " + keyBindingsName);
                    }
                }

                if (_settings.containsKey(this, DISPLAY_STYLE_PREFERENCE)) {
                    final String displayStyleName = _settings.get(this, DISPLAY_STYLE_PREFERENCE, OptionTypes.STRING_TYPE, null);
                    InspectorStyle style = _styleFactory.findStyle(displayStyleName);
                    if (style == null) {
                        style = _styleFactory.defaultStyle();
                    }
                    _style = style;
                }

                _investigateWordValues = _settings.get(this, INVESTIGATE_WORD_VALUES_PREFERENCE, OptionTypes.BOOLEAN_TYPE, true);
                _externalViewerType = _settings.get(this, EXTERNAL_VIEWER_PREFERENCE, new OptionTypes.EnumType<ExternalViewerType>(ExternalViewerType.class), ExternalViewerType.NONE);
                for (ExternalViewerType externalViewerType : ExternalViewerType.VALUES) {
                    final String config = _settings.get(this, "externalViewer." + externalViewerType.name(), OptionTypes.STRING_TYPE, null);
                    _externalViewerConfig.put(externalViewerType, config);
                }
            } catch (Option.Error optionError) {
                ProgramWarning.message(optionError.getMessage());
            }
        }

        /**
         * Gets a panel for configuring which key binding map is active.
         */
        public JPanel getPanel() {
            final JPanel panel = new InspectorPanel(Inspection.this);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

            final JPanel keyBindingsPanel = getUIPanel();
            final JPanel debugPanel = getDebugPanel();
            final JPanel externalViewerPanel = getExternalViewerPanel();

            keyBindingsPanel.setBorder(BorderFactory.createEtchedBorder());
            debugPanel.setBorder(BorderFactory.createEtchedBorder());
            externalViewerPanel.setBorder(BorderFactory.createEtchedBorder());

            panel.add(keyBindingsPanel);
            panel.add(debugPanel);
            panel.add(externalViewerPanel);

            return panel;
        }

        /**
         * Gets a panel for configuring which key binding map is active.
         */
        private JPanel getUIPanel() {

            final JPanel interiorPanel = new InspectorPanel(Inspection.this);

            // Add key binding chooser
            final Collection<KeyBindingMap> allKeyBindingMaps = KeyBindingMap.ALL.values();
            final JComboBox keyBindingsComboBox = new InspectorComboBox(Inspection.this, allKeyBindingMaps.toArray());
            keyBindingsComboBox.setSelectedItem(_keyBindingMap);
            keyBindingsComboBox.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    setKeyBindingMap((KeyBindingMap) keyBindingsComboBox.getSelectedItem());
                }
            });
            interiorPanel.add(new TextLabel(Inspection.this, "Key Bindings:  "));
            interiorPanel.add(keyBindingsComboBox);

            // Add display style chooser
            final JComboBox uiComboBox = new InspectorComboBox(Inspection.this, _styleFactory.allStyles());
            uiComboBox.setSelectedItem(_style);
            uiComboBox.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    setStyle((InspectorStyle) uiComboBox.getSelectedItem());
                }
            });
            interiorPanel.add(new TextLabel(Inspection.this, "Display style:  "));
            interiorPanel.add(uiComboBox);

            final JPanel panel = new InspectorPanel(Inspection.this, new BorderLayout());
            panel.add(interiorPanel, BorderLayout.WEST);
            return panel;
        }

        /**
         * @return a GUI panel for setting debugging preferences
         */
        private JPanel getDebugPanel() {

            final JCheckBox wordValueCheckBox =
                new InspectorCheckBox(Inspection.this,
                                "Investigate memory references",
                                "Should displayed memory words be investigated as possible references by reading from the VM",
                                _investigateWordValues);
            wordValueCheckBox.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent actionEvent) {
                    final JCheckBox checkBox = (JCheckBox) actionEvent.getSource();
                    _investigateWordValues = checkBox.isSelected();
                    updateViewConfiguration();
                }
            });

            final JPanel panel = new InspectorPanel(Inspection.this, new BorderLayout());

            final JPanel interiorPanel = new InspectorPanel(Inspection.this);
            interiorPanel.add(new TextLabel(Inspection.this, "Debugging:  "));

            /*
             * Until there's a good reason for supporting the synchronous debugging mode, it's no longer selectable.
             * This prevents any user confusion as to why the GUI seems to freeze when the VM is running. [Doug]
            interiorPanel.add(synchButton);
            interiorPanel.add(asynchButton);
             */

            interiorPanel.add(wordValueCheckBox);
            panel.add(interiorPanel, BorderLayout.WEST);

            return panel;
        }

        /**
         * Gets a panel for configuring which key binding map is active.
         */
        public JPanel getExternalViewerPanel() {

            final JPanel cards = new InspectorPanel(Inspection.this, new CardLayout());
            final JPanel noneCard = new InspectorPanel(Inspection.this);

            final JPanel processCard = new InspectorPanel(Inspection.this);
            processCard.add(new TextLabel(Inspection.this, "Command: "));
            processCard.setToolTipText("The pattern '$file' will be replaced with the full path to the file and '$line' will be replaced with the line number to view.");
            final JTextArea commandTextArea = new JTextArea(2, 30);
            commandTextArea.setLineWrap(true);
            commandTextArea.setWrapStyleWord(true);
            commandTextArea.setText(_externalViewerConfig.get(ExternalViewerType.PROCESS));
            commandTextArea.addFocusListener(new FocusAdapter() {

                @Override
                public void focusLost(FocusEvent e) {
                    final String command = commandTextArea.getText();
                    setExternalViewerConfiguration(ExternalViewerType.PROCESS, command);
                }
            });
            final JScrollPane scrollPane = new InspectorScrollPane(Inspection.this, commandTextArea);
            processCard.add(scrollPane);

            final JPanel socketCard = new InspectorPanel(Inspection.this);
            final JTextField portTextField = new JTextField(6);
            portTextField.setText(_externalViewerConfig.get(ExternalViewerType.SOCKET));
            socketCard.add(new TextLabel(Inspection.this, "Port: "));
            socketCard.add(portTextField);
            portTextField.addFocusListener(new FocusAdapter() {

                @Override
                public void focusLost(FocusEvent e) {
                    final String portString = portTextField.getText();
                    setExternalViewerConfiguration(ExternalViewerType.SOCKET, portString);
                }
            });

            cards.add(noneCard, ExternalViewerType.NONE.name());
            cards.add(processCard, ExternalViewerType.PROCESS.name());
            cards.add(socketCard, ExternalViewerType.SOCKET.name());

            final ExternalViewerType[] values = ExternalViewerType.values();
            final JComboBox comboBox = new InspectorComboBox(Inspection.this, values);
            comboBox.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    final ExternalViewerType externalViewerType = (ExternalViewerType) comboBox.getSelectedItem();
                    setExternalViewer(externalViewerType);
                    final CardLayout cardLayout = (CardLayout) cards.getLayout();
                    cardLayout.show(cards, externalViewerType.name());
                }
            });
            comboBox.setSelectedItem(_externalViewerType);
            final CardLayout cardLayout = (CardLayout) cards.getLayout();
            cardLayout.show(cards, _externalViewerType.name());

            final JPanel comboBoxPanel = new InspectorPanel(Inspection.this);
            comboBoxPanel.add(new TextLabel(Inspection.this, "External File Viewer:  "));
            comboBoxPanel.add(comboBox);

            final JPanel panel = new InspectorPanel(Inspection.this);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.add(comboBoxPanel);
            panel.add(cards);
            return panel;
        }
    }

    /**
     * Enum for the mechanism to be used when attempting to
     * {@linkplain Inspection#viewSourceExternally(BytecodeLocation) view} a source file in an external tool.
     */
    public enum ExternalViewerType {
        /**
         * Specifies that there is no external viewer available.
         */
        NONE,

        /**
         * Specifies that an external viewer is listening on a socket for 'open file' requests. A request is a string of
         * bytes matching this pattern:
         *
         * <pre>
         *     &lt;path to file&gt;|&lt;line number&gt;
         * </pre>
         *
         * For example, the following code generates the bytes of a typical command:
         *
         * <pre>
         * &quot;/maxine/VM/src/com/sun/max/vm/MaxineVM.java|239&quot;.getBytes()
         * </pre>
         */
        SOCKET,

        /**
         * Specifies that an external tool can be launched as a separate process.
         */
        PROCESS;

        public static final IndexedSequence<ExternalViewerType> VALUES = new ArraySequence<ExternalViewerType>(values());
    }

    private ExternalViewerType _externalViewerType = ExternalViewerType.NONE;

    private final Map<ExternalViewerType, String> _externalViewerConfig = new EnumMap<ExternalViewerType, String>(ExternalViewerType.class);

    public void setExternalViewer(ExternalViewerType externalViewerType) {
        final boolean needToSave = _externalViewerType != externalViewerType;
        _externalViewerType = externalViewerType;
        if (needToSave) {
            settings().save();
        }
    }

    public void setExternalViewerConfiguration(ExternalViewerType externalViewerType, String config) {
        final boolean needToSave = _externalViewerConfig.get(externalViewerType) != config;
        _externalViewerConfig.put(externalViewerType, config);
        if (needToSave) {
            settings().save();
        }
    }

    /**
     * If an external viewer has been {@linkplain #setExternalViewer(ExternalViewerType) configured}, attempt to view a
     * source file location corresponding to a given bytecode location. The view attempt is only made if an existing
     * source file and source line number can be derived from the given bytecode location.
     *
     * @param bytecodeLocation specifies a bytecode position in a class method actor
     * @return true if a file viewer was opened
     */
    public boolean viewSourceExternally(BytecodeLocation bytecodeLocation) {
        if (_externalViewerType == ExternalViewerType.NONE) {
            return false;
        }
        final ClassMethodActor classMethodActor = bytecodeLocation.classMethodActor();
        final CodeAttribute codeAttribute = classMethodActor.codeAttribute();
        final int lineNumber = codeAttribute.lineNumberTable().findLineNumber(bytecodeLocation.bytecodePosition());
        if (lineNumber == -1) {
            return false;
        }
        return viewSourceExternally(classMethodActor.holder(), lineNumber);
    }

    /**
     * If an external viewer has been {@linkplain #setExternalViewer(ExternalViewerType) configured}, attempt to view a
     * source file location corresponding to a given class actor and line number. The view attempt is only made if an
     * existing source file and source line number can be derived from the given bytecode location.
     *
     * @param classActor the class whose source file is to be viewed
     * @param lineNumber the line number at which the viewer should position the current focus point
     * @return true if a file viewer was opened
     */
    public boolean viewSourceExternally(ClassActor classActor, int lineNumber) {
        if (_externalViewerType == ExternalViewerType.NONE) {
            return false;
        }
        final File javaSourceFile = teleVM().findJavaSourceFile(classActor);
        if (javaSourceFile == null) {
            return false;
        }

        switch (_externalViewerType) {
            case PROCESS: {
                final String config = _externalViewerConfig.get(ExternalViewerType.PROCESS);
                if (config != null) {
                    final String command = config.replaceAll("\\$file", javaSourceFile.getAbsolutePath()).replaceAll("\\$line", String.valueOf(lineNumber));
                    try {
                        Trace.line(1, tracePrefix() + "Opening file by executing " + command);
                        Runtime.getRuntime().exec(command);
                    } catch (IOException ioException) {
                        throw new InspectorError("Error opening file by executing " + command, ioException);
                    }
                }
                break;
            }
            case SOCKET: {
                final String hostname = null;
                final String portString = _externalViewerConfig.get(ExternalViewerType.SOCKET);
                if (portString != null) {
                    try {
                        final int port = Integer.parseInt(portString);
                        final Socket fileViewer = new Socket(hostname, port);
                        final String command = javaSourceFile.getAbsolutePath() + "|" + lineNumber;
                        Trace.line(1, tracePrefix() + "Opening file via localhost:" + portString);
                        final OutputStream fileViewerStream = fileViewer.getOutputStream();
                        fileViewerStream.write(command.getBytes());
                        fileViewerStream.flush();
                        fileViewer.close();
                    } catch (IOException ioException) {
                        throw new InspectorError("Error opening file via localhost:" + portString, ioException);
                    }
                }
                break;
            }
            default: {
                ProgramError.unknownCase();
            }
        }
        return true;
    }

    private static final String _SETTINGS_FILE_NAME = "maxine.ins";
    private final InspectionSettings _settings;

    public InspectionSettings settings() {
        return _settings;
    }

    private final InspectionActions _inspectionActions;

    /**
     * @return the global collection of actions, many of which are singletons with state that gets refreshed.
     */
    public InspectionActions actions() {
        return _inspectionActions;
    }

    private final JDesktopPane _desktopPane = new JDesktopPane() {

        /**
         * Any component added to the desktop pane is brought to the front.
         */
        @Override
        public Component add(Component component) {
            super.add(component);
            moveToFront(component);
            return component;
        }
    };

    public JDesktopPane desktopPane() {
        return _desktopPane;
    }

    private final JScrollPane _scrollPane;

    public Rectangle getVisibleBounds() {
        return _scrollPane.getVisibleRect();
    }

    private final InspectorMenu _viewMenu;

    public Point getLocation(Component component) {
        final Point result = new Point();
        Component c = component;
        while (c != null && c != _desktopPane) {
            final Point location = c.getLocation();
            result.translate(location.x, location.y);
            c = c.getParent();
        }
        return result;
    }

    public Inspection(TeleVM teleVM, InspectorGeometry geometry) throws IOException {
        super(_inspectorName);
        _teleVM = teleVM;
        _bootImageFileName = _teleVM.bootImageFile().getAbsolutePath().toString();
        if (!(teleVM().isReadOnly())) {
            _presumedState = State.STOPPED;
            _inspectionState = InspectionState.STOPPED;
        }
        _teleVMTrace = new TeleVMTrace(teleVM);
        _nameDisplay = new InspectorNameDisplay(this);
        _styleFactory = new InspectorStyleFactory(this);
        _style = _styleFactory.defaultStyle();
        _geometry = geometry;
        _scrollPane = new InspectorScrollPane(this, _desktopPane);
        _focus = new InspectionFocus(this);
        _settings = new InspectionSettings(this, new File(teleVM.programFile().getParentFile(), _SETTINGS_FILE_NAME));

        setDefaultLookAndFeelDecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent windowevent) {
                quit();
            }
        });

        setContentPane(_scrollPane);
        _desktopPane.setOpaque(true);
        _desktopPane.setMinimumSize(_geometry.inspectorFrameMinSize());
        _desktopPane.setPreferredSize(_geometry.inspectorFramePrefSize());
        setLocation(_geometry.inspectorFrameDefaultLocation());
        _inspectionActions = new InspectionActions(this);
        _viewMenu = new InspectorMenu();
        _viewMenu.add(_inspectionActions.viewBootImage());
        _viewMenu.add(_inspectionActions.viewMemoryRegions());
        _viewMenu.add(_inspectionActions.viewThreads());
        _viewMenu.add(_inspectionActions.viewVmThreadLocals());
        _viewMenu.add(_inspectionActions.viewRegisters());
        _viewMenu.add(_inspectionActions.viewStack());
        _viewMenu.add(_inspectionActions.viewMethodCode());
        _viewMenu.add(_inspectionActions.viewBreakpoints());
        setJMenuBar(new InspectorMainMenuBar(_inspectionActions));

        _desktopPane.addMouseListener(new InspectorMouseClickAdapter(this) {

            @Override
            public void procedure(final MouseEvent mouseEvent) {
                if (MaxineInspector.mouseButtonWithModifiers(mouseEvent) == MouseEvent.BUTTON3) {
                    _viewMenu.popupMenu().show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
                }
            }
        });
        updateTitle();
        pack();
    }

    /**
     * Updates the string appearing the outermost window frame: program name, process state, bootimage filename.
     *
     */
    private void updateTitle() {
        setTitle(_inspectorName + ": (" + _inspectionState + ") " + _bootImageFileName);
        repaint();
    }

    /**
     * Note that there is a delay, as well as a gap in the AWT event queue between the actual change in the
     * {@link TeleVM} process state and the receipt of notification by the Inspector. This value may not always agree
     * with what's reported by {@link TeleVM#state()}.
     *
     * @return the presumed state of the {@link TeleVM} as of the most recent update, subject to quantum and other
     *         effects.
     */
    private State _presumedState = null;

    /**
     * Is the {@link TeleVM} running, as of the most recent state update? Note that there is a delay, as well as a gap
     * in the AWT event queue, between the stopping of the {@link TeleVM} and the receipt of notification by the
     * Inspector (when running asynchronous), so this value may not always agree with what's reported by
     * {@link TeleVM#state()}. In particular, the AWT event queue might initiate new action sequences when the
     * {@link TeleVM} has actually stopped, but before the Inspector receives notification.
     *
     * @return VM state == {@link State#STOPPED}.
     */
    public boolean isVMRunning() {
        return _presumedState == State.RUNNING;
    }

    /**
     * Is the {@link TeleVM} available to start running, as of the most recent state update?
     *
     * @return VM state == {@link State#STOPPED}.
     */
    public boolean isVMReady() {
        return _presumedState == State.STOPPED;
    }

    /**
     * Handles reported changes in the {@linkplain TeleProcess#state() tele process state}.
     */
    private void processStateChange(StateTransitionEvent e) {
        Tracer tracer = null;
        if (Trace.hasLevel(1)) {
            tracer = new Tracer("process " + e);
        }
        Trace.begin(1, tracer);
        final long startTimeMillis = System.currentTimeMillis();
        _presumedState = e.newState();
        final InspectorMainMenuBar menuBar = (InspectorMainMenuBar) getJMenuBar();
        switch (_presumedState) {
            case STOPPED:
                if (_teleVM.isInGC()) {
                    menuBar.setStateColor(style().vmStoppedinGCBackgroundColor());
                    _inspectionState = InspectionState.STOPPED_IN_GC;
                } else {
                    menuBar.setStateColor(style().vmStoppedBackgroundColor());
                    _inspectionState = InspectionState.STOPPED;
                }
                updateAfterVMStopped(e.epoch());
                break;
            case RUNNING:
                menuBar.setStateColor(style().vmRunningBackgroundColor());
                _inspectionState = InspectionState.RUNNING;
                break;
            case TERMINATED:
                menuBar.setStateColor(style().vmTerminatedBackgroundColor());
                _inspectionState = InspectionState.TERMINATED;
                Trace.line(1, tracePrefix() + " - VM process terminated");
                // Give all process-sensitive views a chance to shut down
                for (InspectionListener listener : _inspectionListeners.clone()) {
                    listener.vmProcessTerminated();
                }
                // Clear any possibly misleading view state.
                focus().clearAll();
                // Be sure all process-sensitive actions are disabled.
                _inspectionActions.refresh(e.epoch(), false);
                break;
        }
        updateTitle();
        _inspectionActions.refresh(e.epoch(), true);
        Trace.end(1, tracer, startTimeMillis);
    }

    /**
     * Preemptively assume that the {@link TeleVM} is running without waiting for notification.
     */
    void assumeRunning() {
        Trace.line(TRACE_VALUE, tracePrefix() + "assuming VM is " + State.RUNNING);
        _presumedState = State.RUNNING;
        _inspectionState = InspectionState.RUNNING;
        final InspectorMainMenuBar menuBar = (InspectorMainMenuBar) getJMenuBar();
        menuBar.setStateColor(style().vmRunningBackgroundColor());
    }

    /**
     * Handles reported changes in the {@linkplain TeleProcess#state() tele process state}. Ensures that the event is
     * handled only on the current AWT event thread.
     */
    private final class ProcessStateListener implements StateTransitionListener {

        public void handleStateTransition(final StateTransitionEvent e) {
            if (java.awt.EventQueue.isDispatchThread()) {
                processStateChange(e);
            } else {
                Tracer tracer = null;
                if (Trace.hasLevel(TRACE_VALUE)) {
                    tracer = new Tracer("scheduled " + e);
                }
                Trace.begin(TRACE_VALUE, tracer);
                SwingUtilities.invokeLater(new Runnable() {

                    public void run() {
                        processStateChange(e);
                    }
                });
                Trace.end(TRACE_VALUE, tracer);
            }
        }
    }

    /**
     * Handles reported breakpoint changes in the {@link TeleVM}.
     */
    private void processBreakpointChange(long epoch) {
        Trace.line(TRACE_VALUE, tracePrefix() + "breakpoint state notification");
        for (InspectionListener listener : _inspectionListeners.clone()) {
            listener.breakpointSetChanged(epoch);
        }
    }

    /**
     * Handles reported breakpoint changes in the {@link TeleVM}. Ensures that the event is handled only on the current
     * AWT event thread.
     */
    private final class BreakpointListener implements TeleViewModel.Listener {

        public void refreshView(final long epoch) {
            if (java.awt.EventQueue.isDispatchThread()) {
                processBreakpointChange(epoch);
            } else {
                SwingUtilities.invokeLater(new Runnable() {

                    public void run() {
                        processBreakpointChange(epoch);
                    }
                });
            }
        }
    }

    /**
     * Delayed initialization of inspection, allows other machinery to be set up first.
     */
    void initialize() throws IOException {
        _preferences.initialize();
        BreakpointPersistenceManager.initialize(this);
        _inspectionActions.refresh(teleVM().epoch(), true);
        // Listen for process state changes
        teleVM().addStateListener(new ProcessStateListener());
        // Listen for changes in breakpoints
        teleVM().addBreakpointListener(new BreakpointListener());
    }

    private InspectorAction _currentAction = null;

    /**
     * Holds the action currently being performed; null when finished.
     */
    public InspectorAction currentAction() {
        return _currentAction;
    }

    void setCurrentAction(InspectorAction action) {
        _currentAction = action;
    }

    /**
     * @return default title for any messages: defaults to name of current {@link InspectorAction} if one is current,
     *         otherwise the generic name of the inspector.
     */
    private String dialogTitle() {
        return _currentAction != null ? _currentAction.name() : _inspectorName;
    }

    /**
     * Displays an information message in a modal dialog with specified frame title.
     */
    public void informationMessage(String message, String title) {
        JOptionPane.showMessageDialog(_desktopPane, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Displays an information message in a modal dialog with default frame title.
     */
    public void informationMessage(String message) {
        informationMessage(message, dialogTitle());
    }

    /**
     * Displays an error message in a modal dialog with specified frame title.
     */
    public void errorMessage(String message, String title) {
        JOptionPane.showMessageDialog(_desktopPane, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Displays an error message in a modal dialog with default frame title.
     */
    public void errorMessage(String message) {
        errorMessage(message, dialogTitle());
    }

    /**
     * Collects textual input from user.
     *
     * @param message a prompt
     * @param initialValue an initial value
     * @return text typed by user
     */
    public String inputDialog(String message, String initialValue) {
        return JOptionPane.showInputDialog(_desktopPane, message, initialValue);
    }

    /**
     * Displays a message and invites text input from user in a modal dialog.
     *
     * @return text typed by user.
     */
    public String questionMessage(String message) {
        return JOptionPane.showInputDialog(_desktopPane, message, dialogTitle(), JOptionPane.QUESTION_MESSAGE);

    }

    private IdentityHashSet<InspectionListener> _inspectionListeners = new IdentityHashSet<InspectionListener>();

    /**
     * Adds a listener for view update when {@link TeleVM} state changes.
     */
    public void addInspectionListener(InspectionListener listener) {
        Trace.line(TRACE_VALUE, tracePrefix() + "adding inspection listener: " + listener);
        synchronized (this) {
            _inspectionListeners.add(listener);
        }
    }

    /**
     * Removes a listener for view update, for example when an Inspector is disposed or when the default notification
     * mechanism is being overridden.
     */
    public void removeInspectionListener(InspectionListener listener) {
        Trace.line(TRACE_VALUE, tracePrefix() + "removing inspection listener: " + listener);
        synchronized (this) {
            _inspectionListeners.remove(listener);
        }
    }

    /**
     * Update all views by reading from {@link TeleVM} state as needed.
     *
     * @param force suspend caching behavior; reload state unconditionally.
     */
    public synchronized void refreshAll(boolean force) {
        final long epoch = teleVM().epoch();
        Tracer tracer = null;
        // Additional listeners may come and go during the update cycle, which can be ignored.
        for (InspectionListener listener : _inspectionListeners.clone()) {
            if (Trace.hasLevel(TRACE_VALUE)) {
                tracer = new Tracer("refresh: " + listener);
            }
            Trace.begin(TRACE_VALUE, tracer);
            final long startTimeMillis = System.currentTimeMillis();
            listener.vmStateChanged(epoch, force);
            Trace.end(TRACE_VALUE, tracer, startTimeMillis);
        }
        _inspectionActions.refresh(epoch, force);
    }

    /**
     * Updates all views, assuming that display and style parameters may have changed; forces state reload from the
     * {@link TeleVM}.
     */
    private synchronized void updateViewConfiguration() {
        final long epoch = teleVM().epoch();
        for (InspectionListener listener : _inspectionListeners) {
            Trace.line(TRACE_VALUE, tracePrefix() + "updateViewConfiguration: " + listener);
            listener.viewConfigurationChanged(epoch);
        }
        _inspectionActions.redisplay();
    }

    private final Tracer _threadTracer = new Tracer("refresh thread state");

    /**
     * Determines what happened in {@link TeleVM} execution that just concluded. Then updates all view state as needed.
     */
    public void updateAfterVMStopped(long epoch) {
        ProgramError.check(_teleVM.state() == State.STOPPED, "State should be stopped, but is " + _teleVM.state());
        setBusy(true);
        final IdentityHashSet<InspectionListener> listeners = _inspectionListeners.clone();
        // Notify of any changes of the thread set

        Trace.begin(TRACE_VALUE, _threadTracer);
        final long startTimeMillis = System.currentTimeMillis();
        final IterableWithLength<TeleNativeThread> deadThreads = teleVM().recentlyDiedThreads();
        final IterableWithLength<TeleNativeThread> startedThreads = teleVM().recentlyCreatedThreads();
        if (deadThreads.length() != 0 || startedThreads.length() != 0) {
            for (InspectionListener listener : listeners) {
                listener.threadSetChanged(epoch);
            }
            for (TeleNativeThread teleNativeThread : teleVM().threads()) {
                for (InspectionListener listener : listeners) {
                    listener.threadStateChanged(teleNativeThread);
                }
            }
        } else {
            // A kind of optimization that keeps the StackInspector from walking every stack every time; is it needed?
            final TeleNativeThread currentThread = focus().thread();
            for (InspectionListener listener : listeners) {
                listener.threadStateChanged(currentThread);
            }
        }
        Trace.end(TRACE_VALUE, _threadTracer, startTimeMillis);
        try {
            refreshAll(false);
            // Make visible the code at the IP of the thread that triggered the breakpoint.
            boolean atBreakpoint = false;
            for (TeleNativeThread teleNativeThread : teleVM().threads()) {
                if (teleNativeThread.breakpoint() != null) {
                    focus().setThread(teleNativeThread);
                    atBreakpoint = true;
                    break;
                }
            }
            if (!atBreakpoint) {
                // If there was no selection based on breakpoint, then check the thread that was selected before the
                // change.
                // TODO (mlvdv) do this some other way, then obsolete isValidThread()
                InspectorError.check(teleVM().isValidThread(focus().thread()), "Selected thread no longer valid");
            }
            // Reset focus to new IP.
            final TeleNativeThread focusThread = focus().thread();
            focus().setStackFrame(focusThread, focusThread.frames().first(), true);
        } catch (Throwable throwable) {
            new InspectorError("could not update view", throwable).display(this);
        } finally {
            setBusy(false);
        }
    }

    private final class DeleteInspectorsAction extends InspectorAction {

        private final Predicate<Inspector> _predicate;

        public DeleteInspectorsAction(Inspection inspection, Predicate<Inspector> predicate, String title) {
            super(inspection, title);
            _predicate = predicate;
        }

        @Override
        public void procedure() {
            for (int i = _desktopPane.getComponentCount() - 1; i >= 0; i--) {
                // Delete backwards so that the indices don't change
                final Component component = _desktopPane.getComponent(i);
                if (component instanceof InspectorFrame) {
                    final InspectorFrame inspectorFrame = (InspectorFrame) component;
                    final Inspector inspector = inspectorFrame.inspector();
                    if (_predicate.evaluate(inspector)) {
                        inspector.dispose();
                    }
                }
            }
            for (Frame frame : Frame.getFrames()) {
                if (frame instanceof InspectorFrame) {
                    final InspectorFrame inspectorFrame = (InspectorFrame) frame;
                    final Inspector inspector = inspectorFrame.inspector();
                    if (_predicate.evaluate(inspector)) {
                        inspector.dispose();
                    }
                }
            }
        }
    }

    private final Cursor _busyCursor = new Cursor(Cursor.WAIT_CURSOR);

    public void setBusy(boolean busy) {
        if (busy) {
            _desktopPane.setCursor(_busyCursor);
        } else {
            _desktopPane.setCursor(null);
        }
    }

    public InspectorAction getDeleteInspectorsAction(Predicate<Inspector> predicate, String title) {
        return new DeleteInspectorsAction(this, predicate, title);
    }

    /**
     * Saves any persistent state, then shuts down {@link TeleVM} process and inspection.
     */
    public void quit() {
        settings().quit();
        try {
            teleVM().terminate();
        } finally {
            Trace.line(1, tracePrefix() + " exiting, Goodbye");
            System.exit(0);
        }
    }

    private Point getMiddle(Component component) {
        final Point point = new Point((getWidth() / 2) - (component.getWidth() / 2), (getHeight() / 2) - (component.getHeight() / 2));
        if (point.y < 0) {
            point.y = 0;
        }
        return point;
    }

    public void moveToMiddle(JComponent inspector) {
        inspector.setLocation(getMiddle(inspector));
    }

    public void moveToMiddle(JDialog dialog) {
        final Point middle = getMiddle(dialog);
        middle.translate(getX(), getY());
        dialog.setLocation(middle);
    }

    public void limitSize(JComponent inspector) {
        if (inspector.getWidth() > getWidth() - 64 || inspector.getHeight() > getHeight() - 64) {
            final int w = Math.min(inspector.getWidth(), getWidth() - 64);
            final int h = Math.min(inspector.getHeight(), getHeight() - 64);
            inspector.setSize(w, h);
        }
    }

    /**
     * An object that delays evaluation of a trace message for controller actions.
     */
    private class Tracer {

        private final String _message;

        /**
         * An object that delays evaluation of a trace message.
         *
         * @param message identifies what is being traced
         */
        public Tracer(String message) {
            _message = message;
        }

        @Override
        public String toString() {
            return tracePrefix() + _message;
        }
    }

}
