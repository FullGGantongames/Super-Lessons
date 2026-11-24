<div align="center">

# 🎓 Super Lessons / Супер уроки

**A lightweight desktop application for online learning, classroom management, and real-time communication between teachers and students.**

**Лёгкое десктопное приложение для онлайн-обучения, управления классами и общения между учителями и учениками.**

![Java](https://shields.io)
![Platform](https://shields.io)
![Language](https://shields.io)
![License](https://shields.io)

</div>

---

## 📸 Screenshots / Скриншоты

> *Tip: Drag and drop your screenshots here in the GitHub editor to replace these placeholders!*

| Light & Dark Themes | Chats & Classes |
| :---: | :---: |
| ![Main Window](https://placeholder.com) | ![Chats](https://placeholder.com) |

---

## 🌐 How to Change Language / Как сменить язык

If you accidentally selected **Russian** at startup and want to switch back to **English** (or vice versa), follow these steps using the top menu bar:

1. Look at the top-left menu bar and click the **second tab** from the left: **`Темы и Вид`** *(Themes & View)*.
2. Hover over the very bottom item in the dropdown list: **`Язык / Language`**.
3. Click **`English (EN)`** (or **`Русский (RU)`**).
4. The interface will reload immediately in your chosen language!

**Путь в меню (если интерфейс на русском):**
> **`Темы и Вид`** *(2-я вкладка сверху)* ➔ **`Язык / Language`** *(в самом низу)* ➔ **`English (EN)`**

*(Alternatively, you can delete the `config.properties` file in the app folder to bring back the startup language selection window).*

---

## ✨ Key Features / Основные возможности

* **👥 Role-Based Access / Разделение ролей:**
  * **Teachers (Учителя):** Create courses, organize virtual classes, add/remove members remotely, lock assignments with passwords, and review student answers.
  * **Students (Ученики):** Study theory and examples, submit solutions, join classes, and unlock secret tasks.
* **🏫 Virtual Classes / Учебные классы:** Create classes and remotely add students or fellow teachers by login.
* **💬 Chats & Direct Messages / Чаты и личные сообщения:** General school chat, dedicated class chat rooms, and private 1-on-1 direct messages with unread counters, sound alerts, and system tray notifications.
* **🔒 Password-Protected Tasks / Задания по паролю:** Send confidential assignments locked with a custom password.
* **🎨 8 Built-in Themes / 8 тем оформления:** Windows 11 (Light & Dark), Cyberpunk Neon, Emerald Forest, Deep Ocean, Sunset, Classic Metal, and Nimbus.
* **🌐 Bilingual Interface / Двуязычный интерфейс:** Switch seamlessly between **English (EN)** and **Russian (RU)**.
* **📦 Built-in EXE Builder / Встроенный сборщик `.exe`:** Package the source code into a standalone Windows executable directly from the app menu.

---

## 🚀 How to Run / Как запустить

### Option 1: Ready-to-Use Windows Installer (No Java Required)
1. Go to the **[Releases](../../releases)** section on the right side of this page.
2. Download the latest Windows installer (`.exe`) and run it.
3. Launch **Super Lessons**, choose your language (**EN** or **RU**), and register a new account.

### Option 2: Run from Source Code
Make sure you have **JDK 17+** installed, then run:
```bash
javac -encoding UTF-8 ManagementApp.java
java ManagementApp
```

---

## 💡 Syncing Between PCs / Синхронизация между компьютерами

The app stores all data locally in lightweight text files. To use it remotely with a class, simply place the installed application folder into a shared network drive or a synced cloud folder (such as Google Drive, Yandex Disk, or OneDrive) with write permissions.

---

## 🛠️ Known Notes / Известные особенности

* The Dark Theme currently uses a legacy 1999-style UI shell (`MetalLookAndFeel`). It's not a big deal — just a hint that in the next major update we'll bring the Dark Theme to life with a modern flat design!
