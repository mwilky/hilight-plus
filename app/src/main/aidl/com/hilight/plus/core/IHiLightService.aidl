package com.hilight.plus.core;

interface IHiLightService {
    void setAmbient(String pattern, long color, float brightness, long speedMs);
    void triggerAlert(String pattern, long color, float brightness, long speedMs, long durationMs);
    void clearAlert();
    void turnOff();
    int getLedCount();
    boolean isSessionActive();
    void destroy();
}
