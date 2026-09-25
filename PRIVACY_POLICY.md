# Privacy Policy for HiLight Plus

**Last updated:** September 25, 2026

**HiLight Plus** ("we", "our", or "the app") is developed by **mwilky**. We are committed to protecting your privacy. This Privacy Policy outlines how your information is handled when you use HiLight Plus.

---

## 1. Summary: 100% On-Device Processing
HiLight Plus operates **entirely on your device**. 
- We do **not** collect, store, transmit, sell, or share any personal information. A debug log stays on your device and is only sent if you choose to share it (see section 3).
- The app does **not** connect to external servers or cloud services. The only network activity off your phone is Google Play Billing, used to process the one-off purchase; Google's handling of that transaction is covered by Google's own privacy policy. The app's other network use is a connection to your phone's own Wireless debugging, which never leaves the device (see section 2C).
- The app does **not** include third-party tracking, analytics, or advertising SDKs.

---

## 2. Permissions We Request and Why

HiLight Plus requests sensitive Android permissions solely to deliver real-time rear hardware LED lighting alerts. All processing occurs strictly in local memory on your device:

### A. Contacts (`READ_CONTACTS`)
- **Purpose:** Allows you to pick contacts and assign custom LED ring lighting colors and patterns (e.g., custom animations for family or VIP contacts), to tell saved contacts from unknown callers and senders when a call or message arrives, and to check whether a caller or sender is starred as a favourite.
- **Data Handling:** Contact data remains solely within Android's local contacts database on your device and is only accessed when matching an incoming caller or sender name or opening the contact picker.

### B. Notification Listener Access (`BIND_NOTIFICATION_LISTENER_SERVICE`)
- **Purpose:** Used to detect incoming calls (from the phone dialer and from apps such as WhatsApp, Teams and Meet) and incoming notifications and chats from user-selected applications, to trigger corresponding LED light alerts. HiLight Plus does **not** request phone-state or call-log permissions; calls are recognised from the dialer's own call notification.
- **Data Handling:** Notification metadata (app package name, sender or caller title) is processed in real time to match your custom lighting rules. We do **not** read or store notification contents, chat bodies, or attachments.

### C. Reaching the Rear Lights (`INTERNET`, `ACCESS_LOCAL_NETWORK`, `WRITE_SECURE_SETTINGS`)
- **Purpose:** Android only lets the phone's own developer tools control the rear LED array. HiLight Plus pairs once with your phone's Wireless debugging and uses it to start a small helper process on your phone that drives the lights. The Internet permission is needed for that connection, which goes only to the phone itself (127.0.0.1). Local network access is used to find the pairing screen, which Wireless debugging announces on the phone. After the first connection, the helper grants the app permission to change secure system settings, used only to switch Wireless debugging on when the helper needs starting (after a restart, for example), and off again afterwards when it can safely do so, so the lights resume without your help.
- **Data Handling:** No data is sent off your device. The pairing key is stored in the app's private storage, is excluded from backups, and is never shared. You can remove the pairing at any time under Developer options > Wireless debugging.

### D. Notifications (`POST_NOTIFICATIONS`) and Start-up (`RECEIVE_BOOT_COMPLETED`)
- **Purpose:** A notification guides you through setup and lets you type the pairing code. Start-up access lets the app reconnect to the lights after a restart or an update.
- **Data Handling:** Nothing is collected or sent.

### E. Shizuku Privileged Access, optional (`moe.shizuku.manager.permission.API_V23`)
- **Purpose:** For people who prefer Shizuku, used to communicate with the local Shizuku service via local Android Binder IPC to control the Pixel device's rear hardware LED array.
- **Data Handling:** Binder IPC communication remains entirely local to your device.

---

## 3. Data Storage & Preferences
All your custom configurations (color choices, animation speeds, per-contact rules, and per-app settings) are stored locally on your device using Android's encrypted **DataStore / SharedPreferences**. You can completely wipe this data at any time by clearing the app data in Android Settings or using the "Reset Onboarding & Rules" button within the app.

### Debug Log
To help diagnose problems, the app keeps a small debug log on your device (at most about 512 KB, with the oldest entries dropped first). It records what the lights were doing and why: app package names, which rule matched, notification categories, and connection and permission events. It does **not** record contact names, phone numbers, or notification contents.
- The log never leaves your device on its own. It is only sent if you tap **Share** on the About page, which opens Android's share sheet so you choose the recipient. The shared report also lists your device model, Android version, permission status, and your settings and rules, with contact rules identified by an internal ID rather than a name.
- You can delete the log at any time with **Clear** on the About page, or by clearing the app's data.

---

## 4. Children's Privacy
HiLight Plus does not collect any personal information from anyone, including children under the age of 13.

---

## 5. Changes to This Privacy Policy
We may update our Privacy Policy from time to time. Any updates will be posted with a revised "Last updated" date.

---

## 6. Contact Us
If you have any questions or suggestions about this Privacy Policy, please contact us at:
- **Developer:** mwilky
- **Support / Inquiries:** mwilky.dev@gmail.com
