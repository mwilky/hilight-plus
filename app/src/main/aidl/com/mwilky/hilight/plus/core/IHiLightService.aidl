package com.mwilky.hilight.plus.core;

interface IHiLightService {
    void setAmbient(String pattern, long color, float brightness, long speedMs);
    void triggerAlert(String pattern, long color, float brightness, long speedMs, long durationMs);
    void postAlert(String key, String pattern, long color, float brightness, long speedMs, long durationMs);
    void removeAlert(String key);
    void clearAlert();
    void turnOff();
    int getLedCount();
    boolean isSessionActive();
    int getSecureInt(String key, int defaultValue);
    String getSecureString(String key);
    void destroy();
}
