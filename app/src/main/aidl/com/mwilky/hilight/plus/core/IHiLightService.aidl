package com.mwilky.hilight.plus.core;

interface IHiLightService {
    void setAmbient(String pattern, long color, float brightness, long speedMs);
    void triggerAlert(String pattern, long color, float brightness, long speedMs, long durationMs, boolean requiresFaceDown);
    void postAlert(String key, String pattern, long color, float brightness, long speedMs, long durationMs, boolean requiresFaceDown);
    void removeAlert(String key);
    void clearAlert();
    void pauseAlerts();
    void resumeAlerts();
    void turnOff();
    int getLedCount();
    boolean isSessionActive();
    int getSecureInt(String key, int defaultValue);
    String getSecureString(String key);
    void destroy();
    void startIncomingCall(String pattern, long color, float brightness, long speedMs, boolean requiresFaceDown);
    void stopIncomingCall();
    void setDeviceFaceDown(boolean faceDown);
}
