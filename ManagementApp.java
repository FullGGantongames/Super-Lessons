import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.metal.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ManagementApp {
    private static final String STUDENTS_FILE = "students.txt", TESTS_FILE = "tests.txt", COURSES_FILE = "courses.txt";
    private static final String ANSWERS_FILE = "answers.txt", ACCOUNTS_FILE = "accounts.txt", SECRET_TASKS_FILE = "secret_tasks.txt";
    private static final String CLASSES_FILE = "classes.txt", CHAT_FILE = "chat.txt", CONFIG_FILE = "config.properties";

    private static DefaultTableModel studentModel, testModel, courseModel, answerModel, secretTaskModel, classModel;
    private static JFrame frame;
    private static JTabbedPane tabbedPane;
    private static JTable studentTable, testTable, courseTable, answerTable, secretTaskTable, classTable;

    private static JTextArea chatArea;
    private static DefaultListModel chatRoomListModel;
    private static JList chatRoomList;
    private static final List chatRoomIds = new ArrayList();
    private static final Set unreadRoomIds = new HashSet();
    private static String activeChatRoomId = "ALL";
    private static JLabel activeChatHeader;
    private static javax.swing.Timer chatRefreshTimer;

    private static String currentLogin = "", userName = "Guest", currentRole = "Ученик";
    private static String currentThemeMode = "WIN11_LIGHT";
    private static Color accentColor = new Color(0, 103, 192);

    private static String currentLang = "RU";
    private static boolean langConfigured = false;

    private static int knownChatLines = 0, unreadCount = 0;
    private static boolean soundEnabled = true, overlayEnabled = false;
    private static TrayIcon trayIcon;
    private static JWindow overlayWindow;
    private static JLabel overlayLabel;

    private static final List secretTasksList = new ArrayList();
    private static final List classesList = new ArrayList();

    private static String tr(String ru, String en) {
        return "EN".equals(currentLang) ? en : ru;
    }

    private static class SecretTask {
        String id, sender, recipient, title, password, taskText, studentReply;
        SecretTask(List a) {
            id = String.valueOf(a.get(0)); sender = String.valueOf(a.get(1)); recipient = String.valueOf(a.get(2));
            title = String.valueOf(a.get(3)); password = String.valueOf(a.get(4)); taskText = String.valueOf(a.get(5)); studentReply = String.valueOf(a.get(6));
        }
    }

    private static class SchoolClass {
        String id, name, ownerLogin, ownerName, members;
        SchoolClass(String id, String name, String ownerLogin, String ownerName, String members) {
            this.id = id; this.name = name; this.ownerLogin = ownerLogin; this.ownerName = ownerName; this.members = members;
        }
        boolean hasAccess(String login) {
            if (ownerLogin.equalsIgnoreCase(login)) return true;
            List mList = Arrays.asList(members.split(","));
            for (int i = 0; i < mList.size(); i++) {
                if (String.valueOf(mList.get(i)).trim().equalsIgnoreCase(login)) return true;
            }
            return false;
        }
    }

    public static void main(String... args) {
        loadConfig();
        SwingUtilities.invokeLater(() -> {
            applyLookAndFeel(currentThemeMode);
            if (!langConfigured) {
                if (!showLanguageDialog()) System.exit(0);
            }
            if (!showAuthDialog()) System.exit(0);
            initSystemTray();
            initMainWindow();
        });
    }

    private static boolean showLanguageDialog() {
        JDialog dlg = new JDialog((Frame) null, "Select Language / Выбор языка — 1.4.0", true);
        dlg.setSize(380, 190);
        dlg.setLayout(new BorderLayout(10, 10));

        JLabel lbl = new JLabel("Выберите язык интерфейса / Choose interface language:", SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setBorder(BorderFactory.createEmptyBorder(15, 10, 5, 10));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 15));
        JButton btnRu = new JButton("Русский (RU)");
        JButton btnEn = new JButton("English (EN)");
        btnRu.setPreferredSize(new Dimension(135, 38));
        btnEn.setPreferredSize(new Dimension(135, 38));

        AtomicBoolean chosen = new AtomicBoolean(false);
        btnRu.addActionListener(e -> {
            currentLang = "RU";
            langConfigured = true;
            saveConfig();
            chosen.set(true);
            dlg.dispose();
        });
        btnEn.addActionListener(e -> {
            currentLang = "EN";
            langConfigured = true;
            saveConfig();
            chosen.set(true);
            dlg.dispose();
        });

        btnPanel.add(btnRu);
        btnPanel.add(btnEn);

        dlg.add(lbl, BorderLayout.NORTH);
        dlg.add(btnPanel, BorderLayout.CENTER);
        applyCustomThemeColors(dlg);
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
        return chosen.get();
    }

    private static String nowTime(String p) { return new SimpleDateFormat(p).format(new Date()); }
    private static Vector vec(String... items) { return new Vector(Arrays.asList(items)); }
    private static List splitSemi(String line) { return Arrays.asList(line.split(";", -1)); }
    private static String sanitize(String s) { return s == null ? "" : s.trim().replace(";", ",").replace("\r", "").replace("\n", "\\n"); }
    private static String unsanitize(String s) { return s == null ? "" : s.replace("\\n", "\n"); }
    private static void copyText(String s) { if (s != null) Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null); }

    private static DefaultTableModel createReadOnlyModel(String... cols) {
        return new DefaultTableModel(vec(cols), 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    }

    private static void initSystemTray() {
        if (trayIcon != null || !SystemTray.isSupported()) return;
        try {
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(accentColor); g.fillRoundRect(0, 0, 16, 16, 5, 5);
            g.setColor(Color.WHITE); g.setFont(new Font("Segoe UI", Font.BOLD, 11)); g.drawString(tr("С", "S"), 4, 12); g.dispose();

            PopupMenu popup = new PopupMenu();
            MenuItem openItem = new MenuItem(tr("Открыть Супер уроки 1.4.0", "Open Super Lessons 1.4.0"));
            MenuItem chatItem = new MenuItem(tr("Открыть чаты и ЛС", "Open Chats & DMs"));
            MenuItem exitItem = new MenuItem(tr("Выход", "Exit"));
            openItem.addActionListener(e -> restoreMainWindow(false));
            chatItem.addActionListener(e -> restoreMainWindow(true));
            exitItem.addActionListener(e -> System.exit(0));
            popup.add(openItem); popup.add(chatItem); popup.addSeparator(); popup.add(exitItem);

            trayIcon = new TrayIcon(img, tr("Супер уроки 1.4.0", "Super Lessons 1.4.0"), popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> restoreMainWindow(false));
            SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void restoreMainWindow(boolean toChat) {
        if (frame != null) {
            frame.setVisible(true); frame.setExtendedState(JFrame.NORMAL); frame.toFront();
            if (toChat && tabbedPane != null) { tabbedPane.setSelectedIndex(3); resetUnreadCount(); }
        }
    }

    private static void toggleOverlay(boolean enable) {
        overlayEnabled = enable;
        if (enable) {
            if (overlayWindow == null) {
                overlayWindow = new JWindow(); overlayWindow.setAlwaysOnTop(true);
                JPanel p = new JPanel(new BorderLayout(8, 0));
                p.setBackground(new Color(28, 28, 30));
                p.setBorder(BorderFactory.createCompoundBorder(new LineBorder(accentColor, 2), BorderFactory.createEmptyBorder(6, 12, 6, 12)));
                overlayLabel = new JLabel(); overlayLabel.setForeground(Color.WHITE); overlayLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                p.add(overlayLabel, BorderLayout.CENTER);
                p.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { restoreMainWindow(true); } });
                overlayWindow.add(p); overlayWindow.setSize(270, 36);
                Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
                overlayWindow.setLocation(scr.width - 290, scr.height - 90);
            }
            updateUnreadIndicators(); overlayWindow.setVisible(true);
        } else if (overlayWindow != null) overlayWindow.setVisible(false);
    }

    private static void updateUnreadIndicators() {
        String baseChat = tr("Чаты и ЛС", "Chats & DMs");
        if (tabbedPane != null && tabbedPane.getTabCount() > 3)
            tabbedPane.setTitleAt(3, unreadCount > 0 ? baseChat + " (" + unreadCount + ")" : baseChat);
        if (trayIcon != null)
            trayIcon.setToolTip(tr("Супер уроки 1.4.0 — ", "Super Lessons 1.4.0 — ") + (unreadCount > 0 ? tr("Новых: ", "New: ") + unreadCount : tr("Нет новых", "No new messages")));
        if (overlayLabel != null)
overlayLabel.setText(tr("Супер уроки 1.4.0 | ", "Super Lessons 1.4.0 | ") + (unreadCount > 0 ? tr("Новых: ", "New: ") + unreadCount : tr("Нет новых", "No new")));
}
private static void resetUnreadCount() { if (unreadCount != 0) { unreadCount = 0; updateUnreadIndicators(); } }
private static boolean showAuthDialog() {
JDialog dlg = new JDialog((Frame) null, tr("Супер уроки 1.4.0 — Авторизация", "Super Lessons 1.4.0 — Authorization"), true);
dlg.setSize(440, 340); dlg.setLayout(new BorderLayout(10, 10));
JTabbedPane tabs = new JTabbedPane();
JPanel loginP = new JPanel(new GridLayout(4, 2, 8, 12)); loginP.setBorder(BorderFactory.createEmptyBorder(20, 18, 15, 18));
JTextField txtL = new JTextField(); JPasswordField txtP = new JPasswordField();
JButton btnL = new JButton(tr("Войти", "Login")), btnExit = new JButton(tr("Выход", "Exit"));
loginP.add(new JLabel(tr("Ваш логин:", "Your login:"))); loginP.add(txtL);
loginP.add(new JLabel(tr("Ваш пароль:", "Your password:"))); loginP.add(txtP);
loginP.add(new JLabel("")); loginP.add(new JLabel(tr("Нет аккаунта? Откройте «Регистрация».", "No account? Open «Register» tab.")));
loginP.add(btnExit); loginP.add(btnL);
JPanel regP = new JPanel(new GridLayout(5, 2, 8, 10)); regP.setBorder(BorderFactory.createEmptyBorder(15, 18, 15, 18));
JTextField rL = new JTextField(), rName = new JTextField(); JPasswordField rP = new JPasswordField();
JComboBox rRole = new JComboBox(vec(tr("Ученик", "Student"), tr("Учитель", "Teacher")));
JButton btnR = new JButton(tr("Зарегистрироваться", "Register"));
regP.add(new JLabel(tr("Придумайте логин:", "Create login:"))); regP.add(rL);
regP.add(new JLabel(tr("Придумайте пароль:", "Create password:"))); regP.add(rP);
regP.add(new JLabel(tr("Ваше ФИО:", "Full Name:"))); regP.add(rName);
regP.add(new JLabel(tr("Выберите роль:", "Select role:"))); regP.add(rRole);
regP.add(new JLabel("")); regP.add(btnR);
AtomicBoolean auth = new AtomicBoolean(false);
btnL.addActionListener(e -> {
if (checkCredentials(sanitize(txtL.getText()), new String(txtP.getPassword()).trim())) { auth.set(true); dlg.dispose(); }
else JOptionPane.showMessageDialog(dlg, tr("Неверный логин или пароль!", "Invalid login or password!"), tr("Ошибка", "Error"), JOptionPane.ERROR_MESSAGE);
});
btnR.addActionListener(e -> {
String l = sanitize(rL.getText()), p = sanitize(new String(rP.getPassword())), n = sanitize(rName.getText());
String role = (rRole.getSelectedIndex() == 1) ? "Учитель" : "Ученик";
if (l.isEmpty() || p.isEmpty() || n.isEmpty()) { JOptionPane.showMessageDialog(dlg, tr("Заполните все поля!", "Please fill in all fields!")); return; }
if (accountExists(l)) { JOptionPane.showMessageDialog(dlg, tr("Такой логин уже занят!", "This login is already taken!")); return; }
appendLine(ACCOUNTS_FILE, l + ";" + p + ";" + n + ";" + role);
currentLogin = l; userName = n; currentRole = role; auth.set(true); dlg.dispose();
});
btnExit.addActionListener(e -> dlg.dispose());
if (!new File(ACCOUNTS_FILE).exists()) { tabs.addTab(tr("Регистрация", "Register"), regP); tabs.addTab(tr("Вход", "Login"), loginP); }
else { tabs.addTab(tr("Вход", "Login"), loginP); tabs.addTab(tr("Регистрация", "Register"), regP); }
dlg.add(tabs); applyCustomThemeColors(dlg); dlg.setLocationRelativeTo(null); dlg.setVisible(true);
return auth.get();
}
private static boolean checkCredentials(String l, String p) {
List lines = readLines(ACCOUNTS_FILE);
for (int i = 0; i < lines.size(); i++) {
List a = splitSemi(String.valueOf(lines.get(i)));
if (a.size() >= 4 && String.valueOf(a.get(0)).equals(l) && String.valueOf(a.get(1)).equals(p)) {
currentLogin = String.valueOf(a.get(0)); userName = String.valueOf(a.get(2)); currentRole = String.valueOf(a.get(3)); return true;
}
}
return false;
}
private static boolean accountExists(String l) {
List lines = readLines(ACCOUNTS_FILE);
for (int i = 0; i < lines.size(); i++) {
List a = splitSemi(String.valueOf(lines.get(i)));
if (!a.isEmpty() && String.valueOf(a.get(0)).equalsIgnoreCase(l)) return true;
}
return false;
}
private static List getAllAccounts() {
List list = new ArrayList();
List lines = readLines(ACCOUNTS_FILE);
for (int i = 0; i < lines.size(); i++) {
List a = splitSemi(String.valueOf(lines.get(i)));
if (a.size() >= 4) list.add(Arrays.asList(String.valueOf(a.get(0)), String.valueOf(a.get(2)), String.valueOf(a.get(3))));
}
return list;
}
private static String makeDmRoomId(String l1, String l2) {
String a = l1.toLowerCase(), b = l2.toLowerCase();
return a.compareTo(b) <= 0 ? "DM:" + a + ":" + b : "DM:" + b + ":" + a;
}
private static void openDirectChatWith(String targetLogin) {
String clean = targetLogin.replace("@", "").trim();
if (clean.isEmpty() || clean.equalsIgnoreCase(currentLogin)) return;
if (!accountExists(clean)) { JOptionPane.showMessageDialog(frame, tr("Пользователь @", "User @") + clean + tr(" не найден.", " not found.")); return; }
activeChatRoomId = makeDmRoomId(currentLogin, clean);
unreadRoomIds.remove(activeChatRoomId);
refreshChatRoomsList(); loadChatHistory(false);
tabbedPane.setSelectedIndex(3); resetUnreadCount();
}
private static String displayRole(String role) {
if ("Учитель".equals(role)) return tr("Учитель", "Teacher");
return tr("Ученик", "Student");
}
private static void initMainWindow() {
final boolean isTeacher = "Учитель".equals(currentRole);
frame = new JFrame(tr("Супер уроки 1.4.0 — [", "Super Lessons 1.4.0 — [") + displayRole(currentRole) + "] " + userName + " (@" + currentLogin + ")");
frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
frame.setSize(1040, 660);
frame.addWindowListener(new WindowAdapter() {
@Override public void windowClosing(WindowEvent e) {
if (trayIcon != null) trayIcon.displayMessage(tr("Супер уроки 1.4.0", "Super Lessons 1.4.0"), tr("Свёрнуто в трей.", "Minimized to tray."), TrayIcon.MessageType.INFO);
else System.exit(0);
}
@Override public void windowActivated(WindowEvent e) { if (tabbedPane != null && tabbedPane.getSelectedIndex() == 3) resetUnreadCount(); }
});
setupMenuBar(isTeacher);
tabbedPane = new JTabbedPane();
// 0. Курсы
JPanel coursesP = new JPanel(new BorderLayout(8, 8)); coursesP.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
courseModel = createReadOnlyModel("ID", tr("Предмет / Курс", "Subject / Course"), tr("Уровень", "Level"), tr("Пример и задание", "Example & Task"));
courseTable = new JTable(courseModel); courseTable.setRowHeight(28);
courseTable.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && courseTable.getSelectedRow() != -1) openCourseExampleDialog(courseTable.getSelectedRow()); } });
attachTableContextMenu(courseTable, isTeacher, true, false);
JPanel cCtrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
JButton btnOpenEx = new JButton(tr("Открыть пример и решить", "Open Example & Solve"));
btnOpenEx.addActionListener(e -> { int r = courseTable.getSelectedRow(); if (r != -1) openCourseExampleDialog(r); });
cCtrl.add(btnOpenEx);
if (isTeacher) {
JButton bAdd = new JButton(tr("+ Создать курс", "+ Create Course")), bEd = new JButton(tr("Редактировать", "Edit")), bDel = new JButton(tr("Удалить", "Delete"));
bAdd.addActionListener(e -> openCourseEditorDialog(-1));
bEd.addActionListener(e -> { int r = courseTable.getSelectedRow(); if (r != -1) openCourseEditorDialog(r); });
bDel.addActionListener(e -> { int r = courseTable.getSelectedRow(); if (r != -1) { courseModel.removeRow(r); saveData(COURSES_FILE, courseModel); } });
cCtrl.add(bAdd); cCtrl.add(bEd); cCtrl.add(bDel);
}
coursesP.add(new JScrollPane(courseTable), BorderLayout.CENTER); coursesP.add(cCtrl, BorderLayout.SOUTH);
// 1. Классы
JPanel classesP = new JPanel(new BorderLayout(8, 8)); classesP.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
classModel = createReadOnlyModel(tr("ID Класса", "Class ID"), tr("Название класса", "Class Name"), tr("Создатель (Учитель)", "Creator (Teacher)"), tr("Участники (@логины)", "Members (@logins)"));
classTable = new JTable(classModel); classTable.setRowHeight(28);
attachClassContextMenu();
JPanel clCtrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
JButton btnOpenClassChat = new JButton(tr("Открыть чат класса", "Open Class Chat"));
btnOpenClassChat.addActionListener(e -> {
int r = classTable.getSelectedRow();
if (r != -1) {
activeChatRoomId = "CLASS:" + classModel.getValueAt(r, 0);
refreshChatRoomsList(); loadChatHistory(false); tabbedPane.setSelectedIndex(3);
}
});
clCtrl.add(btnOpenClassChat);
if (isTeacher) {
JButton btnNewCl = new JButton(tr("+ Создать класс", "+ Create Class"));
JButton btnAddUser = new JButton(tr("+ Добавить ученика/учителя", "+ Add Student/Teacher"));
JButton btnRemUser = new JButton(tr("Исключить участника", "Remove Member"));
JButton btnDelCl = new JButton(tr("Удалить класс", "Delete Class"));
btnNewCl.addActionListener(e -> createNewClassDialog());
btnAddUser.addActionListener(e -> manageClassMember(true));
btnRemUser.addActionListener(e -> manageClassMember(false));
btnDelCl.addActionListener(e -> {
int r = classTable.getSelectedRow();
if (r != -1) {
String id = String.valueOf(classModel.getValueAt(r, 0));
classesList.removeIf(c -> ((SchoolClass) c).id.equals(id));
saveClasses(); loadClasses(); refreshChatRoomsList();
}
});
clCtrl.add(btnNewCl); clCtrl.add(btnAddUser); clCtrl.add(btnRemUser); clCtrl.add(btnDelCl);
}
classesP.add(new JScrollPane(classTable), BorderLayout.CENTER); classesP.add(clCtrl, BorderLayout.SOUTH);
// 2. Задания по паролю
JPanel secretP = new JPanel(new BorderLayout(8, 8)); secretP.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
secretTaskModel = createReadOnlyModel("ID", tr("Отправитель", "Sender"), tr("Кому", "Recipient"), tr("Тема", "Topic"), tr("Статус", "Status"), tr("Ответ ученика", "Student Reply"));
secretTaskTable = new JTable(secretTaskModel); secretTaskTable.setRowHeight(28);
attachTableContextMenu(secretTaskTable, isTeacher, false, true);
JPanel sCtrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
JButton bUnlock = new JButton(tr("Открыть по паролю / Ответить", "Unlock with Password / Reply"));
bUnlock.addActionListener(e -> { int r = secretTaskTable.getSelectedRow(); if (r != -1) openUnlockSecretTaskDialog(r); });
sCtrl.add(bUnlock);
if (isTeacher) {
JButton bSendS = new JButton(tr("+ Отправить задание по паролю", "+ Send Password Task")), bDelS = new JButton(tr("Удалить", "Delete"));
bSendS.addActionListener(e -> openCreateSecretTaskDialog());
bDelS.addActionListener(e -> { int r = secretTaskTable.getSelectedRow(); if (r != -1) { secretTasksList.remove(r); secretTaskModel.removeRow(r); saveSecretTasks(); } });
sCtrl.add(bSendS); sCtrl.add(bDelS);
}
secretP.add(new JScrollPane(secretTaskTable), BorderLayout.CENTER); secretP.add(sCtrl, BorderLayout.SOUTH);
// 3. Чаты
JPanel chatP = createChatPanel(isTeacher);
// 4. Ответы
JPanel ansP = new JPanel(new BorderLayout(8, 8)); ansP.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
answerModel = createReadOnlyModel(tr("Дата и время", "Date & Time"), tr("Ученик", "Student"), tr("Курс", "Course"), tr("Сохранённый ответ", "Saved Answer"));
answerTable = new JTable(answerModel); answerTable.setRowHeight(26);
attachTableContextMenu(answerTable, isTeacher, false, false);
JPanel aCtrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
JButton bViewA = new JButton(tr("Посмотреть ответ полностью", "View Full Answer"));
bViewA.addActionListener(e -> {
int r = answerTable.getSelectedRow();
if (r != -1) JOptionPane.showMessageDialog(frame, tr("Ученик: ", "Student: ") + answerModel.getValueAt(r, 1) + "\n" + tr("Курс: ", "Course: ") + answerModel.getValueAt(r, 2) + "\n\n" + unsanitize(String.valueOf(answerModel.getValueAt(r, 3))));
});
aCtrl.add(bViewA);
if (isTeacher) {
JButton bDelA = new JButton(tr("Удалить", "Delete"));
bDelA.addActionListener(e -> { int r = answerTable.getSelectedRow(); if (r != -1) { answerModel.removeRow(r); saveData(ANSWERS_FILE, answerModel); } });
aCtrl.add(bDelA);
}
ansP.add(new JScrollPane(answerTable), BorderLayout.CENTER); ansP.add(aCtrl, BorderLayout.SOUTH);
loadData(COURSES_FILE, courseModel);
if (courseModel.getRowCount() == 0) populateDefaultCourses();
loadClasses(); loadSecretTasks(); refreshChatRoomsList(); loadChatHistory(false); loadAnswersFiltered(isTeacher);
tabbedPane.addTab(tr("Курсы и Примеры", "Courses & Examples"), coursesP);
tabbedPane.addTab(tr("Мои классы", "My Classes"), classesP);
tabbedPane.addTab(tr("Задания по паролю", "Password Tasks"), secretP);
tabbedPane.addTab(tr("Чаты и ЛС", "Chats & DMs"), chatP);
tabbedPane.addTab(isTeacher ? tr("Ответы учеников", "Student Answers") : tr("Мои ответы", "My Answers"), ansP);
if (isTeacher) {
tabbedPane.addTab(tr("Студенты", "Students"), createStudentsPanel());
tabbedPane.addTab(tr("Тесты", "Tests"), createTestsPanel());
loadData(STUDENTS_FILE, studentModel); loadData(TESTS_FILE, testModel);
}
tabbedPane.addChangeListener(e -> { if (tabbedPane.getSelectedIndex() == 3) resetUnreadCount(); });
chatRefreshTimer = new javax.swing.Timer(2000, e -> {
loadClasses(); refreshChatRoomsList(); loadChatHistory(true);
});
chatRefreshTimer.start();
frame.add(tabbedPane); applyCustomThemeColors(frame); toggleOverlay(overlayEnabled);
frame.setLocationRelativeTo(null); frame.setVisible(true);
}
private static JPanel createChatPanel(boolean isTeacher) {
JPanel main = new JPanel(new BorderLayout(8, 8)); main.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
chatRoomListModel = new DefaultListModel();
chatRoomList = new JList(chatRoomListModel);
chatRoomList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
chatRoomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
chatRoomList.addListSelectionListener(e -> {
if (!e.getValueIsAdjusting() && chatRoomList.getSelectedIndex() != -1) {
int idx = chatRoomList.getSelectedIndex();
if (idx < chatRoomIds.size()) {
activeChatRoomId = String.valueOf(chatRoomIds.get(idx));
unreadRoomIds.remove(activeChatRoomId);
refreshChatRoomsList();
loadChatHistory(false);
}
}
});
JScrollPane leftScroll = new JScrollPane(chatRoomList);
leftScroll.setPreferredSize(new Dimension(250, 0));
leftScroll.setBorder(BorderFactory.createTitledBorder(tr("Чаты, Классы и ЛС", "Chats, Classes & DMs")));
JPanel rightP = new JPanel(new BorderLayout(6, 6));
activeChatHeader = new JLabel("  " + tr("Общий чат школы", "General School Chat"));
activeChatHeader.setFont(new Font("Segoe UI", Font.BOLD, 14));
activeChatHeader.setOpaque(true); activeChatHeader.setBackground(accentColor); activeChatHeader.setForeground(Color.WHITE);
activeChatHeader.setPreferredSize(new Dimension(0, 34));
chatArea = new JTextArea(); chatArea.setEditable(false); chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
chatArea.setLineWrap(true); chatArea.setWrapStyleWord(true); chatArea.setMargin(new Insets(10, 10, 10, 10));
JPopupMenu chatPopup = new JPopupMenu();
JMenuItem cpSel = new JMenuItem(tr("Копировать выделенное", "Copy Selected"));
JMenuItem cpAll = new JMenuItem(tr("Копировать всю переписку", "Copy All Messages"));
cpSel.addActionListener(e -> copyText(chatArea.getSelectedText()));
cpAll.addActionListener(e -> copyText(chatArea.getText()));
chatPopup.add(cpSel); chatPopup.add(cpAll);
if (isTeacher) {
chatPopup.addSeparator();
JMenuItem clr = new JMenuItem(tr("Очистить всю историю чатов", "Clear All Chat History"));
clr.addActionListener(e -> { new File(CHAT_FILE).delete(); knownChatLines = 0; loadChatHistory(false); });
chatPopup.add(clr);
}
chatArea.setComponentPopupMenu(chatPopup);
JPanel inputP = new JPanel(new BorderLayout(8, 0));
JTextField txtMsg = new JTextField();
JButton btnSend = new JButton(tr("Отправить", "Send"));
ActionListener sendAct = e -> {
String msg = txtMsg.getText().trim();
if (!msg.isEmpty()) {
appendLine(CHAT_FILE, activeChatRoomId + ";" + nowTime("HH:mm") + ";" + currentRole + ";" + userName + " (@" + currentLogin + ");" + sanitize(msg));
txtMsg.setText(""); loadChatHistory(false);
}
};
btnSend.addActionListener(sendAct); txtMsg.addActionListener(sendAct);
inputP.add(txtMsg, BorderLayout.CENTER); inputP.add(btnSend, BorderLayout.EAST);
rightP.add(activeChatHeader, BorderLayout.NORTH);
rightP.add(new JScrollPane(chatArea), BorderLayout.CENTER);
rightP.add(inputP, BorderLayout.SOUTH);
JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightP);
split.setDividerLocation(255);
main.add(split, BorderLayout.CENTER);
return main;
}
private static void refreshChatRoomsList() {
if (chatRoomListModel == null) return;
List newIds = new ArrayList();
List newLabels = new ArrayList();
newIds.add("ALL");
newLabels.add((unreadRoomIds.contains("ALL") ? "● " : "") + tr("Общий чат", "General Chat"));
for (int i = 0; i < classesList.size(); i++) {
SchoolClass sc = (SchoolClass) classesList.get(i);
if (sc.hasAccess(currentLogin)) {
String rid = "CLASS:" + sc.id;
newIds.add(rid);
newLabels.add((unreadRoomIds.contains(rid) ? "● " : "") + tr("Класс: ", "Class: ") + sc.name);
}
}
List accs = getAllAccounts();
for (int i = 0; i < accs.size(); i++) {
List acc = (List) accs.get(i);
String l = String.valueOf(acc.get(0));
if (!l.equalsIgnoreCase(currentLogin)) {
String rid = makeDmRoomId(currentLogin, l);
newIds.add(rid);
newLabels.add((unreadRoomIds.contains(rid) ? "● " : "") + tr("ЛС: ", "DM: ") + acc.get(1) + " (@" + l + ", " + displayRole(String.valueOf(acc.get(2))) + ")");
}
}
chatRoomIds.clear(); chatRoomIds.addAll(newIds);
int selIdx = chatRoomIds.indexOf(activeChatRoomId);
if (selIdx == -1) { activeChatRoomId = "ALL"; selIdx = 0; }
boolean changed = chatRoomListModel.size() != newLabels.size();
if (!changed) {
for (int i = 0; i < newLabels.size(); i++) {
if (!String.valueOf(chatRoomListModel.get(i)).equals(String.valueOf(newLabels.get(i)))) { changed = true; break; }
}
}
if (changed) {
chatRoomListModel.clear();
for (int i = 0; i < newLabels.size(); i++) chatRoomListModel.addElement(newLabels.get(i));
chatRoomList.setSelectedIndex(selIdx);
}
if (activeChatHeader != null && selIdx < newLabels.size()) {
activeChatHeader.setText("  " + String.valueOf(newLabels.get(selIdx)).replace("● ", ""));
activeChatHeader.setBackground(accentColor);
}
}
private static void loadChatHistory(boolean notify) {
if (chatArea == null) return;
List lines = readLines(CHAT_FILE);
StringBuilder sb = new StringBuilder();
String lastIncoming = "", lastRoom = "";
for (int i = 0; i < lines.size(); i++) {
String line = String.valueOf(lines.get(i));
List p = Arrays.asList(line.split(";", 5));
if (p.size() == 5) {
lastRoom = String.valueOf(p.get(0));
lastIncoming = "[" + p.get(1) + "] [" + displayRole(String.valueOf(p.get(2))) + "] " + p.get(3) + ": " + unsanitize(String.valueOf(p.get(4)));
if (lastRoom.equals(activeChatRoomId)) sb.append(lastIncoming).append("\n");
} else if ("ALL".equals(activeChatRoomId)) {
sb.append(line).append("\n");
}
}
if (sb.length() == 0) sb.append(tr("Сообщений в этом чате пока нет. Напишите первым!\n", "No messages in this chat yet. Be the first to write!\n"));
if (notify && lines.size() > knownChatLines && knownChatLines > 0) {
boolean isMine = lastIncoming.contains("(@" + currentLogin + "):");
boolean canSee = lastRoom.equals("ALL") || lastRoom.contains(":" + currentLogin.toLowerCase()) || isUserInClassRoom(lastRoom);
if (!isMine && canSee) {
if (soundEnabled) Toolkit.getDefaultToolkit().beep();
boolean visible = frame != null && frame.isVisible() && frame.isActive() && tabbedPane.getSelectedIndex() == 3 && activeChatRoomId.equals(lastRoom);
if (!visible) {
unreadCount += (lines.size() - knownChatLines);
unreadRoomIds.add(lastRoom);
refreshChatRoomsList();
updateUnreadIndicators();
if (trayIcon != null) trayIcon.displayMessage(tr("Новое сообщение", "New Message"), lastIncoming, TrayIcon.MessageType.INFO);
}
}
}
knownChatLines = lines.size();
String txt = sb.toString();
if (!chatArea.getText().equals(txt)) { chatArea.setText(txt); chatArea.setCaretPosition(chatArea.getDocument().getLength()); }
}
private static boolean isUserInClassRoom(String roomId) {
if (!roomId.startsWith("CLASS:")) return false;
String cid = roomId.substring(6);
for (int i = 0; i < classesList.size(); i++) {
SchoolClass sc = (SchoolClass) classesList.get(i);
if (sc.id.equals(cid) && sc.hasAccess(currentLogin)) return true;
}
return false;
}
private static void createNewClassDialog() {
String name = JOptionPane.showInputDialog(frame, tr("Введите название нового класса (например, 9-А или Java-группа):", "Enter new class name (e.g. Grade 9A or Java Group):"));
if (name == null || sanitize(name).isEmpty()) return;
String id = tr("КЛ-", "CL-") + (100 + new Random().nextInt(900));
classesList.add(new SchoolClass(id, sanitize(name), currentLogin, userName, ""));
saveClasses(); loadClasses(); refreshChatRoomsList();
}
private static void manageClassMember(boolean add) {
int r = classTable.getSelectedRow();
if (r == -1) { JOptionPane.showMessageDialog(frame, tr("Сначала выберите класс в таблице!", "Please select a class in the table first!")); return; }
String id = String.valueOf(classModel.getValueAt(r, 0));
SchoolClass target = null;
for (int i = 0; i < classesList.size(); i++) {
SchoolClass sc = (SchoolClass) classesList.get(i);
if (sc.id.equals(id)) target = sc;
}
if (target == null) return;
if (!target.ownerLogin.equalsIgnoreCase(currentLogin)) {
JOptionPane.showMessageDialog(frame, tr("Управлять участниками может только создатель класса!", "Only the class creator can manage members!")); return;
}
List accs = getAllAccounts();
List choices = new ArrayList();
for (int i = 0; i < accs.size(); i++) {
List a = (List) accs.get(i);
if (!String.valueOf(a.get(0)).equalsIgnoreCase(currentLogin)) {
choices.add(a.get(0) + " — " + a.get(1) + " (" + displayRole(String.valueOf(a.get(2))) + ")");
}
}
if (choices.isEmpty()) { JOptionPane.showMessageDialog(frame, tr("Нет других зарегистрированных пользователей.", "No other registered users found.")); return; }
Object sel = JOptionPane.showInputDialog(frame, add ? tr("Выберите ученика или учителя для добавления:", "Select student or teacher to add:") : tr("Выберите кого исключить:", "Select member to remove:"),
tr("Управление классом ", "Manage Class ") + target.name, JOptionPane.PLAIN_MESSAGE, null, choices.toArray(), choices.get(0));
if (sel == null) return;
String login = String.valueOf(Arrays.asList(String.valueOf(sel).split(" — ")).get(0)).trim();
Set set = new LinkedHashSet();
List oldMembers = Arrays.asList(target.members.split(","));
for (int i = 0; i < oldMembers.size(); i++) {
String m = String.valueOf(oldMembers.get(i)).trim();
if (!m.isEmpty()) set.add(m);
}
if (add) set.add(login); else set.remove(login);
target.members = String.join(",", set);
saveClasses(); loadClasses(); refreshChatRoomsList();
}
private static void saveClasses() {
try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(CLASSES_FILE), StandardCharsets.UTF_8))) {
for (int i = 0; i < classesList.size(); i++) {
SchoolClass sc = (SchoolClass) classesList.get(i);
bw.write(sc.id + ";" + sc.name + ";" + sc.ownerLogin + ";" + sc.ownerName + ";" + sc.members);
bw.newLine();
}
} catch (IOException e) { e.printStackTrace(); }
}
private static void loadClasses() {
classesList.clear();
List lines = readLines(CLASSES_FILE);
for (int i = 0; i < lines.size(); i++) {
List p = splitSemi(String.valueOf(lines.get(i)));
if (p.size() >= 5) classesList.add(new SchoolClass(String.valueOf(p.get(0)), String.valueOf(p.get(1)), String.valueOf(p.get(2)), String.valueOf(p.get(3)), String.valueOf(p.get(4))));
}
if (classModel != null) {
int sel = classTable.getSelectedRow();
classModel.setRowCount(0);
for (int i = 0; i < classesList.size(); i++) {
SchoolClass sc = (SchoolClass) classesList.get(i);
if (sc.hasAccess(currentLogin)) {
classModel.addRow(vec(sc.id, sc.name, sc.ownerName + " (@" + sc.ownerLogin + ")", sc.members.isEmpty() ? tr("Пока никого", "No members yet") : sc.members));
}
}
if (sel >= 0 && sel < classModel.getRowCount()) classTable.setRowSelectionInterval(sel, sel);
}
}
private static void attachClassContextMenu() {
JPopupMenu pm = new JPopupMenu();
JMenuItem miChat = new JMenuItem(tr("Перейти в чат этого класса", "Open This Class Chat"));
JMenuItem miCopyId = new JMenuItem(tr("Копировать ID класса", "Copy Class ID"));
miChat.addActionListener(e -> {
int r = classTable.getSelectedRow();
if (r != -1) { activeChatRoomId = "CLASS:" + classModel.getValueAt(r, 0); refreshChatRoomsList(); loadChatHistory(false); tabbedPane.setSelectedIndex(3); }
});
miCopyId.addActionListener(e -> { int r = classTable.getSelectedRow(); if (r != -1) copyText(String.valueOf(classModel.getValueAt(r, 0))); });
pm.add(miChat); pm.add(miCopyId);
classTable.setComponentPopupMenu(pm);
}
private static void attachTableContextMenu(JTable table, boolean isTeacher, boolean isCourse, boolean isSecret) {
JPopupMenu pm = new JPopupMenu();
JMenuItem miCopy = new JMenuItem(tr("Копировать значение ячейки", "Copy Cell Value"));
miCopy.addActionListener(e -> {
int r = table.getSelectedRow(), c = table.getSelectedColumn();
if (r != -1 && c != -1) copyText(String.valueOf(table.getValueAt(r, c)));
});
pm.add(miCopy);
if (isCourse) {
JMenuItem miOpen = new JMenuItem(tr("Открыть задание", "Open Task"));
miOpen.addActionListener(e -> { int r = table.getSelectedRow(); if (r != -1) openCourseExampleDialog(r); });
pm.add(miOpen);
if (isTeacher) {
JMenuItem miEdit = new JMenuItem(tr("Редактировать курс", "Edit Course"));
JMenuItem miDel = new JMenuItem(tr("Удалить курс", "Delete Course"));
miEdit.addActionListener(e -> { int r = table.getSelectedRow(); if (r != -1) openCourseEditorDialog(r); });
miDel.addActionListener(e -> { int r = table.getSelectedRow(); if (r != -1) { courseModel.removeRow(r); saveData(COURSES_FILE, courseModel); } });
pm.add(miEdit); pm.add(miDel);
}
}
if (isSecret) {
JMenuItem miUnl = new JMenuItem(tr("Ввести пароль и открыть", "Enter Password & Open"));
miUnl.addActionListener(e -> { int r = table.getSelectedRow(); if (r != -1) openUnlockSecretTaskDialog(r); });
pm.add(miUnl);
}
JMenuItem miDm = new JMenuItem(tr("Написать в ЛС пользователю...", "Send Direct Message..."));
miDm.addActionListener(e -> {
String login = JOptionPane.showInputDialog(frame, tr("Введите логин собеседника (без @):", "Enter recipient login (without @):"));
if (login != null) openDirectChatWith(login);
});
pm.addSeparator(); pm.add(miDm);
table.addMouseListener(new MouseAdapter() {
@Override public void mousePressed(MouseEvent e) {
int r = table.rowAtPoint(e.getPoint());
if (r >= 0 && r < table.getRowCount()) table.setRowSelectionInterval(r, r);
}
});
table.setComponentPopupMenu(pm);
}
private static void setupMenuBar(boolean isTeacher) {
JMenuBar mb = new JMenuBar();
JMenu mFile = new JMenu(tr("Файл", "File"));
JMenuItem miBuildExe = new JMenuItem(tr("Собрать .exe файл (Супер уроки 1.4.0)...", "Build .exe File (Super Lessons 1.4.0)..."));
miBuildExe.addActionListener(e -> buildExeFromSource());
JMenuItem miRef = new JMenuItem(tr("Обновить все данные", "Refresh All Data"));
miRef.addActionListener(e -> { courseModel.setRowCount(0); loadData(COURSES_FILE, courseModel); loadClasses(); loadSecretTasks(); loadChatHistory(false); loadAnswersFiltered(isTeacher); });
JMenuItem miExit = new JMenuItem(tr("Выйти полностью", "Exit Completely"));
miExit.addActionListener(e -> System.exit(0));
mFile.add(miBuildExe); mFile.add(miRef); mFile.addSeparator(); mFile.add(miExit);
JMenu mView = new JMenu(tr("Темы и Вид", "Themes & View"));
JCheckBoxMenuItem ovItem = new JCheckBoxMenuItem(tr("Мини-оверлей поверх окон", "Always-on-top Mini Overlay"), overlayEnabled);
ovItem.addActionListener(e -> { toggleOverlay(ovItem.isSelected()); saveConfig(); });
mView.add(ovItem); mView.addSeparator();
List themes = Arrays.asList(
Arrays.asList(tr("Windows 11 (Светлая)", "Windows 11 (Light)"), "WIN11_LIGHT"),
Arrays.asList(tr("Windows 11 (Тёмная)", "Windows 11 (Dark)"), "WIN11_DARK"),
Arrays.asList(tr("Киберпанк Неон", "Cyberpunk Neon"), "CYBERPUNK"),
Arrays.asList(tr("Изумрудный лес", "Emerald Forest"), "FOREST"),
Arrays.asList(tr("Глубокий океан", "Deep Ocean"), "OCEAN"),
Arrays.asList(tr("Закат", "Sunset"), "SUNSET"),
Arrays.asList(tr("Классика (Metal)", "Classic (Metal)"), "METAL"),
Arrays.asList(tr("Современный (Nimbus)", "Modern (Nimbus)"), "NIMBUS")
);
for (int i = 0; i < themes.size(); i++) {
List t = (List) themes.get(i);
JMenuItem ti = new JMenuItem(String.valueOf(t.get(0)));
final String code = String.valueOf(t.get(1));
ti.addActionListener(e -> switchTheme(code));
mView.add(ti);
}
mView.addSeparator();
JMenu mLang = new JMenu(tr("Язык / Language", "Language / Язык"));
JMenuItem miRu = new JMenuItem("Русский (RU)");
JMenuItem miEn = new JMenuItem("English (EN)");
miRu.addActionListener(e -> switchLanguage("RU"));
miEn.addActionListener(e -> switchLanguage("EN"));
mLang.add(miRu); mLang.add(miEn);
mView.add(mLang);
JMenu mAcc = new JMenu(tr("Аккаунт", "Account"));
JMenuItem swAcc = new JMenuItem(tr("Сменить пользователя (", "Switch User (") + userName + ")");
swAcc.addActionListener(e -> { if (chatRefreshTimer != null) chatRefreshTimer.stop(); frame.dispose(); if (showAuthDialog()) initMainWindow(); else System.exit(0); });
mAcc.add(swAcc);
mb.add(mFile); mb.add(mView); mb.add(mAcc);
frame.setJMenuBar(mb);
}
private static void switchLanguage(String lang) {
currentLang = lang;
langConfigured = true;
saveConfig();
if (chatRefreshTimer != null) chatRefreshTimer.stop();
if (frame != null) frame.dispose();
initMainWindow();
}
private static void buildExeFromSource() {
File src = new File("ManagementApp.java");
if (!src.exists()) src = new File("Desktop/super-uroki/ManagementApp.java");
if (!src.exists()) {
JFileChooser fc = new JFileChooser();
fc.setDialogTitle(tr("Укажите файл ManagementApp.java", "Select ManagementApp.java file"));
if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) src = fc.getSelectedFile();
else return;
}
final File foundSrc = src;
final File workDir = foundSrc.getParentFile() != null ? foundSrc.getParentFile() : new File(".");
JDialog prog = new JDialog(frame, tr("Сборка SuperUroki140.exe...", "Building SuperUroki140.exe..."), false);
JTextArea log = new JTextArea(12, 48); log.setEditable(false);
prog.add(new JScrollPane(log)); prog.pack(); prog.setLocationRelativeTo(frame); prog.setVisible(true);
new Thread(() -> {
try {
File bDir = new File(workDir, "build_temp"); bDir.mkdirs();
File dDir = new File(workDir, "dist_exe"); dDir.mkdirs();
SwingUtilities.invokeLater(() -> log.append("1. Compiling " + foundSrc.getAbsolutePath() + "...\n"));
if (runCmd(log, "javac", "-encoding", "UTF-8", "-d", bDir.getAbsolutePath(), foundSrc.getAbsolutePath()) != 0) return;
SwingUtilities.invokeLater(() -> log.append("2. Packaging SuperUroki140.jar...\n"));
File jarFile = new File(bDir, "SuperUroki140.jar");
if (runCmd(log, "jar", "cfe", jarFile.getAbsolutePath(), "ManagementApp", "-C", bDir.getAbsolutePath(), ".") != 0) return;
SwingUtilities.invokeLater(() -> log.append("3. Generating .exe via jpackage (wait 15-30 sec)...\n"));
int code = runCmd(log, "jpackage", "--type", "app-image", "--name", "SuperUroki140",
"--input", bDir.getAbsolutePath(), "--main-jar", "SuperUroki140.jar", "--main-class", "ManagementApp", "--dest", dDir.getAbsolutePath());
if (code == 0) {
SwingUtilities.invokeLater(() -> log.append("\nDONE! EXE created at:\n" + new File(dDir, "SuperUroki140/SuperUroki140.exe").getAbsolutePath()));
} else {
SwingUtilities.invokeLater(() -> log.append("\njpackage error. Ensure JDK 14+ is in Windows PATH."));
}
} catch (Exception ex) {
SwingUtilities.invokeLater(() -> log.append("\nError: " + ex.getMessage()));
}
}).start();
}
private static int runCmd(JTextArea log, String... cmd) throws Exception {
Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
String l; while ((l = br.readLine()) != null) { final String fl = l; SwingUtilities.invokeLater(() -> log.append(fl + "\n")); }
}
return p.waitFor();
}
private static JPanel createStudentsPanel() {
JPanel p = new JPanel(new BorderLayout(8, 8)); p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
studentModel = createReadOnlyModel(tr("ID Класса", "Class ID"), tr("ФИО Студента", "Student Name"), tr("Специализация", "Specialization"));
studentTable = new JTable(studentModel); studentTable.setRowHeight(26);
JPanel in = new JPanel(new FlowLayout(FlowLayout.LEFT));
JTextField t1 = new JTextField(6), t2 = new JTextField(14), t3 = new JTextField(10);
JButton bAdd = new JButton(tr("Добавить", "Add")), bDel = new JButton(tr("Удалить", "Delete"));
bAdd.addActionListener(e -> { if (!t2.getText().trim().isEmpty()) { studentModel.addRow(vec(sanitize(t1.getText()), sanitize(t2.getText()), sanitize(t3.getText()))); saveData(STUDENTS_FILE, studentModel); } });
bDel.addActionListener(e -> { int r = studentTable.getSelectedRow(); if (r != -1) { studentModel.removeRow(r); saveData(STUDENTS_FILE, studentModel); } });
in.add(new JLabel(tr("Класс:", "Class:"))); in.add(t1); in.add(new JLabel(tr("ФИО:", "Name:"))); in.add(t2); in.add(new JLabel(tr("Спец:", "Spec:"))); in.add(t3); in.add(bAdd); in.add(bDel);
p.add(new JScrollPane(studentTable), BorderLayout.CENTER); p.add(in, BorderLayout.SOUTH);
return p;
}
private static JPanel createTestsPanel() {
JPanel p = new JPanel(new BorderLayout(8, 8)); p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
testModel = createReadOnlyModel(tr("ID Теста", "Test ID"), tr("Название", "Title"), tr("Вопросов", "Questions"), tr("Мин", "Min"));
testTable = new JTable(testModel); testTable.setRowHeight(26);
JPanel in = new JPanel(new FlowLayout(FlowLayout.LEFT));
JTextField t1 = new JTextField(6), t2 = new JTextField(14), t3 = new JTextField(4), t4 = new JTextField(4);
JButton bAdd = new JButton(tr("Создать", "Create")), bDel = new JButton(tr("Удалить", "Delete"));
bAdd.addActionListener(e -> { if (!t2.getText().trim().isEmpty()) { testModel.addRow(vec(sanitize(t1.getText()), sanitize(t2.getText()), sanitize(t3.getText()), sanitize(t4.getText()))); saveData(TESTS_FILE, testModel); } });
bDel.addActionListener(e -> { int r = testTable.getSelectedRow(); if (r != -1) { testModel.removeRow(r); saveData(TESTS_FILE, testModel); } });
in.add(new JLabel("ID:")); in.add(t1); in.add(new JLabel(tr("Название:", "Title:"))); in.add(t2); in.add(new JLabel(tr("Вопр:", "Q:"))); in.add(t3); in.add(new JLabel(tr("Мин:", "Min:"))); in.add(t4); in.add(bAdd); in.add(bDel);
p.add(new JScrollPane(testTable), BorderLayout.CENTER); p.add(in, BorderLayout.SOUTH);
return p;
}
private static void loadAnswersFiltered(boolean isTeacher) {
answerModel.setRowCount(0);
List lines = readLines(ANSWERS_FILE);
for (int i = 0; i < lines.size(); i++) {
List d = splitSemi(String.valueOf(lines.get(i)));
if (d.size() >= 4 && (isTeacher || String.valueOf(d.get(1)).equals(userName))) answerModel.addRow(new Vector(d));
}
}
private static void openCreateSecretTaskDialog() {
JDialog d = new JDialog(frame, tr("Задание по паролю", "Password Protected Task"), true); d.setSize(480, 400); d.setLayout(new BorderLayout(8, 8));
JPanel f = new JPanel(new GridLayout(3, 2, 6, 6)); f.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
JTextField rec = new JTextField(), tit = new JTextField(), pass = new JTextField();
f.add(new JLabel(tr("Кому (ФИО):", "To (Full Name):"))); f.add(rec); f.add(new JLabel(tr("Тема:", "Topic:"))); f.add(tit); f.add(new JLabel(tr("Пароль:", "Password:"))); f.add(pass);
JTextArea body = new JTextArea(); body.setLineWrap(true);
JButton send = new JButton(tr("Отправить", "Send"));
send.addActionListener(e -> {
if (rec.getText().isEmpty() || tit.getText().isEmpty() || pass.getText().isEmpty()) return;
String id = tr("З-", "T-") + (secretTasksList.size() + 1);
List row = Arrays.asList(id, userName, sanitize(rec.getText()), sanitize(tit.getText()), sanitize(pass.getText()), sanitize(body.getText()), "Ожидает решения");
secretTasksList.add(new SecretTask(row));
secretTaskModel.addRow(vec(id, userName, sanitize(rec.getText()), sanitize(tit.getText()), tr("Защищено паролем", "Password Protected"), tr("Ожидает решения", "Pending")));
saveSecretTasks(); d.dispose();
});
d.add(f, BorderLayout.NORTH); d.add(new JScrollPane(body), BorderLayout.CENTER); d.add(send, BorderLayout.SOUTH);
applyCustomThemeColors(d); d.setLocationRelativeTo(frame); d.setVisible(true);
}
private static void openUnlockSecretTaskDialog(int row) {
SecretTask st = (SecretTask) secretTasksList.get(row);
JPasswordField pf = new JPasswordField();
if (JOptionPane.showConfirmDialog(frame, pf, tr("Пароль для «", "Password for «") + st.title + "»:", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
if (!new String(pf.getPassword()).trim().equals(st.password)) { JOptionPane.showMessageDialog(frame, tr("Неверный пароль!", "Wrong password!")); return; }
JDialog d = new JDialog(frame, st.title, true); d.setSize(520, 420); d.setLayout(new BorderLayout(8, 8));
JTextArea tv = new JTextArea(unsanitize(st.taskText)); tv.setEditable(false); tv.setLineWrap(true);
JTextArea rv = new JTextArea(st.studentReply.equals("Ожидает решения") ? "" : unsanitize(st.studentReply)); rv.setLineWrap(true);
JButton sv = new JButton(tr("Сохранить ответ", "Save Reply"));
sv.addActionListener(e -> { st.studentReply = sanitize(rv.getText()); secretTaskModel.setValueAt(tr("Решено (", "Solved (") + userName + ")", row, 5); saveSecretTasks(); d.dispose(); });
d.add(new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tv), new JScrollPane(rv)), BorderLayout.CENTER);
d.add(sv, BorderLayout.SOUTH); applyCustomThemeColors(d); d.setLocationRelativeTo(frame); d.setVisible(true);
}
private static void saveSecretTasks() {
try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(SECRET_TASKS_FILE), StandardCharsets.UTF_8))) {
for (int i = 0; i < secretTasksList.size(); i++) {
SecretTask st = (SecretTask) secretTasksList.get(i);
bw.write(st.id + ";" + st.sender + ";" + st.recipient + ";" + st.title + ";" + st.password + ";" + st.taskText + ";" + st.studentReply); bw.newLine();
}
} catch (IOException e) { e.printStackTrace(); }
}
private static void loadSecretTasks() {
secretTasksList.clear(); secretTaskModel.setRowCount(0);
List lines = readLines(SECRET_TASKS_FILE);
for (int i = 0; i < lines.size(); i++) {
List p = splitSemi(String.valueOf(lines.get(i)));
if (p.size() >= 7) {
SecretTask st = new SecretTask(p);
secretTasksList.add(st);
secretTaskModel.addRow(vec(st.
id, st.sender, st.recipient, st.title, tr("Защищено паролем", "Password Protected"), st.studentReply.equals("Ожидает решения") ? tr("Ожидает решения", "Pending") : tr("Ответ отправлен", "Answered")));
}
}
}
private static void openCourseExampleDialog(int row) {
String title = String.valueOf(courseModel.getValueAt(row, 1));
JDialog d = new JDialog(frame, title, true); d.setSize(560, 460); d.setLayout(new BorderLayout(8, 8));
JTextArea ta = new JTextArea(unsanitize(String.valueOf(courseModel.getValueAt(row, 3)))); ta.setEditable(false); ta.setLineWrap(true);
JTextArea ans = new JTextArea(); ans.setLineWrap(true);
JButton sub = new JButton(tr("Сохранить ответ", "Save Answer"));
sub.addActionListener(e -> {
if (ans.getText().trim().isEmpty()) return;
String ts = nowTime("dd.MM.yyyy HH:mm"), enc = sanitize(ans.getText());
answerModel.addRow(vec(ts, sanitize(userName), sanitize(title), enc));
appendLine(ANSWERS_FILE, ts + ";" + sanitize(userName) + ";" + sanitize(title) + ";" + enc);
d.dispose();
});
d.add(new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(ta), new JScrollPane(ans)), BorderLayout.CENTER);
d.add(sub, BorderLayout.SOUTH); applyCustomThemeColors(d); d.setLocationRelativeTo(frame); d.setVisible(true);
}
private static void openCourseEditorDialog(int editRow) {
boolean isEd = editRow >= 0;
JDialog d = new JDialog(frame, isEd ? tr("Редактировать курс", "Edit Course") : tr("Новый курс", "New Course"), true); d.setSize(500, 400); d.setLayout(new BorderLayout(8, 8));
JPanel f = new JPanel(new GridLayout(3, 2, 6, 6));
JTextField tId = new JTextField(isEd ? String.valueOf(courseModel.getValueAt(editRow, 0)) : tr("М-", "C-") + (courseModel.getRowCount() + 1));
JTextField tNm = new JTextField(isEd ? String.valueOf(courseModel.getValueAt(editRow, 1)) : "");
JComboBox cb = new JComboBox(vec(tr("Базовый", "Basic"), tr("Средний", "Intermediate"), tr("Продвинутый", "Advanced")));
f.add(new JLabel("ID:")); f.add(tId); f.add(new JLabel(tr("Название:", "Title:"))); f.add(tNm); f.add(new JLabel(tr("Уровень:", "Level:"))); f.add(cb);
JTextArea tsk = new JTextArea(isEd ? unsanitize(String.valueOf(courseModel.getValueAt(editRow, 3))) : ""); tsk.setLineWrap(true);
JButton sv = new JButton(tr("Сохранить", "Save"));
sv.addActionListener(e -> {
if (isEd) { courseModel.setValueAt(sanitize(tId.getText()), editRow, 0); courseModel.setValueAt(sanitize(tNm.getText()), editRow, 1); courseModel.setValueAt(cb.getSelectedItem(), editRow, 2); courseModel.setValueAt(sanitize(tsk.getText()), editRow, 3); }
else courseModel.addRow(vec(sanitize(tId.getText()), sanitize(tNm.getText()), String.valueOf(cb.getSelectedItem()), sanitize(tsk.getText())));
saveData(COURSES_FILE, courseModel); d.dispose();
});
d.add(f, BorderLayout.NORTH); d.add(new JScrollPane(tsk), BorderLayout.CENTER); d.add(sv, BorderLayout.SOUTH);
applyCustomThemeColors(d); d.setLocationRelativeTo(frame); d.setVisible(true);
}
private static void populateDefaultCourses() {
courseModel.addRow(vec("М-01", tr("Математика", "Mathematics"), tr("Базовый", "Basic"), tr("Решите уравнение: x² - 7x + 12 = 0.", "Solve the equation: x² - 7x + 12 = 0.")));
courseModel.addRow(vec("И-02", tr("Информатика (Java)", "Computer Science (Java)"), tr("Средний", "Intermediate"), tr("Напишите цикл вывода чётных чисел от 2 до 20.", "Write a loop to print even numbers from 2 to 20.")));
saveData(COURSES_FILE, courseModel);
}
private static void switchTheme(String mode) {
currentThemeMode = mode;
if ("CYBERPUNK".equals(mode)) accentColor = new Color(255, 0, 128);
else if ("FOREST".equals(mode)) accentColor = new Color(34, 139, 34);
else if ("OCEAN".equals(mode)) accentColor = new Color(0, 150, 199);
else if ("SUNSET".equals(mode)) accentColor = new Color(230, 81, 0);
else accentColor = new Color(0, 103, 192);
applyLookAndFeel(mode);
if (frame != null) { SwingUtilities.updateComponentTreeUI(frame); applyCustomThemeColors(frame); refreshChatRoomsList(); }
saveConfig();
}
private static void applyLookAndFeel(String mode) {
try {
if ("WIN11_LIGHT".equals(mode) || "FOREST".equals(mode) || "OCEAN".equals(mode) || "SUNSET".equals(mode))
UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
else if ("NIMBUS".equals(mode)) UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
else { MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme()); UIManager.setLookAndFeel(new MetalLookAndFeel()); }
} catch (Exception e) { e.printStackTrace(); }
}
private static void applyCustomThemeColors(Component root) {
Color bg = new Color(243, 243, 243), surf = Color.WHITE, fg = new Color(25, 25, 25), brd = new Color(205, 205, 205);
if ("WIN11_DARK".equals(currentThemeMode)) { bg = new Color(32, 32, 32); surf = new Color(45, 45, 45); fg = new Color(238, 238, 238); brd = new Color(75, 75, 75); }
else if ("CYBERPUNK".equals(currentThemeMode)) { bg = new Color(18, 12, 36); surf = new Color(32, 20, 60); fg = new Color(0, 255, 213); brd = new Color(255, 0, 128); }
else if ("FOREST".equals(currentThemeMode)) { bg = new Color(232, 245, 233); surf = new Color(248, 255, 248); fg = new Color(20, 60, 25); brd = new Color(129, 199, 132); }
else if ("OCEAN".equals(currentThemeMode)) { bg = new Color(224, 247, 250); surf = new Color(245, 253, 255); fg = new Color(0, 54, 77); brd = new Color(77, 208, 225); }
else if ("SUNSET".equals(currentThemeMode)) { bg = new Color(255, 243, 224); surf = new Color(255, 251, 245); fg = new Color(78, 30, 0); brd = new Color(255, 167, 38); }
paintRec(root, bg, surf, fg, brd);
}
private static void paintRec(Component c, Color bg, Color surf, Color fg, Color brd) {
if (c instanceof JPanel || c instanceof JTabbedPane || c instanceof JSplitPane) { c.setBackground(bg); c.setForeground(fg); }
else if (c instanceof JScrollPane) { ((JScrollPane) c).getViewport().setBackground(surf); ((JScrollPane) c).setBorder(new LineBorder(brd, 1)); }
else if (c instanceof JLabel && !c.isOpaque()) c.setForeground(fg);
else if (c instanceof JTextArea || c instanceof JTextField || c instanceof JPasswordField || c instanceof JList) { c.setBackground(surf); c.setForeground(fg); }
else if (c instanceof JTable) {
JTable t = (JTable) c; t.setBackground(surf); t.setForeground(fg); t.setGridColor(brd);
t.setSelectionBackground(accentColor); t.setSelectionForeground(Color.WHITE);
}
if (c instanceof Container) {
List children = Arrays.asList(((Container) c).getComponents());
for (int i = 0; i < children.size(); i++) paintRec((Component) children.get(i), bg, surf, fg, brd);
}
}
private static void saveConfig() {
Properties p = new Properties();
p.setProperty("themeMode", currentThemeMode); p.setProperty("accentColor", String.valueOf(accentColor.getRGB()));
p.setProperty("soundEnabled", String.valueOf(soundEnabled)); p.setProperty("overlayEnabled", String.valueOf(overlayEnabled));
p.setProperty("lang", currentLang); p.setProperty("langConfigured", String.valueOf(langConfigured));
try (OutputStream out = new FileOutputStream(CONFIG_FILE)) { p.store(out, "Super Lessons 1.4.0"); } catch (IOException ignored) {}
}
private static void loadConfig() {
File f = new File(CONFIG_FILE);
if (!f.exists()) return;
Properties p = new Properties();
try (InputStream in = new FileInputStream(f)) {
p.load(in);
currentThemeMode = p.getProperty("themeMode", currentThemeMode);
String rgb = p.getProperty("accentColor");
if (rgb != null && !rgb.isEmpty()) accentColor = new Color(Integer.parseInt(rgb));
soundEnabled = "true".equalsIgnoreCase(p.getProperty("soundEnabled", "true"));
overlayEnabled = "true".equalsIgnoreCase(p.getProperty("overlayEnabled", "false"));
currentLang = p.getProperty("lang", "RU");
langConfigured = "true".equalsIgnoreCase(p.getProperty("langConfigured", "false"));
} catch (Exception ignored) {}
}
private static List readLines(String fn) {
List res = new ArrayList(); File f = new File(fn); if (!f.exists()) return res;
try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
String l; while ((l = br.readLine()) != null) if (!l.trim().isEmpty()) res.add(l);
} catch (IOException ignored) {}
return res;
}
private static void appendLine(String fn, String line) {
try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fn, true), StandardCharsets.UTF_8))) {
bw.write(line); bw.newLine();
} catch (IOException ignored) {}
}
private static void saveData(String fn, DefaultTableModel m) {
try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fn), StandardCharsets.UTF_8))) {
for (int i = 0; i < m.getRowCount(); i++) {
for (int j = 0; j < m.getColumnCount(); j++) { bw.write(String.valueOf(m.getValueAt(i, j))); if (j < m.getColumnCount() - 1) bw.write(";"); }
bw.newLine();
}
} catch (IOException ignored) {}
}
private static void loadData(String fn, DefaultTableModel m) {
List lines = readLines(fn);
for (int i = 0; i < lines.size(); i++) m.addRow(new Vector(splitSemi(String.valueOf(lines.get(i)))));
}
}
