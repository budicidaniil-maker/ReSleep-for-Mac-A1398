import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LidSleepBlocker {

    private static boolean wasLidClosed = false;

    public static void main(String[] args) {
        System.out.println("=== Блокировщик сна (Hard Mode) ===");

        // 1. Запрашиваем права администратора и жестко блокируем сон
        disableSystemSleep();

        // 2. Хук для возврата настроек при нажатии кнопки Stop в IDEA
        Runtime.getRuntime().addShutdownHook(new Thread(() -> enableSystemSleep()));

        System.out.println("[i] Начинаем мониторинг состояния крышки...");

        try {
            while (true) {
                boolean isLidClosed = checkLidClosed();

                if (isLidClosed && !wasLidClosed) {
                    System.out.println("[!] Крышка закрыта. Принудительно гасим дисплей...");
                    turnOffDisplay();
                } else if (!isLidClosed && wasLidClosed) {
                    System.out.println("[!] Крышка открыта. Дисплей должен включиться.");
                    wakeUpDisplay();
                }

                wasLidClosed = isLidClosed;
                Thread.sleep(2000);
            }
        } catch (InterruptedException e) {
            System.out.println("[i] Мониторинг прерван.");
        }
    }

    private static void disableSystemSleep() {
        System.out.println("[i] macOS требует пароль для блокировки аппаратного сна (Clamshell Mode)...");
        try {
            // Вызываем системное окно ввода пароля для выполнения команды pmset
            String[] cmd = {
                    "osascript",
                    "-e",
                    "do shell script \"pmset -a disablesleep 1\" with administrator privileges"
            };
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            System.out.println("[OK] Сон системы полностью заблокирован на уровне ядра.");
        } catch (Exception e) {
            System.out.println("[Ошибка] Не удалось заблокировать сон.");
        }
    }

    private static void enableSystemSleep() {
        System.out.println("\n[i] Возвращаем стандартные настройки сна...");
        try {
            String[] cmd = {
                    "osascript",
                    "-e",
                    "do shell script \"pmset -a disablesleep 0\" with administrator privileges"
            };
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            System.out.println("[OK] Настройки сна восстановлены.");
        } catch (Exception e) {
            // Игнорируем
        }
    }

    private static boolean checkLidClosed() {
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
            // Игнорируем ошибки чтения
        }
        return false;
    }

    private static void turnOffDisplay() {
        try {
            Runtime.getRuntime().exec(new String[]{"pmset", "displaysleepnow"});
        } catch (Exception e) {
            // Игнорируем
        }
    }

    private static void wakeUpDisplay() {
        try {
            Runtime.getRuntime().exec(new String[]{"caffeinate", "-u", "-t", "1"});
        } catch (Exception e) {
            // Игнорируем
        }
    }
}