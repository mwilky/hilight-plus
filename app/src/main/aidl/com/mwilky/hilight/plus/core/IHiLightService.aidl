package com.mwilky.hilight.plus.core;

import com.mwilky.hilight.plus.core.ILogSink;

interface IHiLightService {
    void triggerAlert(String pattern, long color, float brightness, long speedMs, long durationMs, boolean requiresFaceDown, String dndMode, String quietHoursMode, int quietStartMinutes, int quietEndMinutes);
    void postAlert(String key, String pattern, long color, float brightness, long speedMs, long durationMs, boolean requiresFaceDown, String dndMode, String quietHoursMode, int quietStartMinutes, int quietEndMinutes);
    void removeAlert(String key);
    void clearAlert();
    void turnOff();
    int getLedCount();
    String getSecureString(String key);
    void destroy();
    void startIncomingCall(String pattern, long color, float brightness, long speedMs, boolean requiresFaceDown, String dndMode, String quietHoursMode, int quietStartMinutes, int quietEndMinutes);
    void stopIncomingCall();
    void setDeviceFaceDown(boolean faceDown);
    void setDndActive(boolean dndActive);
    void setDndSuppressEnabled(boolean enabled);
    void setQuietHours(boolean enabled, int startMinutes, int endMinutes);
    void setSplitRing(boolean enabled);
    void testAlert(String pattern, long color, float brightness, long speedMs, long durationMs);
    void cancelTestAlert();
    void setBatteryConfig(boolean enabled, String chargingPattern, String lowPattern, boolean autoColor, long color, boolean showCharging, boolean lowWarningEnabled, int lowThresholdPercent, int fullTimeoutMinutes, boolean overridesNotifications, boolean requiresFaceDown, String dndMode, String quietHoursMode, int quietStartMinutes, int quietEndMinutes);
    void setBatteryState(int levelPercent, boolean charging, boolean full);
    String getGlobalString(String key);
    boolean putGlobalString(String key, String value);
    void setEntitled(boolean entitled);
    void setLogSink(ILogSink sink);
    String dumpState();
    void setSplitAnimation(String animation);
    void restart();
    boolean isRingStuck();
    int getVersionCode();
    boolean grantWriteSecureSettings();
    // JSON object keyed by Gemini state id: pattern, color, brightness, speed, requiresFaceDown, dndMode,
    // quietHoursMode, quietStart, quietEnd (-1 for none). A missing state stays unlit.
    void setGeminiConfig(boolean enabled, String statesJson);
    // Split ring levels, each 0.1-1.0 of full power: Breathe and Spotlight move between dimmest
    // and brightest, Solid and Rotate hold steady.
    void setSplitLook(float dimmest, float brightest, float steady);
    // Previews the split ring on the test channel: colors newest first, cancelled by cancelTestAlert.
    void testSplit(in long[] colors, String animation, float dimmest, float brightest, float steady, long durationMs);
}
