package com.billsdesk.github.keyreporter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * @author Bill
 * @version $Rev: 8235 $ $Date: 2020-07-20 17:02:32 -0700 (Mon, 20 Jul 2020) $
 */
@TestMethodOrder(MethodOrderer.Alphanumeric.class)
public class KeyReporterTest {

    @BeforeAll
    public static void beforeAll() {
        KeyReporter.getInstance().clear();
    }

    @AfterAll
    public static void afterAll() {
        try {
            final File txt = File.createTempFile("Report", ".txt");
            final File csv = File.createTempFile("Report", ".csv");
            KeyReporter.getInstance().report(txt).reportCsv(csv);
            System.out.println(txt.getAbsolutePath());
            System.out.println(csv.getAbsolutePath());
            final KeyReporter.TableFrame frame = KeyReporter.getInstance()
                                                            .reportTable(new Dimension(1000, 600));
            while (frame.isVisible()) {
                pause(200);
            }
//        TestUtils.pauseSeconds(5);
        } catch (final IOException error) {
        }
    }

    @Test
    public void menu() {
        final JMenuItem open = new JMenuItem();
        open.setText("New");
        setAccelerator(open, KeyEvent.VK_N, 0);
        KeyReporter.getInstance().registerMenuItem(open);

        final JMenuItem close = new JMenuItem();
        close.setText("Close");
        setAccelerator(close, KeyEvent.VK_C, 0);
        KeyReporter.getInstance().registerMenuItem(close);

        final JMenuItem closeall = new JMenuItem();
        closeall.setText("Close All");
        setAccelerator(closeall, KeyEvent.VK_C, 0);
        KeyReporter.getInstance().registerMenuItem(closeall);

        final JMenuItem task = new JMenuItem();
        task.setText("Task");
        setAccelerator(task, 0, 0);
        KeyReporter.getInstance().registerMenuItem(task);

        final JMenuItem junk = new JMenuItem();
        junk.setText("Junk");
        setAccelerator(junk, 0, 0);
        KeyReporter.getInstance().registerMenuItem(junk);
    }

    @Test
    public void inputMap() {
        final JTextPane text = new JTextPane();
        final InputMap inputMap = text.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        final ActionMap actionMap = text.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "actionCancel");
        actionMap.put("actionCancel", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(final ActionEvent event) {
            }
        });
        // override CMD-V in JTextPane and process it with GrabPicFrame.
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, 0), "paste-from-clipboard");
        actionMap.put("paste-from-clipboard", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(final ActionEvent event) {
            }
        });

        KeyReporter.getInstance().registerComponent(text);
    }

    @Test
    public void jframe() {
        final JMenuBar menuBar = new JMenuBar();
        final JMenuItem newItem = new JMenuItem("New");
        setAccelerator(newItem, KeyEvent.VK_N, 0);
        final JMenuItem openItem = new JMenuItem("Open...");
        setAccelerator(openItem, KeyEvent.VK_O, 0);
        final JMenu file = new JMenu("File");
        file.add(newItem);
        file.add(openItem);
        menuBar.add(file);

        final JPanel top = new JPanel();
        top.add(new JComboBox<>());
        final JTable table = new JTable();
        final JPanel center = new JPanel(new BorderLayout());
        center.add(table.getTableHeader(), BorderLayout.NORTH);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        final JPanel bottom = new JPanel();
        bottom.add(new JLabel("Label"));
        bottom.add(new JButton("Button"));

        final JFrame frame = new JFrame();
        frame.setJMenuBar(menuBar);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(center, BorderLayout.CENTER);
        frame.getContentPane().add(bottom, BorderLayout.SOUTH);

        KeyReporter.getInstance().registerJFrame(frame);
    }

    private void setAccelerator(final JMenuItem item, final int key, final int modifier) {
        if (key != 0) {
            final int tempModifer = (System.getProperty("os.name")
                                           .equals("Mac OS X") ? ActionEvent.META_MASK
                                                               : ActionEvent.CTRL_MASK)
                                    | modifier;
            item.setAccelerator(javax.swing.KeyStroke.getKeyStroke(key, tempModifer));
        }

    }

    public static void pause(final long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (final InterruptedException error) {
            // Ignore
        }
    }
}
