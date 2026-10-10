/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.about;

import java.util.ArrayList;
import java.util.List;

/** Whether About phone shows the phone number and network addresses. Hidden by default. */
public final class AboutPrivacy {

    public interface Listener {
        void onRevealChanged(boolean revealed);
    }

    private static final List<Listener> sListeners = new ArrayList<>();
    private static boolean sRevealed;

    private AboutPrivacy() {
    }

    public static boolean isRevealed() {
        return sRevealed;
    }

    static void reset() {
        if (sRevealed) {
            toggle();
        }
    }

    static void toggle() {
        sRevealed = !sRevealed;
        for (Listener listener : new ArrayList<>(sListeners)) {
            listener.onRevealChanged(sRevealed);
        }
    }

    static void addListener(Listener listener) {
        if (!sListeners.contains(listener)) {
            sListeners.add(listener);
        }
    }

    static void removeListener(Listener listener) {
        sListeners.remove(listener);
    }
}
