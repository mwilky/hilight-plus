package com.mwilky.hilight.plus.core;

/** The app's end of the daemon's debug log: each call carries one already-formatted line. */
oneway interface ILogSink {
    void onLog(String line);
}
