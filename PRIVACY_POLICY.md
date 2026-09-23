# Privacy Policy for HiLight Plus

**Last updated:** September 23, 2026

**HiLight Plus** ("we", "our", or "the app") is developed by **mwilky**. We are committed to protecting your privacy. This Privacy Policy outlines how your information is handled when you use HiLight Plus.

---

## 1. Summary: 100% On-Device Processing
HiLight Plus operates **entirely on your device**. 
- We do **not** collect, store, transmit, sell, or share any personal information.
- The app does **not** connect to external servers or cloud services. The only network activity is Google Play Billing, used to process the one-off purchase; Google's handling of that transaction is covered by Google's own privacy policy.
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

### C. Shizuku Privileged Access (`moe.shizuku.manager.permission.API_V23`)
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
