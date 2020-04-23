package com.github.opengrabeso.loctio;

import com.sun.jna.*;
import com.sun.jna.win32.*;
/**
 * adapted from http://ochafik.com/p_98
 */
public class Win32IdleTime {
    final static boolean isWindows = System.getProperty("os.name").contains("Windows");

    public interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
        /**
         * Retrieves the number of milliseconds that have elapsed since the system was started.
         * @return number of milliseconds that have elapsed since the system was started.
         */
        public int GetTickCount();
    };
    public interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);
        /**
         * Contains the time of the last input.
         */
        @Structure.FieldOrder({"cbSize", "dwTime"})
        public static class LASTINPUTINFO extends Structure {
            public int cbSize = 8;
            /// Tick count of when the last input event was received.
            public int dwTime;
        }
        /**
         * Retrieves the time of the last input event.
         * @return time of the last input event, in milliseconds
         */
        public boolean GetLastInputInfo(LASTINPUTINFO result);
    };
    /**
     * Get the amount of milliseconds that have elapsed since the last input event
     * (mouse or keyboard)
     * @return idle time in milliseconds
     */
    public static int getIdleTimeMillisWin32() {
        User32.LASTINPUTINFO lastInputInfo = new User32.LASTINPUTINFO();
        User32.INSTANCE.GetLastInputInfo(lastInputInfo);
        return Kernel32.INSTANCE.GetTickCount() - lastInputInfo.dwTime;
    }

    public static boolean isSuppported() {
        return isWindows;
    }
}