package com.mwilky.hilight.plus.core;

interface IHiLightService {
    void setAmbient(String pattern, long color, float brightness, long speedMs);
    void triggerAlert(String pattern, long color, float brightness, long speedMs, long durationMs, boolean requiresFaceDown, String dndMode, String quietHoursMode, int quietStartMinutes, int quietEndMinutes);
    void postAlert(String key, String pattern, long color, float brightness, long speedMs, long durationMs, boolean requiresFaceDown, String dndMode, String quietHoursMode, int quietStartMinutes, int quietEndMinutes);
    void removeAlert(String key);
    void clearAlert();
    void turnOff();
    int getLedCount();
    boolean isSessionActive();
    int getSecureInt(String key, int defaultValue);
    String getSecureString(String key);
    void destroy();
    void startIncomingCall(String pattern, long color, float brightness, long speedMs, boolean requiresFaceDown, String dndMode, String quietHoursMode, int quietStartMinutes, int quietEndMinutes);
    void stopIncomingCall();
    void setDeviceFaceDown(boolean faceDown);
    void setDndActive(boolean dndActive);
    void setDndSuppressEnabled(boolean enabled);
    void setQuietHours(boolean enabled, int startMinutes, int endMinutes);
}
