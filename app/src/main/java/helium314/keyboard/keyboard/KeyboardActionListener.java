/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard;

import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import helium314.keyboard.agent.SpaceGesture;
import helium314.keyboard.event.HapticEvent;
import helium314.keyboard.keyboard.internal.LayoutDirective;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.common.InputPointers;
import helium314.keyboard.latin.utils.RecapitalizeMode;

public interface KeyboardActionListener {
    /**
     * Called when the user presses a key. This is sent before the {@link #onCodeInput} is called.
     * For keys that repeat, this is only called once.
     *
     * @param primaryCode the unicode of the key being pressed. If the touch is not on a valid key,
     *            the value will be zero.
     * @param repeatCount how many times the key was repeated. Zero if it is the first press.
     * @param pointerCount the number of pointers being tracked, including this one.
     * @param hapticEvent the type of haptic feedback to perform.
     */
    void onPressKey(int primaryCode, int repeatCount, int pointerCount, HapticEvent hapticEvent);

    void onLongPressKey(int primaryCode);

    /**
     * Called when the user releases a key. This is sent after the {@link #onCodeInput} is called.
     * For keys that repeat, this is only called once.
     *
     * @param primaryCode the code of the key that was released
     * @param withSliding true if releasing has occurred because the user slid finger from the key
     *             to other key without releasing the finger.
     */
    void onReleaseKey(int primaryCode, boolean withSliding);

    /** For handling hardware key presses. Returns whether the event was handled. */
    boolean onKeyDown(int keyCode, KeyEvent keyEvent);

    /** For handling hardware key presses. Returns whether the event was handled. */
    boolean onKeyUp(int keyCode, KeyEvent keyEvent);

    /**
     * Send a key code to the listener.
     *
     * @param primaryCode this is the code of the key that was pressed
     * @param x x-coordinate pixel of touched event. If onCodeInput is not called by
     *            {@link PointerTracker} or so, the value should be
     *            {@link Constants#NOT_A_COORDINATE}. If it's called on insertion from the
     *            suggestion strip, it should be {@link Constants#SUGGESTION_STRIP_COORDINATE}.
     * @param y y-coordinate pixel of touched event. If #onCodeInput is not called by
     *            {@link PointerTracker} or so, the value should be
     *            {@link Constants#NOT_A_COORDINATE}.If it's called on insertion from the
     *            suggestion strip, it should be {@link Constants#SUGGESTION_STRIP_COORDINATE}.
     * @param isKeyRepeat true if this is a key repeat, false otherwise
     */
    // TODO: change this to send an Event object instead
    void onCodeInput(int primaryCode, int x, int y, boolean isKeyRepeat);

    /**
     * Sends a string of characters to the listener.
     *
     * @param text the string of characters to be registered.
     */
    void onTextInput(String text);

    /** Fork (review of stages 44/45): a vault secret or code the user picked; never composed or learned. */
    default void onPrivateTextInput(String text) { onTextInput(text); }

    /** sends content (URI and description) */
    void onContent(InputContentInfoCompat content);

    /**
     * Called when user started batch input.
     */
    void onStartBatchInput();

    /**
     * Sends the ongoing batch input points data.
     * @param batchPointers the batch input points representing the user input
     */
    void onUpdateBatchInput(InputPointers batchPointers);

    /**
     * Sends the final batch input points data.
     *
     * @param batchPointers the batch input points representing the user input
     */
    void onEndBatchInput(InputPointers batchPointers);

    void onCancelBatchInput();

    /**
     * Called when user released a finger outside any key.
     */
    void onCancelInput();

    /**
     * Called when user finished sliding key input.
     */
    void onFinishSlidingInput();

    /**
     * Send a non-"code input" custom request to the listener.
     * @return true if the request has been consumed, false otherwise.
     */
    boolean onCustomRequest(CustomAction request);

