# Privacy Policy for HiLight Plus

**Last updated:** August 26, 2026

**HiLight Plus** ("we", "our", or "the app") is developed by **mwilky**. We are committed to protecting your privacy. This Privacy Policy outlines how your information is handled when you use HiLight Plus.

---

## 1. Summary: 100% On-Device Processing
HiLight Plus operates **entirely on your device**. 
- We do **not** collect, store, transmit, sell, or share any personal information.
- The app does **not** connect to external servers or cloud services.
- The app does **not** include third-party tracking, analytics, or advertising SDKs.

---

## 2. Permissions We Request and Why

HiLight Plus requests sensitive Android permissions solely to deliver real-time rear hardware LED lighting alerts. All processing occurs strictly in local memory on your device:

### A. Phone State (`READ_PHONE_STATE`) & Call Logs (`READ_CALL_LOG`)
- **Purpose:** Used to detect incoming phone calls in real time and match the incoming phone number against your saved contacts.
- **Data Handling:** Phone numbers and call states are processed transiently in memory to trigger your configured LED lighting animations and are never logged, stored externally, or transmitted off your device.

### B. Contacts (`READ_CONTACTS`)
- **Purpose:** Allows you to pick contacts and assign custom LED ring lighting colors and patterns (e.g., custom animations for family or VIP contacts), and look up contact names for incoming calls/messages.
- **Data Handling:** Contact data remains solely within Android's local contacts database on your device and is only accessed when matching incoming caller IDs or opening the contact picker.

### C. Notification Listener Access (`BIND_NOTIFICATION_LISTENER_SERVICE`)
- **Purpose:** Used to detect incoming notifications and chats from user-selected applications (e.g., WhatsApp, Messages, Slack) to trigger corresponding LED light alerts.
- **Data Handling:** Notification metadata (app package name, sender title) is processed in real time to match your custom lighting rules. We do **not** read or store notification contents, chat bodies, or attachments.

### D. Shizuku Privileged Access (`moe.shizuku.manager.permission.API_V23`)
- **Purpose:** Used to securely communicate with the local Shizuku service via local Android Binder IPC to control the Pixel device's rear hardware LED array.
- **Data Handling:** Binder IPC communication remains entirely local to your device.

---

## 3. Data Storage & Preferences
All your custom configurations (color choices, animation speeds, per-contact rules, and per-app settings) are stored locally on your device using Android's encrypted **DataStore / SharedPreferences**. You can completely wipe this data at any time by clearing the app data in Android Settings or using the "Reset Onboarding & Rules" button within the app.

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
