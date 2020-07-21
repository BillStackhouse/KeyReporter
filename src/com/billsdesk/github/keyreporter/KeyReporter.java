package com.billsdesk.github.keyreporter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 * Find all KeyStrokes in one or more: JFrame, JComponent, JMenuBar, JMenu, JMenuItem, and InputMap.
 * Produce a report sorted by key strokes marking any duplicates. Reports maybe created as tab
 * delimited files, .csv file that can be opened directly in a spreadsheet program, or displayed in
 * a window using JTable. Files are encoded as UTF-8 and the .csv file is generated so that
 * Microsoft Excel will open it correctly without using the import Wizard.
 * <p>
 * <b>Example</b>
 * </p>
 * <pre>{@code
 *      KeyManager.getInstance()
 *          .registerJFrame(frame)
 *          .reportTable(new Dimension(1000, 800);
 * }</pre>
 * <p>
 * <b>Screenshot</b>
 * <p>
 * <img src="doc-files/KeyReporter.jpg" width="100%" alt="KeyReporter.jpg">
 * </p>
 * <b>Build Requirements</b><br>
 * Java 11
 * <p>
 * <b>Contact Information</b>
 * </p>
 * Author: Bill Stackhouse<br>
 * email: billsdesk@gmail.com<br>
 *
 * @author Bill
 * @version $Rev: 8245 $ $Date: 2020-07-21 14:09:12 -0700 (Tue, 21 Jul 2020) $
 */
public class KeyReporter {

    private static Properties properties = new Properties();
    static {
        // if file not found then strings will have default values
        setProperties(KeyReporter.class, "messages.properties");
    }

    /**
     * Allow User to specify a different properties files for the external strings. Must be called
     * prior to creating the dialog.
     *
     * @param aClass
     *            Class for the anchor of the location
     * @param name
     *            name of the properties, may include relative path
     */
    public static void setProperties(final Class< ? > aClass, final String name) {
        try {
            properties.load(aClass.getResourceAsStream(name));
        } catch (final NullPointerException | IOException error) {
            // not found, leave empty and use default values.
        }
    }

    private static final String STR_ACTION     =                               //
            properties.getProperty("KeyReporter.action", "Action");
    private static final String STR_ANCESTOR   =                               //
            properties.getProperty("KeyReporter.ancestor", "Ancestor");
    private static final String STR_CLASS      =                               //
            properties.getProperty("KeyReporter.class", "Class");
    private static final String STR_DUP        =                               //
            properties.getProperty("KeyReporter.dup", "Dup");
    private static final String STR_FOCUS_TYPE =                               //
            properties.getProperty("KeyReporter.focus_type", "Focus Type");
    private static final String STR_FOCUSED    =                               //
            properties.getProperty("KeyReporter.focused", "Focused");
    private static final String STR_IN_FOCUS   =                               //
            properties.getProperty("KeyReporter.in_focused", "In Focus");
    private static final String STR_KEYSTROKE  =                               //
            properties.getProperty("KeyReporter.keystroke", "Key Stroke");

    private static List<String> sColumnTitles  = Arrays.asList(STR_CLASS,
                                                               STR_DUP,
                                                               STR_KEYSTROKE,
                                                               STR_ACTION,
                                                               STR_FOCUS_TYPE);
    private static KeyReporter  sInstance      = new KeyReporter();

    public static KeyReporter getInstance() {
        return sInstance;
    }

    private final List<AbstractKey> mEntries = new ArrayList<>();

    /**
     * Register all JCompoenents in JFrame.
     *
     * @param frame
     *            frame to register
     * @return this
     */
    public KeyReporter registerJFrame(final JFrame frame) {
        final JMenuBar menuBar = frame.getJMenuBar();
        if (menuBar != null) {
            registerMenuBar(menuBar);
        }
        for (final Component component : getAllComponents(frame.getContentPane())) {
            if (component instanceof JComponent) {
                registerComponent((JComponent) component);
            }
        }
        return this;
    }

    /**
     * Register all menu in a menu bar.
     *
     * @param menuBar
     *            menu bar
     * @return this
     */
    public KeyReporter registerMenuBar(final JMenuBar menuBar) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            registerMenu(menuBar.getMenu(i));
        }
        return this;
    }

    /**
     * Register all items in menu.
     *
     * @param menu
     *            menu
     * @return this
     */
    public KeyReporter registerMenu(final JMenu menu) {
        for (int i = 0; i < menu.getMenuComponentCount(); i++) {
            registerMenuItem(menu.getMenuComponent(i));
        }
        return this;
    }

    /**
     * Register a single menu item.
     *
     * @param item
     *            if menu then process its menu items.
     * @return this
     */
    public KeyReporter registerMenuItem(final Component item) {
        if (item instanceof JMenu) {
            registerMenu((JMenu) item);
        } else if (item instanceof JMenuItem) {
            register(new KeyReporter.MenuEntry((JMenuItem) item));
        }
        return this;
    }

    /**
     * Register all key strokes for WHEN_FOCUSED, WHEN_IN_FOCUSED_WINDOW, and
     * WHEN_ANCESTOR_OF_FOCUSED_COMPONENT input maps.
     *
     * @param component
     *            component to register
     * @return this
     */
    public KeyReporter registerComponent(final JComponent component) {
        registerInputMap(component,
                         component.getInputMap(JComponent.WHEN_FOCUSED),
                         FocusType.WHEN_FOCUSED);
        registerInputMap(component,
                         component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW),
                         FocusType.WHEN_IN_FOCUSED_WINDOW);
        registerInputMap(component,
                         component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT),
                         FocusType.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        return this;
    }

    /**
     * Register a single input map.
     *
     * @param component
     *            component for this input map used to list class name in reports
     * @param inputMap
     *            input map to register
     * @param type
     *            a prefix to mark this input map in the report
     * @return this
     */
    public KeyReporter registerInputMap(final JComponent component,
                                        final InputMap inputMap,
                                        final FocusType type) {
        final KeyStroke[] keyStrokes = inputMap.allKeys();
        if (keyStrokes != null) { // sometimes null with WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
            Arrays.asList(keyStrokes).stream().forEach(key -> {
                register(new InputMapEntry(component, key, inputMap.get(key), type));
            });
        }
        return this;
    }

    /**
     * Create a tab delimited report in a file.
     *
     * @param file
     *            File to write.
     * @return this
     */
    public KeyReporter report(final File file) {
        final String string = mEntries.stream()
                                      .sorted(AbstractKey.SORTED)
                                      .map(AbstractKey::toString)
                                      .collect(Collectors.joining(System.lineSeparator()));
        try {
            if (file.exists()) {
                file.delete();
            }
            Files.writeString(Path.of(file.getParent(), file.getName()),
                              string,
                              StandardOpenOption.CREATE);
        } catch (final IOException error) {
        }
        return this;
    }

    /**
     * Create the report as a .csv file encoded as UTF-8 in such a way that Microsoft Excel will
     * open it correctly without using the Import Wizard.
     *
     * @param file
     *            file to write
     * @return this
     * @throws IllegalArgumentException
     *             file is a directory or any IOEException thrown during the report generation
     */
    public KeyReporter reportCsv(final File file) throws IllegalArgumentException {
        final CsvFile csv = new CsvFile(file);
        csv.addFields(sColumnTitles).writeRecord();
        mEntries.stream()
                .sorted(AbstractKey.SORTED)
                .forEach(e -> csv.addFields(e.toCsv()).writeRecord());
        return this;
    }

    /**
     * Create the report in a window using a JTable with columns that can be sorted.
     *
     * @param frameSize
     *            size of the window, used to help size the columns.
     * @return this
     */
    public TableFrame reportTable(final Dimension frameSize) {
        final TableFrame table = new TableFrame(mEntries, frameSize);
        return table;
    }

    public KeyReporter clear() {
        mEntries.clear();
        return this;
    }

    private static List<Component> getAllComponents(final Container container) {
        final List<Component> result = new ArrayList<Component>();
        for (final Component component : container.getComponents()) {
            result.add(component);
            if (component instanceof Container) {
                result.addAll(getAllComponents((Container) component));
            }
        }
        return result;
    }

    private boolean isDuplicate(final AbstractKey info) {
        final Optional<AbstractKey> result = mEntries.stream()
                                                     .filter(entry -> entry.isSameKeyStroke(info))
                                                     .findFirst();
        return result.isPresent();
    }

    private KeyReporter register(final AbstractKey info) throws IllegalArgumentException {
        mEntries.add(info);
        return this;
    }

    private enum FocusType {

        WHEN_FOCUSED(STR_FOCUSED), //
        WHEN_IN_FOCUSED_WINDOW(STR_IN_FOCUS), //
        WHEN_ANCESTOR_OF_FOCUSED_COMPONENT(STR_ANCESTOR);

        private final String mTitle;

        private FocusType(final String title) {
            mTitle = title;
        }

        public String getTitle() {
            return mTitle;
        }
    };

    private abstract static class AbstractKey {

        private final Class< ? > mComponentClass;
        private final KeyStroke  mKeyStroke;
        private final String     mDescription;

        public AbstractKey(final Class< ? > compoenentClass,
                           final KeyStroke keyStroke,
                           final String description) {
            mComponentClass = compoenentClass;
            mKeyStroke = keyStroke == null ? KeyStroke.getKeyStroke(0, 0) : keyStroke;
            mDescription = description;
        }

        public KeyStroke getKeyStroke() {
            return mKeyStroke;
        }

        public int getKeyCode() {
            return mKeyStroke.getKeyCode();
        }

        public int getModifiers() {
            return mKeyStroke.getModifiers();
        }

        public String getDescription() {
            return mDescription;
        }

        public static final Comparator<AbstractKey> SORTED = // NOCHECK
                Comparator.comparing(AbstractKey::getKeyCode)
                          .thenComparing(AbstractKey::getModifiers)
                          .thenComparing(AbstractKey::getDescription);

        public boolean isUnusedKeyStroke() {
            return mKeyStroke.getKeyCode() == 0 && mKeyStroke.getModifiers() == 0;
        }

        public boolean isDefinedKeyStroke() {
            return !isUnusedKeyStroke();
        }

        public boolean isSameKeyStroke(final AbstractKey info) {
            return this != info // is not same object
                   && isDefinedKeyStroke()
                   && info.isDefinedKeyStroke()
                   && mKeyStroke.equals(info.mKeyStroke);
        }

        @Override
        public String toString() {
            String dup;
            String keystroke;
            if (isUnusedKeyStroke()) {
                dup = "";
                keystroke = "";
            } else {
                dup = KeyReporter.getInstance().isDuplicate(this) ? "*" : " ";
                keystroke = keyStrokeString(mKeyStroke);
            }
            return String.format("%s\t%s\t%s\t%s",
                                 mComponentClass.getSimpleName(),
                                 dup,
                                 keystroke,
                                 mDescription);
        }

        public List<String> toCsv() {
            return Arrays.asList(toString().split("\t", Integer.MAX_VALUE));
        }

        public String[] toRow() {
            return toString().split("\t", Integer.MAX_VALUE);
        }

        /**
         * Get KeyStroke toString() and if on MacOS system change the text.
         *
         * @param keyStroke
         *            keyStroke
         * @return text
         * @see <a href=
         *      "https://apple.stackexchange.com/questions/4074/what-do-i-type-to-produce-the-command-symbol-in-mac-os-x">stackexchange.com
         *      </a>
         */
        private String keyStrokeString(final KeyStroke keyStroke) {
            String result = keyStroke.toString();
            if (System.getProperty("os.name").equals("Mac OS X")) {
                // use MacOS terms
                result = result.replaceFirst("pressed ", "");
                result = result.replaceFirst("meta ", "⌘ "); // command
                result = result.replaceFirst("alt ", "⌥ "); // option
                result = result.replaceFirst("ctrl ", "⌃ "); // option
                result = result.replaceFirst("shift ", "⇧ "); // shift
            }
            return result;
        }
    }

    private static class MenuEntry
        extends
            AbstractKey {

        private final JMenuItem mMenuItem;

        public MenuEntry(final JMenuItem menuItem) {
            super(menuItem.getClass(), menuItem.getAccelerator(), menuItem.getText());
            mMenuItem = menuItem;
        }
    }

    private static class InputMapEntry
        extends
            AbstractKey {

        private final FocusType mType;

        public InputMapEntry(final JComponent component,
                             final KeyStroke keyStroke,
                             final Object actionMapKey,
                             final FocusType type) {
            super(component.getClass(), keyStroke, actionMapKey.toString());
            mType = type;
        }

        @Override
        public String toString() {
            return String.format("%s\t%s", super.toString(), mType.getTitle());
        }
    }

    /**
     * Write CSV file and make so MS Excel (or any standard .csv reader) opens as UTF-8 text.
     */
    private static class CsvFile {

        private final List<String> mFields = new ArrayList<>();
        private final File         mFile;

        /**
         * Create a .csv file.
         *
         * @param file
         *            file to write
         * @throws IllegalArgumentException
         *             thrown if file is a directory.
         */
        public CsvFile(final File file) throws IllegalArgumentException {
            mFile = file;
            if (mFile.exists()) {
                if (file.isDirectory()) {
                    throw new IllegalArgumentException(file.getAbsolutePath());
                }
                mFile.delete();
            }
            try {
                mFile.createNewFile();
                // Write an Excel BOM that indicates this is a UTF-8 file, a standard for .csv
                // files so any application reading it will process this properly.
                Files.writeString(Path.of(mFile.getParent(), mFile.getName()),
                                  new String(new byte[]{
                                                        (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
                                  }, StandardCharsets.UTF_8),
                                  StandardCharsets.UTF_8,
                                  StandardOpenOption.APPEND);
            } catch (final IOException error) {
                // ignore
            }
        }

        /**
         * Add a list of fields to be written to file.
         *
         * @param fields
         *            list of strings
         * @return this
         */
        public CsvFile addFields(final List<String> fields) {
            mFields.addAll(fields);
            return this;
        }

        /**
         * Write accumulated fields to file followed by separator (LF, CRLF, etc.).
         *
         * @throws IllegalArgumentException
         *             any IOException
         * @return this
         */
        public CsvFile writeRecord() throws IllegalArgumentException {
            final String data = mFields.stream()
                                       .map(s -> escape(s))
                                       .collect(Collectors.joining(","));
            try {
                Files.writeString(Path.of(mFile.getParent(), mFile.getName()),
                                  data + System.lineSeparator(),
                                  StandardCharsets.UTF_8,
                                  StandardOpenOption.APPEND);
            } catch (final IOException error) {
                throw new IllegalArgumentException(error);
            }
            mFields.clear();
            return this;
        }

        private String escape(String data) { // NOCHECK
            String escapedData = data.replaceAll("\\R", " ");
            if (data.contains(",") || data.contains("\"") || data.contains("'")) {
                data = data.replace("\"", "\"\"");
                escapedData = "\"" + data + "\"";
            }
            return escapedData;
        }
    }

    /**
     * Display the results in a JTable. Public so that report Table() can return the JTable for
     * additional settings.
     */
    public static class TableFrame
        extends
            JFrame {

        private static final long serialVersionUID = 1L;

        public TableFrame(final List<AbstractKey> list, final Dimension frameSize) {
            super("Key Usage");

            final List<String> columns = new ArrayList<String>();
            final List<String[]> values = new ArrayList<String[]>();

            columns.addAll(sColumnTitles);
            list.stream().sorted(AbstractKey.SORTED).forEach(e -> {
                final String[] data = e.toRow();
                final String[] row = new String[columns.size()];
                for (int i = 0; i < row.length; i++) {
                    row[i] = "";
                }
                System.arraycopy(data, 0, row, 0, data.length);
                values.add(row);
            });

            final TableModel tableModel = //
                    new DefaultTableModel(values.toArray(new Object[][]{}), columns.toArray());
            final JTable table = new JTable(tableModel);
            table.setFont(new Font("Courier New", Font.PLAIN, 16));
            table.setAutoCreateRowSorter(true);
            table.setShowGrid(false);
            table.setShowVerticalLines(true);
            table.setGridColor(Color.LIGHT_GRAY);
            table.setIntercellSpacing(new Dimension(5, 0)); // pixels either side of cell
            table.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
            final TableColumnModel columnModel = table.getColumnModel();
            columnModel.getColumn(0).setPreferredWidth((int) (frameSize.width * 0.20));
            columnModel.getColumn(1).setPreferredWidth((int) (frameSize.width * 0.03));
            columnModel.getColumn(2).setPreferredWidth((int) (frameSize.width * 0.15));
            columnModel.getColumn(3).setPreferredWidth((int) (frameSize.width * 0.35));
            getContentPane().setLayout(new BorderLayout());
            getContentPane().add(table.getTableHeader(), BorderLayout.NORTH);
            getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
            setSize(frameSize);
            setLocation(200, 25);
            setVisible(true);
        }
    }
}
