import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MenuBarSleepBlocker {

    private TrayIcon trayIcon;
    private MenuItem toggleItem;
    private MenuItem statusItem;

    private boolean isActive = false;
    private boolean wasLidClosed = false;
    private Thread monitorThread;

    public static void main(String[] args) {
        // Магия macOS: скрываем приложение из нижнего Dock-а
        System.setProperty("apple.awt.UIElement", "true");

        if (!SystemTray.isSupported()) {
            System.err.println("SystemTray не поддерживается вашей системой.");
            return;
        }

        new MenuBarSleepBlocker().initTray();
    }

    private void initTray() {
        SystemTray tray = SystemTray.getSystemTray();

        // Рисуем стартовую иконку (красный кружок)
        Image icon = createIcon(Color.RED);

        // Создаем выпадающее меню
        PopupMenu popup = new PopupMenu();

        statusItem = new MenuItem("Статус: Обычный режим");
        statusItem.setEnabled(false); // Делаем некликабельным, это просто текст
        popup.add(statusItem);

        popup.addSeparator();

        toggleItem = new MenuItem("Включить блокировку сна");
        toggleItem.addActionListener(e -> toggleProtection());
        popup.add(toggleItem);

        popup.addSeparator();

        MenuItem exitItem = new MenuItem("Выход");
        exitItem.addActionListener(e -> {
            if (isActive) {
                stopProtection(); // Возвращаем настройки перед закрытием
            }
            System.exit(0);
        });
        popup.add(exitItem);

        trayIcon = new TrayIcon(icon, "MacBook Clamshell Mode", popup);
        trayIcon.setImageAutoSize(true);

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("Не удалось добавить иконку в трей.");
        }

        // Хук на случай, если процесс убьют через IDE или терминал
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isActive) stopProtection();
        }));
    }

    private void toggleProtection() {
        if (!isActive) {
            startProtection();
        } else {
            stopProtection();
        }
    }

    private void startProtection() {
        trayIcon.displayMessage("Блокировка сна", "Запрос пароля для отключения сна...", TrayIcon.MessageType.INFO);

        if (disableSystemSleep()) {
            isActive = true;
            toggleItem.setLabel("Выключить блокировку");
            statusItem.setLabel("Статус: Сон заблокирован (A1398)");
            trayIcon.setImage(createIcon(Color.GREEN)); // Меняем иконку на зеленую

            // Запускаем мониторинг крышки (чтобы не пахло китайскими кедами от перегрева)
            monitorThread = new Thread(this::monitorLoop);
            monitorThread.start();
        } else {
            trayIcon.displayMessage("Ошибка", "Не удалось заблокировать сон.", TrayIcon.MessageType.ERROR);
        }
    }

    private void stopProtection() {
        isActive = false; // Останавливаем цикл
        if (enableSystemSleep()) {
            toggleItem.setLabel("Включить блокировку сна");
            statusItem.setLabel("Статус: Обычный режим");
            trayIcon.setImage(createIcon(Color.RED)); // Возвращаем красный кружок
        } else {
            trayIcon.displayMessage("Ошибка", "Не удалось вернуть настройки сна.", TrayIcon.MessageType.ERROR);
        }
    }

    private void monitorLoop() {
        wasLidClosed = checkLidClosed();

        while (isActive) {
            boolean isLidClosed = checkLidClosed();

            if (isLidClosed && !wasLidClosed) {
                turnOffDisplay();
            } else if (!isLidClosed && wasLidClosed) {
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
    }

    // --- Рисовалка иконки прямо в памяти ---
    private Image createIcon(Color color) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        // Сглаживание краев
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.fillOval(2, 2, 12, 12);
        g.dispose();
        return image;
    }

    // --- Системные команды ---

    private boolean disableSystemSleep() {
        try {
            String[] cmd = {"osascript", "-e", "do shell script \"pmset -a disablesleep 1\" with administrator privileges"};
            return Runtime.getRuntime().exec(cmd).waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean enableSystemSleep() {
        try {
            String[] cmd = {"osascript", "-e", "do shell script \"pmset -a disablesleep 0\" with administrator privileges"};
            return Runtime.getRuntime().exec(cmd).waitFor() == 0;
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
                if (line.contains("Yes")) return true;
            }
        } catch (Exception e) {}
        return false;
    }

    private void turnOffDisplay() {
        try { Runtime.getRuntime().exec(new String[]{"pmset", "displaysleepnow"}); } catch (Exception e) {}
    }

    private void wakeUpDisplay() {
        try { Runtime.getRuntime().exec(new String[]{"caffeinate", "-u", "-t", "1"}); } catch (Exception e) {}
    }
}