    /**
     * Called when the user performs a swipe gesture on the space bar. [gesture] is the direction the
     * swipe started in and stays the same for the whole swipe; [steps] is signed along its axis.
     */
    boolean onSpaceSwipe(SpaceGesture gesture, int steps);
    /**
     * Fork (stage 33): a sideways space swipe whose action runs once moved. [distance] is the current
     * x minus the touch-down x in px (signed), [keyWidth] the keyboard's most common key width in px.
     * The action runs at the last detent (SpaceDetents); the swipe ends with onEndSpaceSwipe.
     */
    void onSpaceSwipeDistance(SpaceGesture gesture, int distance, int keyWidth);
    void onEndSpaceSwipe();
    /** Fork (stage 87): [onCursorTrack] answers: not handled here (the old step logic runs), a drag that has not become a swipe yet, a swipe. */
    int TRACK_OFF = 0;
    int TRACK_PENDING = 1;
    int TRACK_ACTIVE = 2;
    /** A refused finger (another one owns the track) dragged past the swipe distance: its touch is dropped (no tap, no repeat, no long press). */
    int TRACK_REFUSED_DRAG = 3;
    /**
     * Fork (stage 87): the cursor trackpad. Called on every move event for each finger that went down on space or
     * delete ([pointerId]; [downTime] identifies its touch), with the distance from its touch-down point in px (not
     * stepped) and the event time in ms. Exactly one finger can own a track: TRACK_OFF means the old step logic of
     * PointerTracker runs for this finger; TRACK_PENDING means nothing happens yet (a tap stays a tap; a finger refused
     * because another one owns the track stays pending until it drags past the swipe distance, then TRACK_REFUSED_DRAG: the
     * tracker cancels its key timers and drops the touch: no tap, no repeat, no long press. The key stays drawn as pressed until the lift,
     * and that lift still releases older fingers (upstream releaseAllPointersOlderThan), which ends an older owner's swipe, a delete
     * slide then deleting its selection); TRACK_ACTIVE means the listener moves the
     * cursor and PointerTracker only marks the swipe (no space typed, no long press, onEndSpaceSwipe at the end).
     * Once a finger got TRACK_ACTIVE it never gets TRACK_OFF again in the same touch.
     */
    int onCursorTrack(int pointerId, int code, int dx, int dy, long eventTime, long downTime);
    /** Fork (stage 87): the touch of [pointerId] is over (up, cancel, tracking dropped); frees the track it owned. -1: every touch (a field starts or the keyboard hides). */
    void onCursorTrackEnd(int pointerId);
    void toggleLayout(LayoutDirective.Utility layout, int autoCapsFlags, @Nullable RecapitalizeMode recapitalizeMode);
    void onLongPressAlphaSymbolForNumpad();

    void onMoveDeletePointer(int steps);
    void onUpWithDeletePointerActive();
    void resetMetaState();

    KeyboardActionListener EMPTY_LISTENER = new Adapter();

    enum SwipeAction { NONE, MOVE_CURSOR, SWITCH_LANGUAGE, TOGGLE_NUMPAD, TOGGLE_DPAD, HIDE_KEYBOARD, TOUCHPAD_MODE }
    // SPACE_HOLD runs the hold action chosen for the space bar (SpaceGestures); false if nothing was done
    enum CustomAction { SHOW_INPUT_METHOD_PICKER, TOUCHPAD_ON, TOUCHPAD_OFF, PERFORM_HAPTIC, SPACE_HOLD }

    class Adapter implements KeyboardActionListener {
        @Override
        public void onPressKey(int primaryCode, int repeatCount, int pointerCount, HapticEvent hapticEvent) {}
        @Override
        public void onLongPressKey(int primaryCode) {}
        @Override
        public void onReleaseKey(int primaryCode, boolean withSliding) {}
        @Override
        public boolean onKeyDown(int keyCode, KeyEvent keyEvent) { return false; }
        @Override
        public boolean onKeyUp(int keyCode, KeyEvent keyEvent) { return false; }
        @Override
        public void onCodeInput(int primaryCode, int x, int y, boolean isKeyRepeat) {}
        @Override
        public void onTextInput(String text) {}
        @Override
        public void onContent(InputContentInfoCompat content) {}
        @Override
        public void onStartBatchInput() {}
        @Override
        public void onUpdateBatchInput(InputPointers batchPointers) {}
        @Override
        public void onEndBatchInput(InputPointers batchPointers) {}
        @Override
        public void onCancelBatchInput() {}
        @Override
        public void onCancelInput() {}
        @Override
        public void onFinishSlidingInput() {}
        @Override
        public boolean onCustomRequest(CustomAction request) {
            return false;
        }
        @Override
        public boolean onSpaceSwipe(SpaceGesture gesture, int steps) {
            return false;
        }
        @Override
        public void onSpaceSwipeDistance(SpaceGesture gesture, int distance, int keyWidth) {}
        @Override
        public void toggleLayout(LayoutDirective.Utility layout, int autoCapsFlags, @Nullable RecapitalizeMode recapitalizeMode) {}
        @Override
        public void onLongPressAlphaSymbolForNumpad() {}
        @Override
        public void onEndSpaceSwipe() {}
        @Override
        public int onCursorTrack(int pointerId, int code, int dx, int dy, long eventTime, long downTime) { return TRACK_OFF; }
        @Override
        public void onCursorTrackEnd(int pointerId) {}
        @Override
        public void onMoveDeletePointer(int steps) {}
        @Override
        public void onUpWithDeletePointerActive() {}
        @Override
        public void resetMetaState() {}
    }
}
