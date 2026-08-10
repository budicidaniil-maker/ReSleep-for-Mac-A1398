import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LidSleepBlockerGUI {

    private JFrame frame;
    private JButton toggleButton;
    private JTextArea logArea;

    private boolean isActive = false;
    private boolean wasLidClosed = false;
    private Thread monitorThread;

    public static void main(String[] args) {
        // Запускаем интерфейс в специальном потоке Swing
        SwingUtilities.invokeLater(() -> {
            try {
                LidSleepBlockerGUI window = new LidSleepBlockerGUI();
                window.frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public LidSleepBlockerGUI() {
        initializeUI();
    }

    private void initializeUI() {
        frame = new JFrame("MacBook Clamshell Blocker");
        frame.setBounds(100, 100, 500, 400);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));

        // Обработка закрытия окна (нажатие на крестик)
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isActive) {
                    stopProtection(); // Возвращаем настройки сна перед выходом
                }
                System.exit(0);
            }
        });

        // Верхняя панель с предупреждением
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel("Блокировка аппаратного сна (A1398)");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel warningLabel = new JLabel("Внимание: следите за температурами при закрытой крышке,");
        warningLabel.setForeground(Color.RED);
        warningLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel warningLabel2 = new JLabel("чтобы под нагрузкой не запахло старыми китайскими кедами.");
        warningLabel2.setForeground(Color.RED);
        warningLabel2.setAlignmentX(Component.CENTER_ALIGNMENT);

        topPanel.add(Box.createVerticalStrut(10));
        topPanel.add(titleLabel);
        topPanel.add(warningLabel);
        topPanel.add(warningLabel2);
        topPanel.add(Box.createVerticalStrut(10));
        frame.add(topPanel, BorderLayout.NORTH);

        // Текстовое поле для логов
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(43, 43, 43));
        logArea.setForeground(new Color(169, 183, 198));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Кнопка включения/выключения
        toggleButton = new JButton("Включить блокировку сна");
        toggleButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        toggleButton.setPreferredSize(new Dimension(0, 50));
        toggleButton.addActionListener(e -> toggleProtection());
        frame.add(toggleButton, BorderLayout.SOUTH);

        log("Готово к работе. Нажмите кнопку ниже для старта.");
    }

    private void toggleProtection() {
        if (!isActive) {
            startProtection();
        } else {
            stopProtection();
        }
    }

    private void startProtection() {
        log("\n[i] Запрашиваем права администратора...");
        if (disableSystemSleep()) {
            isActive = true;
            toggleButton.setText("Выключить блокировку сна");
            toggleButton.setForeground(Color.RED);
            log("[OK] Сон системы заблокирован. Запуск мониторинга крышки...");

            // Запускаем бесконечный цикл в отдельном потоке
            monitorThread = new Thread(this::monitorLoop);
            monitorThread.start();
        } else {
            log("[Ошибка] Не удалось заблокировать сон. Вы отменили ввод пароля?");
        }
    }

    private void stopProtection() {
        isActive = false; // Это остановит цикл в monitorThread
        log("\n[i] Возвращаем стандартные настройки...");
        if (enableSystemSleep()) {
            toggleButton.setText("Включить блокировку сна");
            toggleButton.setForeground(Color.BLACK);
            log("[OK] Настройки сна восстановлены.");
        } else {
            log("[Ошибка] Не удалось восстановить настройки сна.");
        }
    }

    private void monitorLoop() {
        wasLidClosed = checkLidClosed(); // Проверяем начальное состояние

        while (isActive) {
            boolean isLidClosed = checkLidClosed();

            if (isLidClosed && !wasLidClosed) {
                log("[!] Крышка закрыта. Принудительно гасим дисплей...");
                turnOffDisplay();
            } else if (!isLidClosed && wasLidClosed) {
                log("[!] Крышка открыта. Дисплей должен включиться.");
                wakeUpDisplay();
            }

            wasLidClosed = isLidClosed;

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log("[i] Мониторинг остановлен.");
    }

    // --- Системные методы ---

    private void log(String message) {
        // Безопасное обновление интерфейса из фонового потока
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            // Прокрутка вниз
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private boolean disableSystemSleep() {
        try {
            String[] cmd = {"osascript", "-e", "do shell script \"pmset -a disablesleep 1\" with administrator privileges"};
            Process p = Runtime.getRuntime().exec(cmd);
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean enableSystemSleep() {
        try {
            String[] cmd = {"osascript", "-e", "do shell script \"pmset -a disablesleep 0\" with administrator privileges"};
            Process p = Runtime.getRuntime().exec(cmd);
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkLidClosed() {
        try {
            String[] cmd = {"sh", "-c", "ioreg -r -k AppleClamshellState -d 4 | grep AppleClamshellState"};
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Yes")) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Игнорируем
        }
        return false;
    }

    private void turnOffDisplay() {
        try {
            Runtime.getRuntime().exec(new String[]{"pmset", "displaysleepnow"});
        } catch (Exception e) {
            // Игнорируем
        }
    }

    private void wakeUpDisplay() {
        try {
            Runtime.getRuntime().exec(new String[]{"caffeinate", "-u", "-t", "1"});
        } catch (Exception e) {
            // Игнорируем
        }
    }
}