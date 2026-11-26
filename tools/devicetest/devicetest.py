#!/usr/bin/env python3
"""Device tests for the SecondHand keyboard in an Android emulator (stage 73).

Run on a machine with a booted emulator and adb on the PATH (the workflow device-tests.yml does):

    python3 -I tools/devicetest/devicetest.py --keyboard-apk <apk> --test-apk <apk> --out <dir>

The script installs the keyboard and the small test app (tools/devicetest/app), makes the keyboard the
current input method and then works like a user: it asks the test app's ProbeAccessibilityService
for the nodes of all windows (the keyboard exposes its keys as accessibility nodes while an
accessibility service is enabled), finds the keys there, taps them with "input tap", reads
the content of the field back from the test app and compares it with the expectation.

Results go to the log (one RESULT line per scenario, one SUMMARY line), to <out>/summary.md and
<out>/results.json; screenshots and the screen recording go to <out> as well. The exit code is 0
only if every scenario ran and passed (control run: failed) and the recording exists.

--control is the control run that shows the scenarios can fail: the last character of every typed
text is left out, so the field really holds something else than expected and every scenario must end
with a failed comparison. The control run itself is good (exit code 0) exactly when every scenario
FAILED; a scenario that still passes cannot notice an error and is reported as "CONTROL BROKEN". It
shows this for the first comparison that depends on the typed text, not for every comparison.

Only the standard library is used. Nothing typed here is secret; the texts are fixed test words.
"""

import argparse
import base64
import json
import math
import os
import re
import struct
import subprocess
import sys
import threading
import time
import zlib

KEYBOARD_PACKAGE = "dev.personal.agentkeyboard.debug"
TEST_PACKAGE = "dev.personal.secondhand.devicetest"
PROBE_SERVICE = TEST_PACKAGE + "/" + TEST_PACKAGE + ".ProbeAccessibilityService"
AUTOFILL_SERVICE = TEST_PACKAGE + "/" + TEST_PACKAGE + ".FakeAutofillService"  # stage 73c, test app only
FAKE_USER, FAKE_PASSWORD = "testnutzer@example.invalid", "Testpasswort-73c"  # what FakeAutofillService offers
# stage 79: the titles of the three datasets of FakeAutofillService, in the order the service ranks them
AUTOFILL_CHIP_TITLES = ["Test 73c", "Test 73c-2", "Test 73c-3"]
AUTOFILL_TITLE_ID = "autofill_inline_suggestion_title"
DUMP_URI = "content://" + TEST_PACKAGE + ".dump/now"
PROBE_LABEL = "SH Device Test"  # android:label of the test app, as "dumpsys accessibility" names a bound service
# The system languages of the emulator. The keyboard enables one layout per system language on its
# first start (SubtypeSettings.getDefaultEnabledSubtypes), so this gives German first, then English.
LOCALES = "de-DE,en-US"
REMOTE_TMP = "/data/local/tmp"

# Names of the keys without a character, as the keyboard describes them (strings-talkback-descriptions,
# German and English). A key is matched by its exact description.
KEY_NAMES = {
    "space": ("Leertaste", "Space"),
    "delete": ("Löschen", "Delete"),
    "shift": ("Umschalttaste", "Umschalttaste-Taste", "Shift"),
    # the enter key is named after its action in the field
    "enter": ("Eingabetaste", "Weiter", "Fertig", "Suchen", "Senden", "Los", "Zurück",
              "Return", "Next", "Done", "Search", "Send", "Go", "Previous"),
    "symbols": ("Symbole", "Symbols"),
    "letters": ("Buchstaben", "Letters"),
}
# What the strip shows without any word: the buttons at its ends and the empty slots.
# What the test app's three copy buttons put on the clipboard (MainActivity.CLIP_TEXTS).
CLIP_TEXTS = ("Aus der Zwischenablage", "Noch ein kopierter Text", "Der dritte kopierte Text")
STRIP_FIXED = ("Mehr Tasten", "More keys", "Kein Vorschlag", "No suggestion", "Textaktionen", "Text actions",
               # stage 85: the fixed bar's left key (grid, back arrow, close; with and without "Inkognito ist an")
               "Alle Werkzeuge", "All tools", "Werkzeuge zeigen", "Show tools", "Alle Werkzeuge schließen", "Close all tools")
# stage 85: the tools of the fixed bar are strip nodes with a description but they are never words or chips; they carry this id
STRIP_TOOL_IDS = (KEYBOARD_PACKAGE + ":id/agent_bar_tool", KEYBOARD_PACKAGE + ":id/suggestions_strip_toolbar_key")
# A word separator key announces a pending autocorrection instead of its plain name
# (AccessibilityUtils.getAutoCorrectionDescription): group 1 is the plain name.
AUTOCORRECT_WRAPPERS = (
    re.compile(r'^Mit (.+?) wird "'),
    re.compile(r"^(.+?) corrects "),
)


class DeviceError(Exception):
    """adb or the emulator did not answer as needed. Never a statement about the keyboard."""


class CheckFailed(Exception):
    """An expectation about the keyboard was not met."""


def log(message):
    print(message, flush=True)


# ---------------------------------------------------------------------------------------------- adb

def adb(*args, timeout=60, check=True, binary=False):
    """Runs adb and returns stdout. Raises DeviceError on a timeout or (with check) a failure."""
    command = ["adb", *args]
    try:
        done = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    except subprocess.TimeoutExpired:
        raise DeviceError("timeout after %ss: adb %s" % (timeout, " ".join(args)[:120]))
    except OSError as error:
        raise DeviceError("adb could not be started: %s" % error)
    if check and done.returncode != 0:
        raise DeviceError("adb %s -> exit %s: %s" % (
            " ".join(args)[:120], done.returncode, done.stderr.decode("utf-8", "replace").strip()[:200]))
    return done.stdout if binary else done.stdout.decode("utf-8", "replace")


def shell(command, timeout=60, check=True):
    return adb("shell", command, timeout=timeout, check=check).strip()


def wait_for_boot(limit=300):
    """Waits until Android has booted and the package manager answers."""
    deadline = time.time() + limit
    while time.time() < deadline:
        try:
            adb("wait-for-device", timeout=30)
            if shell("getprop sys.boot_completed", timeout=15, check=False) == "1" \
                    and "package:" in shell("pm path android", timeout=15, check=False):
                return
        except DeviceError:
            pass
        time.sleep(3)
    raise DeviceError("the emulator did not finish booting within %ss" % limit)


# --------------------------------------------------------------------------------------- UI dumps

class Node:
    __slots__ = ("package", "rid", "desc", "text", "cls", "window", "left", "top", "right", "bottom")

    def __init__(self, item):
        self.package = item.get("p", "")
        self.rid = item.get("id", "")
        self.desc = item.get("d", "")
        self.text = item.get("t", "")
        self.cls = item.get("c", "")
        self.window = item.get("w", 0)
        bounds = item.get("b") or [0, 0, 0, 0]
        self.left, self.top, self.right, self.bottom = (int(v) for v in bounds)

    @property
    def center(self):
        return (self.left + self.right) // 2, (self.top + self.bottom) // 2

    @property
    def visible(self):
        return self.right > self.left and self.bottom > self.top

    @property
    def area(self):
        return max(0, self.right - self.left) * max(0, self.bottom - self.top)

    @property
    def box(self):
        return "[%d,%d][%d,%d]" % (self.left, self.top, self.right, self.bottom)

    def contains(self, x, y):
        return self.left <= x < self.right and self.top <= y < self.bottom

    def move(self, dx, dy):
        self.left, self.right, self.top, self.bottom = self.left + dx, self.right + dx, self.top + dy, self.bottom + dy


class Screen:
    """The nodes of all windows, the keyboard window included, as the probe service of the test app
    lists them (ProbeAccessibilityService.dump)."""

    def __init__(self, json_text):
        self.raw = json_text
        try:
            data = json.loads(json_text)
        except ValueError as error:
            raise DeviceError("the screen dump is not JSON (%s): %r" % (error, json_text[:120]))
        if not isinstance(data, dict) or "nodes" not in data:
            raise DeviceError("the screen dump has no nodes: %r" % json_text[:160])
        self.nodes = [Node(item) for item in data["nodes"]]
        self.shifted = False
        self.shift = (0, 0)

    def by_id(self, name):
        """The node of the test app with this resource id, or None."""
        wanted = TEST_PACKAGE + ":id/" + name
        for node in self.nodes:
            if node.rid == wanted:
                return node
        return None

    def keys(self):
        """The keys of the keyboard: description -> Node (virtual nodes of the keyboard view)."""
        keys = [node for node in self.nodes
                if node.package == KEYBOARD_PACKAGE and node.cls.endswith(".Key") and node.desc and node.visible]
        view = self.keyboard_view()
        if keys and view is not None:
            # The keyboard's own key positions are not reliable on the screen (runs 8 and 9: 128 px too
            # high, or at the very top), but they are right relative to each other. The view that draws
            # the keys is an ordinary view with a true position, and the keys fill it down to its lower
            # edge: put the lower left corner of the block of keys on the lower left corner of the view.
            block_left = min(key.left for key in keys)
            block_top = min(key.top for key in keys)
            block_bottom = max(key.bottom for key in keys)
            height, view_height = block_bottom - block_top, view.bottom - view.top
            if not 0.75 * view_height <= height <= view_height + 4:
                raise DeviceError("the keys (%d px high) do not fit the keyboard view %s" % (height, view.box))
            dx, dy = view.left - block_left, view.bottom - block_bottom
            if dx or dy:  # the nodes are moved in place: a second call finds them aligned
                for key in keys:
                    key.move(dx, dy)
                self.shifted = True
                self.shift = (self.shift[0] + dx, self.shift[1] + dy)
        found = {}
        for node in keys:
            found.setdefault(plain_key_name(node.desc), node)
        return found

    def keyboard_view(self):
        """The view that draws the keys. Other nodes carry the same id (a container as high as the
        window, and the bottom rows of the emoji and clipboard views): the real one is a plain View,
        and the smallest visible of them."""
        best = None
        for node in self.nodes:
            if node.rid == KEYBOARD_PACKAGE + ":id/keyboard_view" and node.visible \
                    and node.cls == "android.view.View":
                if best is None or node.area < best.area:
                    best = node
        return best

    def keyboard_node(self, name):
        """The largest visible node of the keyboard with this resource id, or None."""
        best = None
        for node in self.nodes:
            if node.rid == KEYBOARD_PACKAGE + ":id/" + name and node.visible:
                if best is None or node.area > best.area:
                    best = node
        return best

    def keyboard_nodes(self):
        return [node for node in self.nodes if node.package == KEYBOARD_PACKAGE]

    def strip_nodes(self):
        """What the suggestion strip shows: the nodes with a text or description inside it (words, chips)."""
        strip = self.keyboard_node("suggestion_strip_view")
        if strip is None:
            return []
        return [node for node in self.nodes
                if node.package == KEYBOARD_PACKAGE and node is not strip and node.visible
                and node.rid not in STRIP_TOOL_IDS
                and (node.text or node.desc) and strip.contains(*node.center)]

    def strip_texts(self):
        return [node.text or node.desc for node in self.strip_nodes()]


def plain_key_name(description):
    for pattern in AUTOCORRECT_WRAPPERS:
        match = pattern.match(description)
        if match:
            return match.group(1)
    return description


def dump():
    """Reads the nodes of all windows through the test app's provider. Three tries, because the
    probe service is briefly away right after it was enabled."""
    last = "no attempt"
    for _ in range(3):
        try:
            text = adb("shell", "content read --uri " + DUMP_URI, timeout=40, check=False).strip()
            if text.startswith("{") and '"nodes"' in text:
                return Screen(text)
            last = "no node list: %r" % text[:160]
        except DeviceError as error:
            last = str(error)
        time.sleep(1.5)
    raise DeviceError("the screen could not be read three times (%s)" % last)


# ------------------------------------------------------------------------------------- the driver

class Driver:
    """What a scenario may do: open a field, press keys, read fields, compare."""

    def __init__(self, out_dir, control):
        self.out_dir = out_dir
        self.control = control
        self.logged_layouts = set()
        self.keymap = {}
        self.name = ""
        self.checks = 0
        self.notes = []
        self.hold_info = ""

    # -- app and fields

    def start(self, name):
        """A fresh test app for the scenario: empty fields, the scenario name as headline."""
        self.name = name
        self.keymap = {}
        self.checks = 0
        self.notes = []
        # A new activity in a cleared task, not "am force-stop": stopping the package would also stop
        # and disable its accessibility service, and the keyboard would hide its keys again.
        shell("am start -W --activity-clear-task -n %s/.MainActivity --es scenario %s"
              % (TEST_PACKAGE, name), timeout=60)
        # the headline shows the new name only in the new activity: old content cannot be taken for it
        self.wait(lambda screen: screen.by_id("field_text") is not None
                  and getattr(screen.by_id("scenario"), "text", None) == name, "the fresh test app of " + name)

    def wait(self, condition, what, limit=20.0):
        """Dumps until the condition holds and returns that screen. A timeout is a DeviceError: the
        emulator did not get into the state the scenario starts from."""
        deadline = time.time() + limit
        while True:
            screen = dump()
            if condition(screen):
                return screen
            if time.time() > deadline:
                self.save_dump(screen, "timeout")
                foreground_info("waited for " + what)
                raise DeviceError("waited %ss for %s; on screen: %s" % (int(limit), what, describe(screen)))
            time.sleep(0.7)

    def focus(self, field):
        """Taps into the field and waits for the keyboard with its keys."""
        screen = dump()
        node = screen.by_id("field_" + field)
        if node is None:
            foreground_info("field_%s missing" % field)
            raise DeviceError("field_%s is not on the screen" % field)
        tap(*node.center)
        screen = self.wait(lambda s: len(s.keys()) >= 10, "the keyboard with its keys")
        if not any(n.package == KEYBOARD_PACKAGE for n in screen.nodes):
            raise DeviceError("the keys do not belong to " + KEYBOARD_PACKAGE)
        self.keymap = screen.keys()
        if field not in self.logged_layouts:  # once per kind of field, for reading a failed run
            self.logged_layouts.add(field)
            log("INFO keys in field %s: %s" % (field, self.key_list()))
            view = screen.keyboard_view()
            sample = next(iter(self.keymap.values()))
            strip = screen.keyboard_node("suggestion_strip_view")
            log("INFO geometry in field %s: field %s, keyboard view %s, strip %s, key %r %s%s" % (
                field, node.box, view.box if view else "not found", strip.box if strip else "not found",
                sample.desc[:12], sample.box, ", keys moved by %s to fit the view" % (screen.shift,) if screen.shifted else ""))
            if len(self.logged_layouts) == 1:  # once: where the keyboard's views are
                parts = ["%s#%s%s" % (n.cls.rsplit(".", 1)[-1], n.rid.rsplit("/", 1)[-1], n.box)
                         for n in screen.keyboard_nodes() if not n.cls.endswith(".Key")]
                log("INFO keyboard window: " + " ".join(parts)[:1800])
        return screen

    def field(self, field):
        """The real content of the field, read from its mirror line in the test app."""
        node = dump().by_id("mirror_" + field)
        if node is None:
            raise DeviceError("mirror_%s is not on the screen" % field)
        prefix = field + "=["
        if not (node.text.startswith(prefix) and node.text.endswith("]")):
            raise DeviceError("unexpected mirror text %r" % node.text[:80])
        return node.text[len(prefix):-1]

    # -- keys

    def refresh_keys(self):
        self.keymap = dump().keys()

    def find_key(self, names):
        for name in names:
            if name in self.keymap:
                return self.keymap[name]
        return None

    def press(self, name):
        """Presses a named key (space, delete, shift, enter, symbols, letters)."""
        node = self.find_key(KEY_NAMES[name])
        if node is None:
            self.refresh_keys()
            node = self.find_key(KEY_NAMES[name])
        if node is None:
            raise CheckFailed("no key %r on the keyboard; keys: %s" % (name, self.key_list()))
        tap(*node.center)

    def type(self, text):
        """Taps the key of every character. A lower-case letter is tapped on its key whatever the shift
        state is, so automatic capitalisation shows in the result. For an upper-case letter the keyboard
        must be shifted: if its keys are lower-case, shift is tapped first (the keys name their own
        case). A character without a key in the current layout fails the scenario."""
        if self.control:
            text = text[:-1]
        for char in text:
            if char == " ":
                self.press("space")
                continue
            node = self.key_for(char)
            if node is None:
                self.refresh_keys()
                node = self.key_for(char)
            if node is None and not char.isalpha():
                # not in the letters: look on the symbols layout and come back
                self.press("symbols")
                self.refresh_keys()
                node = self.keymap.get(char)
                if node is None:
                    raise CheckFailed("no key for %r, also not among the symbols; keys: %s" % (char, self.key_list()))
                tap(*node.center)
                self.press("letters")
                self.refresh_keys()
                continue
            if node is None:
                raise CheckFailed("no key for %r on the keyboard; keys: %s" % (char, self.key_list()))
            if char.isalpha() and char.isupper():
                self.refresh_keys()  # the shift state changes while typing: look, do not assume
                if char not in self.keymap:
                    self.press("shift")
                    self.refresh_keys()
                    if char not in self.keymap:
                        raise CheckFailed("shift did not give the key %r; keys: %s" % (char, self.key_list()))
                node = self.keymap[char]
            tap(*node.center)

    def wait_for_suggestions(self, limit=15.0):
        """Waits until the strip offers words for what is being typed: the dictionary is loaded then.
        Without this the first words after a start are typed before the keyboard can correct them."""
        deadline = time.time() + limit
        while True:
            texts = [t for t in dump().strip_texts() if t not in STRIP_FIXED]
            if texts:
                return texts
            if time.time() > deadline:
                raise DeviceError("the strip offered no word within %ss (dictionary not loaded?)" % int(limit))
            time.sleep(0.8)

    def tap_in_strip(self, wanted):
        """Taps the word or chip of the strip whose text contains one of the wanted strings."""
        deadline = time.time() + 10
        while True:
            nodes = dump().strip_nodes()
            for node in nodes:
                label = node.text or node.desc
                if any(w.lower() in label.lower() for w in wanted):
                    self.notes.append("tapped %r in the strip" % label)
                    tap(*node.center)
                    return label
            if time.time() > deadline:
                raise CheckFailed("the strip shows none of %s but %s" % (
                    list(wanted), [n.text or n.desc for n in nodes][:12]))
            time.sleep(0.8)

    def _popup_nodes(self, screen):
        main = {(k.left, k.top, k.right, k.bottom) for k in self.keymap.values()}
        popup = {}
        for n in screen.nodes:
            if n.package == KEYBOARD_PACKAGE and n.cls.endswith(".Key") and n.desc and n.visible \
                    and n.desc not in self.keymap and (n.left, n.top, n.right, n.bottom) not in main:
                popup.setdefault(n.desc, n)
        return popup

    def popup_of(self, char):
        """Holds the key of the character and returns what its popup offers: description -> Node, read
        while the finger is still down. The finger is then lifted far outside the popup, so nothing is chosen."""
        node = self.key_for(char)
        if node is None:
            raise CheckFailed("no key for %r on the keyboard; keys: %s" % (char, self.key_list()))
        x, y = node.center
        shell("input motionevent DOWN %d %d" % (x, y), timeout=30)
        during = ""
        try:
            time.sleep(1.4)
            screen = dump()
            popup = self._popup_nodes(screen)
            mirror = screen.by_id("mirror_password")
            during = mirror.text if mirror else "?"
        finally:
            # lift the finger far above the keyboard, inside the app: a release outside every popup key
            # chooses nothing (a CANCEL at the key was followed by a paste into the field, runs 34 and 35)
            shell("input motionevent MOVE %d %d" % (x, 300), timeout=30, check=False)
            shell("input motionevent UP %d %d" % (x, 300), timeout=30, check=False)
        time.sleep(0.8)
        self.hold_info = "touch at %s: popup %s; password mirror while held %r, after cancel %r" % (
            node.box, " ".join("%s%s" % (d[:8], n.box) for d, n in list(popup.items())[:6]), during,
            self.field("password"))
        return popup

    def hold_and_choose(self, char, wanted):
        """One touch: presses the key of the character, waits for the popup, moves to the popup key
        whose description contains one of the wanted strings and lifts there. Returns its description."""
        node = self.key_for(char)
        if node is None:
            raise CheckFailed("no key for %r on the keyboard; keys: %s" % (char, self.key_list()))
        x, y = node.center
        shell("input motionevent DOWN %d %d" % (x, y), timeout=30)
        tx, ty = x, y
        try:
            time.sleep(1.4)
            popup = self._popup_nodes(dump())
            target = next((n for d, n in popup.items() if any(w.lower() in d.lower() for w in wanted)), None)
            if target is None:
                raise CheckFailed("the popup of %r offers none of %s but %s" % (char, list(wanted), sorted(popup)[:12]))
            # The popup's key boxes are reported with the top of the whole popup (run 33: two rows, every
            # key from y 1716), so the row is taken from the lower edge: one key height up from it.
            tx = target.center[0]
            ty = target.bottom - (node.bottom - node.top) // 2
            shell("input motionevent MOVE %d %d" % ((x + tx) // 2, (y + ty) // 2), timeout=30)
            shell("input motionevent MOVE %d %d" % (tx, ty), timeout=30)
            time.sleep(0.5)
        finally:
            shell("input motionevent UP %d %d" % (tx, ty), timeout=30, check=False)
        time.sleep(1.5)
        self.notes.append("held %r and chose %r" % (char, target.desc))
        self.hold_info = "key %s, popup %s, chose %s %s" % (
            node.box, " ".join("%s%s" % (d[:10], n.box) for d, n in list(popup.items())[:6]),
            target.desc[:14], target.box)
        return target.desc

    def density(self):
        """Pixels per dp of the screen (stage 87), read from `wm density` once."""
        if getattr(self, "_density", None) is None:
            lines = shell("wm density", check=False).strip().splitlines()
            match = re.search(r"(\d+)\s*$", lines[-1]) if lines else None  # the override line, if any, comes last
            self._density = (int(match.group(1)) if match else 420) / 160.0
        return self._density

    def drag_on(self, name, dx_dp, dy_dp, step_dp=4.0, pause=0.0, claim_dp=16.0):
        """One finger on a named key (space, delete), moved by (dx, dy) dp, then lifted. The first MOVE follows the
        DOWN at once and is claim_dp long (the trackpad takes a drag as a swipe from 10 dp, so the swipe is claimed
        and the long press of the space bar, 450 ms, is cancelled before it can fire); the rest goes in steps of at
        most step_dp. `input motionevent` needs 100 to 300 ms per call, so such a step is a finger of about 20 dp/s,
        the slow finger the cursor trackpad is tuned for (stage 87)."""
        node = self.find_key(KEY_NAMES[name])
        if node is None:
            self.refresh_keys()
            node = self.find_key(KEY_NAMES[name])
        if node is None:
            raise CheckFailed("no key %r on the keyboard; keys: %s" % (name, self.key_list()))
        x, y = node.center
        px = self.density()
        total = math.hypot(dx_dp, dy_dp)
        first = min(total, claim_dp)
        # positions along the line, in dp from the touch-down point: one big step first, then small ones
        marks = [first] if first > 0 else []
        left = total - first
        steps = int(math.ceil(left / step_dp)) if left > 0 else 0
        marks += [first + left * i / steps for i in range(1, steps + 1)]
        shell("input motionevent DOWN %d %d" % (x, y), timeout=30)
        tx, ty = x, y
        try:
            for mark in marks:
                tx = int(round(x + dx_dp * px * mark / total))
                ty = int(round(y + dy_dp * px * mark / total))
                shell("input motionevent MOVE %d %d" % (tx, ty), timeout=30)
                if pause:
                    time.sleep(pause)
        finally:
            shell("input motionevent UP %d %d" % (tx, ty), timeout=30, check=False)
        time.sleep(0.8)
        self.notes.append("dragged on %s by (%d, %d) dp in %d moves, the first %d dp" % (name, dx_dp, dy_dp, len(marks), first))

    def tap_keyboard_text(self, wanted, outside_strip=True):
        """Taps the node of the keyboard (not a key) whose text or description contains the wanted string."""
        deadline = time.time() + 8
        while True:
            screen = dump()
            strip = screen.keyboard_node("suggestion_strip_view")
            seen = []
            for n in screen.nodes:
                label = n.text or n.desc
                if n.package != KEYBOARD_PACKAGE or not n.visible or not label or n.cls.endswith(".Key"):
                    continue
                seen.append(label)
                if wanted.lower() in label.lower() and not (outside_strip and strip and strip.contains(*n.center)):
                    tap(*n.center)
                    time.sleep(1.0)
                    return label
            if time.time() > deadline:
                raise CheckFailed("the keyboard shows no %r; it shows %s" % (wanted, seen[:14]))
            time.sleep(0.8)

    def strip_until(self, condition, limit=6.0):
        """The words of the strip, read again until the condition holds or the time is over."""
        deadline = time.time() + limit
        while True:
            texts = [t for t in dump().strip_texts() if t not in STRIP_FIXED]
            if condition(texts) or time.time() > deadline:
                return texts
            time.sleep(0.8)

    def tap_id(self, name):
        node = dump().by_id(name)
        if node is None:
            foreground_info("%s missing" % name)
            raise DeviceError("%s is not on the screen" % name)
        tap(*node.center)

    def tap_text(self, label, swipes=6):
        """Taps the node that shows exactly this text, scrolling down for it if needed."""
        seen = []
        for attempt in range(swipes + 1):
            screen = dump()
            for node in screen.nodes:
                if node.visible and label in (node.text, node.desc):
                    tap(*node.center)
                    time.sleep(1.0)
                    return
            seen = [n.text for n in screen.nodes if n.text and n.package == KEYBOARD_PACKAGE]
            shell("input swipe 540 1700 540 700 400", check=False)
            time.sleep(0.8)
        raise CheckFailed("no %r on the screen; texts: %s" % (label, seen[:25]))

    def key_for(self, char):
        if char in self.keymap:
            return self.keymap[char]
        if char.isalpha():  # the same key carries the other case while the keyboard is shifted
            return self.keymap.get(char.swapcase())
        return None

    def key_list(self):
        return " ".join(sorted(self.keymap))[:400] or "(none)"

    # -- expectations

    def expect_equal(self, what, actual, expected):
        """Compares exactly."""
        self.checks += 1
        self.notes.append("%s: expected %r, got %r" % (what, expected, actual))
        if actual != expected:
            raise CheckFailed("%s: expected %r, got %r" % (what, expected, actual))

    def expect_field(self, field, expected, limit=6.0):
        """The field must reach the expected content. It is read again for a few seconds because the
        text arrives asynchronously; the comparison itself is exact."""
        deadline = time.time() + limit
        actual = self.field(field)
        while actual != expected and time.time() < deadline:
            time.sleep(0.7)
            actual = self.field(field)
        self.expect_equal("field " + field, actual, expected)

    # -- evidence

    def screenshot(self, label):
        path = os.path.join(self.out_dir, "%s-%s.png" % (self.name, label))
        try:
            data = adb("exec-out", "screencap", "-p", timeout=30, binary=True)
            if data[:4] == b"\x89PNG":
                with open(path, "wb") as out:
                    out.write(data)
                return True
        except DeviceError:
            pass
        return False

    def save_dump(self, screen, label):
        try:
            with open(os.path.join(self.out_dir, "%s-%s.json" % (self.name or "setup", label)), "w",
                      encoding="utf-8") as out:
                out.write(screen.raw)
        except OSError:
            pass


def describe(screen):
    """A short description of a screen for the log: what the keyboard and the test app show."""
    keyboard = screen.keyboard_nodes()
    packages = sorted({n.package for n in screen.nodes})
    parts = []
    for n in keyboard:
        label = n.cls.rsplit(".", 1)[-1]
        if n.rid:
            label += "#" + n.rid.rsplit("/", 1)[-1]
        if n.desc or n.text:
            label += "'%s'" % (n.desc or n.text)[:20]
        parts.append(label)
    return ("%d nodes of %s; keyboard nodes %d: %s" % (
        len(screen.nodes), ",".join(packages), len(keyboard), " ".join(parts)))[:1100]


def foreground_info(why):
    """INFO lines for a test app that is not in front (flaky runs of clipboard_pill and prompt_folders):
    the resumed activity and the last activity starts of the ActivityTaskManager, as component names only.
    Never a field content, clipboard text or intent extra."""
    try:
        text = shell("dumpsys activity activities", timeout=30, check=False)
        resumed = [re.sub(r"\s+", " ", l.strip())[:200] for l in text.splitlines()
                   if "ResumedActivity" in l or "topResumedActivity" in l][:3]
        log("INFO foreground (%s): %s" % (why, " | ".join(resumed) or "no resumed-activity line"))
        raw = adb("logcat", "-d", "-t", "3000", "ActivityTaskManager:I", "*:S", timeout=30, check=False)
        starts = []
        for l in raw.splitlines():
            if "START u0" in l:
                comp = re.search(r"cmp=(\S+)", l)
                caller = re.search(r"from uid (\d+)", l)
                starts.append("%s %s%s" % (l[:18].strip(), comp.group(1) if comp else "?",
                                           " uid " + caller.group(1) if caller else ""))
        log("INFO recent activity starts (%s): %s" % (why, " ; ".join(starts[-10:]) or "none"))
    except DeviceError as error:
        log("INFO foreground (%s): not readable: %s" % (why, error))


def tap(x, y):
    shell("input tap %d %d" % (x, y), timeout=30)
    time.sleep(0.15)


# -------------------------------------------------------------------------------------- scenarios
# Every scenario starts from a fresh test app. The expectations are what a user must see in the field.

def keyboard_is_secondhand(d):
    """Control: the keyboard on the screen is the installed SecondHand build, not another input method."""
    d.start("keyboard_is_secondhand")
    d.focus("text")
    d.expect_equal("current input method", shell("settings get secure default_input_method").split("/")[0],
                   KEYBOARD_PACKAGE)
    d.type("a")
    d.expect_field("text", "A")


def key_positions_reported(d):
    """The keyboard tells accessibility services where its keys are, and that is where they are drawn
    (a screen reader taps there). Before the fix of stage 73 they were reported up to a whole screen
    too high in some starts."""
    d.start("key_positions_reported")
    screen = d.focus("text")
    dx, dy = screen.shift
    d.expect_equal("keys reported where they are drawn (off by %d, %d px)" % (dx, dy),
                   abs(dx) <= 12 and abs(dy) <= 12, True)
    d.type("a")
    d.expect_field("text", "A")


def typing_text(d):
    """Typing in a normal text field; the first letter of the sentence is capitalised by the keyboard."""
    d.start("typing_text")
    d.focus("text")
    d.expect_field("text", "")
    d.type("hallo du")
    d.expect_field("text", "Hallo du")


def delete_key(d):
    """The delete key removes one character per tap."""
    d.start("delete_key")
    d.focus("text")
    d.type("hallo")
    d.expect_field("text", "Hallo")
    d.press("delete")
    d.press("delete")
    d.expect_field("text", "Hal")


def typing_password(d):
    """A password field takes exactly the typed characters: no capitalisation, no correction."""
    d.start("typing_password")
    d.focus("password")
    d.type("geheim")
    d.expect_field("password", "geheim")
    d.expect_field("text", "")


def typing_email(d):
    """An e-mail field offers @ and the dot and does not capitalise."""
    d.start("typing_email")
    d.focus("email")
    d.type("test@web.de")
    d.expect_field("email", "test@web.de")


def typing_number(d):
    """A number field shows the number pad."""
    d.start("typing_number")
    d.focus("number")
    d.type("12345")
    d.expect_field("number", "12345")


def cursor_trackpad(d):
    """The space bar moves the cursor like a trackpad (stage 87): a slow swipe to the left through
    "Hallo welt" crosses the start of "welt" and stops at word edges. The exact position depends on the
    finger's speed and the detents, so the check is a set: the new letter must land left of the space
    (the swipe crossed the word edge) and not before the second letter (no runaway). The control run
    fails because the typed letter is left out."""
    d.start("cursor_trackpad")
    d.focus("text")
    d.type("hallo welt")
    d.expect_field("text", "Hallo welt")
    d.drag_on("space", -100, 0)  # 7 steps of 13 dp without word edges, 4 to 5 with them
    d.type("x")
    time.sleep(1.0)
    text = d.field("text")
    d.expect_equal("the field without the new letter", text.replace("x", "").replace("X", ""), "Hallo welt")
    index = max(text.find("x"), text.find("X"))
    d.expect_equal("the new letter landed in 'Hallo', not after the space (index %d of %r)" % (index, text), 2 <= index <= 5, True)


def cursor_trackpad_vertical(d):
    """One vertical swipe on the space bar moves the cursor up in a multi-line field (stage 87):
    the keyboard sends DPAD_UP and the editor decides where it lands. The letter typed afterwards must
    be in the first line. The test app's text field holds two lines."""
    d.start("cursor_trackpad_vertical")
    d.focus("text")
    d.type("eins")
    d.press("enter")
    d.type("zwei")
    time.sleep(1.0)
    d.drag_on("space", 0, -70)  # the track starts at the first delivered sample (the 16 dp jump): 54 dp are left, two lines of 26 dp
    d.type("x")
    time.sleep(1.0)
    lines = d.field("text").replace("\r", "").split("\n")
    d.expect_equal("two lines with the letter in the first (%r)" % lines, len(lines) == 2 and "x" in lines[0].lower() and "x" not in lines[1].lower(), True)


def _open_settings_and_switch_on(d, path, switch):
    """Opens the keyboard's settings, follows the menu texts of the path and switches the named
    setting on (taps its row once, only if the dump does not already show it as checked)."""
    shell("am start -W -n %s/helium314.keyboard.settings.SettingsActivity" % KEYBOARD_PACKAGE, timeout=60)
    time.sleep(2.0)
    # The settings may open with a dialog over them: "Build N ist da" (the emulator has a network and
    # the test build has a low version number) or "Neu in Build N". Back closes it.
    for _ in range(2):
        texts = [n.text for n in dump().nodes if n.package == KEYBOARD_PACKAGE and n.text]
        if any(re.match(r"(Build \d+ ist da|Neu in Build \d+|Build \d+ is available|New in build \d+)", t) for t in texts):
            shell("input keyevent KEYCODE_BACK", check=False)
            time.sleep(1.5)
    # The settings come back where they were left: follow the path only if the setting is not in sight.
    if not any(switch in (n.text, n.desc) for n in dump().nodes if n.visible):
        for label in path:
            d.tap_text(label)
    d.tap_text(switch)
    shell("input keyevent KEYCODE_HOME", check=False)
    time.sleep(1.0)
    foreground_info("after Home")  # investigation of the flaky clipboard_pill: is the settings task left?


def emoji_suggestion(d):
    """Typing a word with an emoji offers it in the strip; a tap puts it into the field."""
    d.start("emoji_suggestion")
    d.focus("text")
    d.type("und")
    d.wait_for_suggestions()
    d.type(" geil")
    d.tap_in_strip(("🔥",))
    d.expect_field("text", "Und 🔥")  # the emoji replaces the typed word (seen in run 18)


def clipboard_pill(d):
    """With "Zwischenablagen-Inhalt vorschlagen" on, copied text is offered in the strip and a tap
    inserts it."""
    _open_settings_and_switch_on(d, ("Tippen & Korrektur", "Alle Korrektur-Einstellungen"),
                                 "Zwischenablagen-Inhalt vorschlagen")
    first, second, third = CLIP_TEXTS
    d.start("clipboard_pill")
    if not d.control:  # control run: nothing is copied, so the pill must be missing and the scenario fail
        d.tap_id("copy_1")  # the test app copies a text, as a user would copy it somewhere
    d.focus("text")
    d.tap_in_strip((first,))
    d.expect_field("text", first)


def clipboard_not_readable_in_password(d):
    """A copied text is offered again after an earlier pill was tapped (fixed in stage 73: without the
    history the first tapped pill used to be the last), and in a password field the strip never shows
    the copied text in the clear (by design the pill there shows stars)."""
    first, second, third = CLIP_TEXTS
    d.start("clipboard_not_readable_in_password")
    # A second, different text, then the password field: nothing may be offered there. (The keyboard
    # offers a copied text once; the same text again would be missing everywhere, run 21.)
    d.tap_id("copy_2")
    d.focus("password")
    time.sleep(2.5)
    offered = dump().strip_texts()
    d.expect_equal("copied text readable in the password field (strip %s)" % offered[:6],
                   any(second in text for text in offered), False)
    d.expect_field("password", "")
    # And a third text for an empty text field: only if the pill comes there, its absence in the
    # password field says something about the password field.
    d.start("clipboard_not_readable_in_password")
    if not d.control:  # control run: no third copy, the pill must be missing
        d.tap_id("copy_3")
    d.focus("text")
    d.tap_in_strip((third,))
    d.expect_field("text", third)


def prompt_store(d):
    """The prompt store (stage 74) at its real entry: hold the comma key, slide to the store's key,
    save the copied text as a prompt, tap the prompt: it is inserted. In a password field the popup
    of the comma key does not offer the store."""
    first, second, third = CLIP_TEXTS
    d.start("prompt_store")
    # control run: another text is copied, so the saved prompt is not the expected one (copying nothing
    # was not enough: the text of an earlier scenario was still on the clipboard, run 41)
    d.tap_id("copy_1" if d.control else "copy_2")
    d.focus("text")
    d.hold_and_choose(",", ("Ablage in der Komma-Taste",))
    try:
        d.tap_keyboard_text("Kopiertes in der Ablage speichern")
    except CheckFailed as error:
        later = dump()
        fresh = " ".join("%s#%s'%s'%s" % (n.cls.rsplit(".", 1)[-1][:10], n.rid.rsplit("/", 1)[-1][:16],
                                           (n.text or n.desc)[:12], n.box)
                         for n in later.keyboard_nodes() if n.visible and not n.cls.endswith(".Key")
                         and not n.cls.endswith("FrameLayout") and not n.cls.endswith("LinearLayout"))
        keyboxes = [n.box for n in later.keyboard_nodes() if n.cls.endswith(".Key") and n.visible][:2]
        raise CheckFailed("%s | hold: %s | now: %s | keys %s" % (
            str(error)[:60], d.hold_info[:200], fresh[:260], keyboxes))
    d.tap_keyboard_text(second)  # the new row shows the first line of its text
    d.expect_field("text", second)
    # the counter-check: the same popup in a password field
    d.focus("password")
    offered = sorted(d.popup_of(","))
    d.expect_equal("the store's key in the popup of a password field (popup %s)" % offered[:8],
                   any("ablage in der komma-taste" in name.lower() or "prompt" in name.lower() for name in offered), False)
    # Lifting the finger outside the popup may type the held key's own comma (run 36); anything else in
    # the password field, above all a prompt or the copied text, is a failure.
    left = d.field("password")
    d.expect_equal("the password field holds nothing but the held key's own comma (got %r; %s)"
                   % (left, d.hold_info[:300]), left in ("", ","), True)


def learning_not_in_password(d):
    """With "Personalisierte Vorschläge" on, a new word chosen three times is offered afterwards; a
    word typed into a password field is never offered."""
    _open_settings_and_switch_on(d, ("Tippen & Korrektur", "Alle Korrektur-Einstellungen"),
                                 "Personalisierte Vorschläge")
    word, secret = "xqzvok", "pwzzkq"
    d.start("learning_not_in_password")
    d.focus("text")
    d.type("und")
    d.wait_for_suggestions()
    d.type(" ")
    kept = "Und "
    for _ in range(3):
        # The way a user keeps an unknown word: type it with a space; if the keyboard changed it, one
        # tap on delete takes the correction back (stage 72), then the space again.
        d.type(word + " ")
        time.sleep(1.0)
        if not d.field("text").endswith(word + " "):
            d.press("delete")
            time.sleep(0.8)
            if d.field("text").endswith(word):
                d.press("space")
        kept += word + " "
    d.expect_field("text", kept)
    d.type(word[:4])
    offered = d.strip_until(lambda texts: word in texts)
    d.expect_equal("the chosen word is offered for %r (strip %s)" % (word[:3], offered[:5]), word in offered, True)

    d.start("learning_not_in_password")
    d.focus("password")
    d.type(secret)
    d.type(" ")
    d.type(secret)
    d.focus("text")
    d.type(secret[:4])
    time.sleep(2.5)
    offered = [t for t in dump().strip_texts() if t not in STRIP_FIXED]
    d.expect_equal("the password-field word is offered for %r (strip %s)" % (secret[:3], offered[:5]),
                   any(secret in text for text in offered), False)


def _typed_becomes(d, name, cases):
    """Types each text into a fresh text field, after the dictionary answered, and compares the field."""
    seen = []
    for typed, expected in cases:
        d.start(name)
        d.focus("text")
        d.type("und")
        d.wait_for_suggestions()
        # the word without its final space first: what the strip offers for it goes into the result line
        d.type(typed.rstrip(" "))
        seen.append("strip for %r: %s" % (typed.strip(), [t for t in dump().strip_texts() if t not in STRIP_FIXED][:4]))
        d.notes = seen + d.notes
        d.type(" " * (len(typed) - len(typed.rstrip(" "))))
        try:
            d.expect_field("text", "Und" + expected)
        except CheckFailed as error:
            raise CheckFailed("%s; %s" % (error, seen[-1]))


def capital_after_period(d):
    """After a full stop and a space the next word starts with a capital letter."""
    d.start("capital_after_period")
    d.focus("text")
    d.type("hallo. du")
    d.expect_field("text", "Hallo. Du")


def autocorrect_typo(d):
    """A typo of a very common word is corrected when the word ends: "idr" becomes "dir" (stage 71)."""
    _typed_becomes(d, "autocorrect_typo", [(" idr ", " dir ")])


def autocorrect_works(d):
    """Control for the scenarios around it: the real dictionary corrects a plain typo ("nciht")."""
    _typed_becomes(d, "autocorrect_works", [(" nciht ", " nicht ")])


def autocorrect_typo_in_sentence(d):
    """The same typo "idr" inside a sentence, where the words before it point to "dir"."""
    _typed_becomes(d, "autocorrect_typo_in_sentence", [(" wie geht es idr ", " wie geht es dir ")])


def swapped_letters_keep_nouns(d):
    """The swapped-letters rule (stage 75) leaves nouns alone: "ort", "rest", "titel" get their capital,
    they do not become "rot", "erst" or "title"."""
    _typed_becomes(d, "swapped_letters_keep_nouns", [(" ort ", " Ort "), (" rest ", " Rest "), (" titel ", " Titel ")])


def swapped_letters_keep_caps(d):
    """A capital inside the word stops the swapped-letters rule: "idR" stays."""
    _typed_becomes(d, "swapped_letters_keep_caps", [(" idR ", " idR ")])


def autocorrect_noun(d):
    """A common noun typed in lower case gets its capital: "jungs" becomes "Jungs" (stages 48, 71)."""
    _typed_becomes(d, "autocorrect_noun", [(" jungs ", " Jungs ")])


def slang_stays(d):
    """Informal words are not "corrected" (stage 71): two from the list, one stretched word."""
    _typed_becomes(d, "slang_stays", [(" gecovert ", " gecovert "), (" lowkey ", " lowkey "), (" neee ", " neee ")])


def language_switch(d):
    """The command @lang offers the active languages; choosing English gives the English layout."""
    d.start("language_switch")
    d.focus("text")
    d.expect_equal("German layout first (has the key ü)", "ü" in d.keymap or "Ü" in d.keymap, True)
    d.type("@lang")
    d.tap_in_strip(("Englisch", "English"))
    time.sleep(1.5)
    d.refresh_keys()
    d.expect_equal("English layout now (no key ü)", "ü" in d.keymap or "Ü" in d.keymap, False)
    d.expect_field("text", "")
    d.type("the")
    d.expect_field("text", "The")
    # back to German, so the scenarios after this one find the keyboard as the others did
    d.start("language_switch")
    d.focus("text")
    d.type("@lang")
    d.tap_in_strip(("Deutsch", "German"))
    time.sleep(1.5)
    d.refresh_keys()
    d.expect_equal("German layout again (has the key ü)", "ü" in d.keymap or "Ü" in d.keymap, True)


# ----------------------------------------------------------------------- investigation (autocorrect findings)
# Round "Untersuchung Autokorrektur" (they run only with --only / the workflow input `only`): the owner reports (1) German corrections in English sentences, (2) a lost
# apostrophe in "war's" / "I'm", (3) "i" never becoming "I", (4) a correct word turned into a non-word. These probes
# type the words with real touches and write what the keyboard did to the log (lines "PROBE ..."): the correction the
# space key announces BEFORE the space, the strip, and the field AFTER the space. They compare nothing about the
# keyboard's choice (the first run only records); the checks are of the probe itself.

def pending_correction(screen=None):
    """What the space key announces ("Mit Leertaste wird "x" in "y" geändert."), or "-" without a pending correction."""
    screen = screen or dump()
    for node in screen.nodes:
        if node.package == KEYBOARD_PACKAGE and node.cls.endswith(".Key") and node.desc \
                and any(p.match(node.desc) for p in AUTOCORRECT_WRAPPERS):
            return node.desc[:140]
    return "-"


def _ensure_layout(d, language):
    """Makes sure the keyboard shows the English ("en") or German ("de") layout: the command @lang offers the other
    active languages, so if the wanted one is not offered, the keyboard is already on it."""
    wanted = ("Englisch", "English") if language == "en" else ("Deutsch", "German")
    d.start("probe_language")
    d.focus("text")
    d.type("@lang")
    time.sleep(1.2)
    try:
        d.tap_in_strip(wanted)
        time.sleep(1.5)
    except CheckFailed:
        pass  # not offered: already on that language
    d.refresh_keys()
    d.start("probe_language")
    d.focus("text")
    has_umlaut = "ü" in d.keymap or "Ü" in d.keymap
    d.expect_equal("layout %s (umlaut key %s)" % (language, has_umlaut), has_umlaut, language == "de")


def _probe_cases(d, name, prefix, cases, english=False):
    """Each case in a fresh field: prefix, then the text of the case, the pending correction and the strip are read
    before the final space, the field after it. The layout (English or German) is set first and German is restored
    afterwards. Returns a list of (text, pending, strip, field)."""
    rows = []
    _ensure_layout(d, "en" if english else "de")
    try:
        for text in cases:
            d.start(name)
            d.focus("text")
            d.type(prefix)
            d.wait_for_suggestions()
            d.type(" " + text)
            time.sleep(1.2)
            screen = dump()
            pend = pending_correction(screen)
            strip = [t for t in screen.strip_texts() if t not in STRIP_FIXED][:5]
            d.type(" ")
            time.sleep(1.0)
            field = d.field("text")
            rows.append((text, pend, strip, field))
            log("PROBE %s | %s | typed %r | pending %s | strip %s | field after space %r" % (
                name, "EN layout" if english else "DE layout", prefix + " " + text, pend, strip, field))
            d.notes.append("%r -> %r" % (text, field))
    finally:
        if english:
            try:
                _ensure_layout(d, "de")
            except (CheckFailed, DeviceError) as error:
                log("PROBE could not restore the German layout: %s" % error)
    d.expect_equal("probe ran %d cases" % len(rows), len(rows), len(cases))
    return rows


def _expect_fields(d, rows, expected):
    """Compares the field after the space of each recorded case with what the keyboard does TODAY (the value is
    documented here, it is not a statement that it is right)."""
    got = {text: field for text, _pending, _strip, field in rows}
    for text, field in expected.items():
        d.expect_equal("%r gives" % text, got.get(text), field)


def reported_finding_2_apostrophe_de(d):
    """Reported finding 2, German layout, default settings, no history: words typed WITH an apostrophe stay as typed;
    typed without it ("wars") the keyboard does not add one ("ward"), only the English dictionary has such pairs."""
    rows = _probe_cases(d, "reported_finding_2_apostrophe_de", "und",
                        ["war's", "geht's", "gibt's", "ist's", "wie's", "wars", "gibts", "nichtmal"])
    _expect_fields(d, rows, {"war's": "Und war's ", "geht's": "Und geht's ", "gibt's": "Und gibt's ", "ist's": "Und ist's ",
                             "wie's": "Und wie's ", "wars": "Und ward ", "gibts": "Und gibts ", "nichtmal": "Und nicht mal "})


def reported_finding_2_apostrophe_en(d):
    """Reported finding 2, English layout, default settings: the keyboard adds the apostrophe for "im", "cant", "ive"
    (whitelist of the dictionary), keeps typed apostrophes, but turns "dont" into "font" and not into "don't"."""
    rows = _probe_cases(d, "reported_finding_2_apostrophe_en", "and",
                        ["i'm", "im", "don't", "dont", "it's", "its", "that's", "cant", "ive", "war's"], english=True)
    _expect_fields(d, rows, {"i'm": "And I'm ", "im": "And I'm ", "don't": "And don't ", "dont": "And font ", "it's": "And it's ",
                             "its": "And its ", "that's": "And that's ", "cant": "And can't ", "ive": "And I've ", "war's": "And war's "})


def reported_finding_3_capital_i(d):
    """Reported finding 3, default settings: on the English layout "i" in the middle of a sentence becomes "I"; on the
    German layout (no second language) it stays "i". (At the level Behutsam it also stays on the English layout, see
    reported_finding_3_level_gentle.)"""
    rows = _probe_cases(d, "reported_finding_3_capital_i_en", "and", ["i", "i think", "i am", "i'll"], english=True)
    _expect_fields(d, rows, {"i": "And I ", "i think": "And I think ", "i am": "And I am ", "i'll": "And I'll "})
    rows = _probe_cases(d, "reported_finding_3_capital_i_de", "und", ["i", "i think"])
    _expect_fields(d, rows, {"i": "Und i ", "i think": "Und i think "})


def reported_finding_1_english_sentences(d):
    """Reported finding 1: English sentences stay English on the English layout; on the German layout without a second
    language the German dictionary corrects English words ("name" -> "Name", "bank" -> "Bank", "new" -> "New")."""
    rows = _probe_cases(d, "reported_finding_1_english_en", "and",
                        ["this is a test of the keyboard", "what is your name", "i went to the bank", "my new job is fine",
                         "we should meet tomorrow", "thanks for the help"], english=True)
    _expect_fields(d, rows, {"this is a test of the keyboard": "And this is a test of the keyboard ",
                             "what is your name": "And what is your name ", "i went to the bank": "And I went to the bank ",
                             "my new job is fine": "And my new job is fine ", "we should meet tomorrow": "And we should meet tomorrow ",
                             "thanks for the help": "And thanks for the help "})
    rows = _probe_cases(d, "reported_finding_1_english_de", "und",
                        ["this is a test of the keyboard", "what is your name", "i went to the bank", "my new job is fine"])
    _expect_fields(d, rows, {"this is a test of the keyboard": "Und this is a test of the keyboard ",
                             "what is your name": "Und what is your Name ", "i went to the bank": "Und i went to the Bank ",
                             "my new job is fine": "Und my New job is fine "})


def _settings_fresh(d, path, wanted):
    """Opens the keyboard's settings in a cleared task (they would come back where they were left), follows the menu
    texts of the path and taps the first node whose text or description starts with `wanted` (any case)."""
    shell("am start -W --activity-clear-task -n %s/helium314.keyboard.settings.SettingsActivity" % KEYBOARD_PACKAGE, timeout=60)
    time.sleep(2.0)
    for _ in range(2):
        texts = [n.text for n in dump().nodes if n.package == KEYBOARD_PACKAGE and n.text]
        if any(re.match(r"(Build \d+ ist da|Neu in Build \d+|Build \d+ is available|New in build \d+)", t) for t in texts):
            shell("input keyevent KEYCODE_BACK", check=False)
            time.sleep(1.5)
    for label in path:
        d.tap_text(label)
    seen = []
    for _ in range(7):
        for node in dump().nodes:
            label = node.text or node.desc
            if node.visible and label and label.lower().startswith(wanted.lower()):
                tap(*node.center)
                time.sleep(1.0)
                shell("input keyevent KEYCODE_HOME", check=False)
                time.sleep(1.0)
                return label
        seen = [(n.text or n.desc)[:30] for n in dump().nodes if n.package == KEYBOARD_PACKAGE and (n.text or n.desc)]
        shell("input swipe 540 1700 540 700 400", check=False)
        time.sleep(0.8)
    raise CheckFailed("no text starting with %r on the screen; texts: %s" % (wanted, seen[:25]))


def _keep_typed(d, word, times):
    """Types the word with a space `times` times the way the owner keeps a word: if the keyboard changed it,
    one tap on delete takes the correction back, then the space again (as learning_not_in_password)."""
    for _ in range(times):
        d.type(word + " ")
        time.sleep(1.0)
        if not d.field("text").endswith(word + " "):
            d.press("delete")
            time.sleep(0.8)
            if d.field("text").endswith(word):
                d.press("space")


def reported_finding_2_4_history_loop(d):
    """Findings 2 and 4: with "Personalisierte Vorschläge" on, a typo the owner once kept ("nicbmal", "war'a") sits
    in the typing history (German layout). Does the correct word ("nichtmal", "war's") then become that typo?"""
    _settings_fresh(d, ("Tippen & Korrektur", "Alle Korrektur-Einstellungen"), "Personalisierte Vorschläge")
    _ensure_layout(d, "de")
    d.start("probe_history_loop")
    d.focus("text")
    d.type("und")
    d.wait_for_suggestions()
    d.type(" ")
    for typo in ("nicbmal", "war'a"):
        _keep_typed(d, typo, 2)
    log("PROBE probe_history_loop | after keeping the typos twice: field %r" % d.field("text"))
    rows = _probe_cases(d, "reported_finding_2_4_history_loop", "und", ["nichtmal", "war's", "nicbmal", "war'a"])
    # today: the correct "war's" becomes the kept typo "war'a"; "nichtmal" becomes "nicht mal" (the history word "nicbmal"
    # is offered in the strip but the engine's own two-word correction ranks first, with this little history)
    _expect_fields(d, rows, {"war's": "Und war'a ", "nichtmal": "Und nicht mal ", "nicbmal": "Und nicbmal ", "war'a": "Und war'a "})


def reported_finding_3_level_gentle(d):
    """Reported finding 3 at the level "Behutsam" (Tippen & Korrektur, "Richtige Wörter nie ändern"): since stage 101 "i"
    in the middle of a sentence becomes "I" on the English layout (sentence context on, the default); "cant" stays, "im"
    and "i'll" are still corrected."""
    label = _settings_fresh(d, ("Tippen & Korrektur",), "Behutsam")
    d.notes.append("tapped %r" % label)
    rows = _probe_cases(d, "reported_finding_3_level_gentle_en", "and", ["i", "i'll", "im", "cant", "dont"], english=True)
    _expect_fields(d, rows, {"i": "And I ", "i'll": "And I'll ", "im": "And I'm ", "cant": "And cant ", "dont": "And font "})

def _strip_signature(screen):
    """The nodes inside the suggestion strip (of any package, text or not), as (class, id, text) -> Node.
    Inline autofill chips are remote surfaces whose text is probably not readable, so they are found by
    what appears in the strip, not by what it says."""
    strip = screen.keyboard_node("suggestion_strip_view")
    found = {}
    if strip is None:
        return strip, found
    for node in screen.nodes:
        if node.visible and node is not strip and strip.contains(*node.center):
            found.setdefault((node.cls.rsplit(".", 1)[-1], node.rid.rsplit("/", 1)[-1], (node.text or node.desc)[:20]), node)
    return strip, found


def _strip_report(strip, found):
    return "strip %s: %s" % (strip.box if strip else "not found", " ".join(
        "%s#%s'%s'%s%s" % (c, i, t, n.box, "" if n.package == KEYBOARD_PACKAGE else "(" + n.package + ")")
        for (c, i, t), n in found.items())[:700] or "no node")


def _chip_titles(screen, strip):
    """Stage 79: the titles of the inline autofill chips in the strip, left to right (by the title nodes' position)."""
    if strip is None:
        return []
    titles = [n for n in screen.nodes if n.visible and n.rid.rsplit("/", 1)[-1] == AUTOFILL_TITLE_ID
              and (n.text or n.desc) and strip.contains(*n.center)]
    return [n.text or n.desc for n in sorted(titles, key=lambda n: n.center[0])]


def _leftmost_chip_view(screen, strip):
    """Stage 79: the left-most inline chip view in the strip (the signature of the strip keeps only one of equal nodes)."""
    if strip is None:
        return None
    views = [n for n in screen.nodes if n.visible and "InlineContentView" in n.cls and n.area > 0 and strip.contains(*n.center)]
    return min(views, key=lambda n: n.left) if views else None


def _autofill_state():
    """Short facts about the system's autofill for a failed run (the lead only sees RESULT lines)."""
    service = shell("settings get secure autofill_service", check=False)
    text = shell("dumpsys autofill", check=False, timeout=40)
    lines = [l.strip() for l in text.splitlines() if re.search(r"(?i)autofill service|enabled|disabled|inline|sessions", l)]
    return "autofill_service=%s; dumpsys autofill: %s" % (service, " | ".join(lines)[:500])


AUTOFILL_LOG_TAGS = ("InlineSuggestionRenderService", "RemoteInlineSuggestionUi", "InlineContentProviderImpl",
                     "AutofillInlineSuggestionsRequestSession", "InlineSuggestionSession", "AutofillSession")


def _keyboard_dump_line(prefix):
    """Stage 79b: the line of LatinIME.dump() that starts with `prefix`. The dump of an input method service is
    printed by `dumpsys input_method` (the system forwards the dump request to the current input method); the
    second command asks the running service directly. Fake data and numbers only."""
    ime = shell("settings get secure default_input_method", check=False)  # package/class, as verified by the preflight
    tried = []
    for command in ("dumpsys input_method", "dumpsys activity service " + ime):
        try:
            text = shell(command, timeout=40, check=False)
        except DeviceError as error:
            tried.append("%s: %s" % (command, error))
            continue
        for line in text.splitlines():
            if line.strip().startswith(prefix):
                return "%s -> %s" % (command, line.strip()[:400])
        tried.append("%s: no line '%s' (%d lines)" % (command, prefix, len(text.splitlines())))
    return " | ".join(tried)[:500]


def _autofill_failure_info(d):
    """Stage 79b: facts for a failed autofill scenario, printed as INFO lines only (the lead reads RESULT/INFO lines).
    Never changes the verdict and never raises."""
    try:
        log("INFO autofill diagnostic, keyboard counters: " + _keyboard_dump_line("InlineAutofill:"))
    except Exception as error:
        log("INFO autofill diagnostic, keyboard counters failed: %s" % error)
    try:
        log("INFO autofill diagnostic, ext.services pid: %r" % shell("pidof com.google.android.ext.services", check=False))
    except Exception as error:
        log("INFO autofill diagnostic, pidof failed: %s" % error)
    try:
        text = shell("logcat -d -t 400", timeout=40, check=False)
        lines = [l.strip()[:200] for l in text.splitlines() if any(tag in l for tag in AUTOFILL_LOG_TAGS)]
        log("INFO autofill diagnostic, logcat (%d matching lines of the last 400, last 25 shown):" % len(lines))
        for line in lines[-25:]:
            log("INFO   | " + line)
    except Exception as error:
        log("INFO autofill diagnostic, logcat failed: %s" % error)
    # information only, never a pass: leave the field and focus it again once, then count the chips
    try:
        d.focus("secret")
        d.focus("login")
        deadline, titles = time.time() + 8, []
        while time.time() < deadline:
            screen = dump()
            strip, _ = _strip_signature(screen)
            titles = _chip_titles(screen, strip)
            if len(titles) >= len(AUTOFILL_CHIP_TITLES):
                break
            time.sleep(1.0)
        log("INFO autofill diagnostic, after refocusing the field once: %d chips (expected %d)" % (len(titles), len(AUTOFILL_CHIP_TITLES)))
        log("INFO autofill diagnostic, keyboard counters after the refocus: " + _keyboard_dump_line("InlineAutofill:"))
    except Exception as error:
        log("INFO autofill diagnostic, refocus failed: %s" % error)


def autofill_inline(d):
    """Inline autofill (stage 73c): with the test app's fake autofill service selected, focusing a user
    name field with autofill hints shows a suggestion chip in the keyboard's strip; a tap puts the fake
    user name and password into the fields. The chip is a remote surface: it is found by the node that
    appears in the strip (compared with the strip before the service was selected) and tapped by
    coordinates; the fields prove it. The service is selected only here and unset afterwards."""
    shell("settings delete secure autofill_service", check=False)
    try:
        # (1) no autofill service: the field with hints shows no such node and stays empty
        d.start("autofill_inline")
        d.focus("login")
        time.sleep(4.0)
        before_screen = dump()
        strip, before = _strip_signature(before_screen)
        surfaces = [k for k in before if any(w in k[0] for w in ("Surface", "InlineContent"))]
        log("INFO autofill, no service: " + _strip_report(strip, before))
        d.expect_equal("strip nodes of an inline suggestion without an autofill service %s" % surfaces, surfaces, [])
        d.expect_equal("login field without an autofill service", d.field("login"), "")

        # (2) the service selected, a fresh activity (the autofill session starts when the field is focused).
        # Stage 79b: the system's autofill logs verbosely from here on (reset in the finally block), the log buffer is
        # emptied so a failed run reads only this step; all of it only feeds the INFO lines of a failure.
        shell("cmd autofill set log_level verbose", check=False)
        shell("logcat -c", check=False)
        shell("settings put secure autofill_service " + AUTOFILL_SERVICE)
        time.sleep(2.0)
        try:
            _autofill_inline_with_service(d, before)
        except (CheckFailed, DeviceError):
            if not d.control:  # the control run fails on purpose: nothing to explain
                _autofill_failure_info(d)
            raise
    finally:
        shell("cmd autofill set log_level off", check=False)
        shell("settings delete secure autofill_service", check=False)


def _autofill_inline_with_service(d, before):
    """Step 2 of autofill_inline: the service selected, the three chips expected, a tap on the first one."""
    d.start("autofill_inline")
    d.focus("login")
    deadline = time.time() + 20
    appeared = {}
    while True:
        strip, now = _strip_signature(dump())
        appeared = {k: n for k, n in now.items() if k not in before}
        if appeared or time.time() > deadline:
            break
        time.sleep(1.0)
    report = _strip_report(strip, now)
    log("INFO autofill, service set: " + report)
    log("INFO autofill state: " + _autofill_state())
    d.notes.append("new in the strip: %s" % sorted(appeared)[:8])

    # Stage 79: the service offers three datasets in a fixed order; the chips are inflated one by one and may
    # finish in any order, but the strip must show them left to right in the service's order.
    titles, title_start = [], time.time()
    title_deadline, trace = title_start + 12, []
    while True:
        screen = dump()
        strip, now = _strip_signature(screen)
        titles = _chip_titles(screen, strip)
        trace.append("%d@%.1fs" % (len(titles), time.time() - title_start))  # a slow third chip vs. a missing one
        if len(titles) >= len(AUTOFILL_CHIP_TITLES) or time.time() > title_deadline:
            break
        time.sleep(1.0)
    log("INFO autofill chip titles left to right: %s; visible titles per poll: %s" % (titles, " ".join(trace)))
    d.expect_equal("order of the three inline autofill chips, left to right (polls: %s; %s)" % (" ".join(trace)[-120:], report[:150]),
                   titles, AUTOFILL_CHIP_TITLES)

    # Where to tap: the chip view itself if it is a node, a node that reads the chip's title, or the
    # left end of the chip row (the chip is at least 100 px wide and starts at the row's left edge).
    target, how = None, ""
    first_chip = _leftmost_chip_view(screen, strip)  # stage 79: with three chips, tap the first one
    if first_chip is not None:
        target, how = first_chip.center, "left-most chip view " + first_chip.box
    if target is None:
        for key, node in appeared.items():
            if key[2] == AUTOFILL_CHIP_TITLES[0]:
                target, how = node.center, "chip text " + node.box
                break
    if target is None:
        rows = [n for k, n in appeared.items() if "HorizontalScroll" in k[0] and n.area > 0]
        if rows:
            target, how = (rows[0].left + 60, rows[0].center[1]), "left end of the chip row " + rows[0].box
        elif strip is not None:
            target, how = (strip.left + 60, strip.center[1]), "BLIND, left end of the strip " + strip.box
    if target is None:
        raise CheckFailed("no suggestion strip on the screen; " + report)
    d.notes.append("tap at %s (%s)" % (target, how))
    if not d.control:  # control run: the chip is not tapped, so the fields stay empty and the scenario must fail
        tap(*target)
    try:
        d.expect_field("login", FAKE_USER, limit=8.0)
    except CheckFailed as error:
        raise CheckFailed("%s | tap at %s (%s) | %s" % (error, target, how, report[:230]))
    d.expect_field("secret", FAKE_PASSWORD)


FOLDER_NAME = "Arbeit73"  # stage 77: typeable with shift and the symbols layout, and no word the keyboard corrects
FOLDER_CHIP_DESCRIPTIONS = {  # the descriptions of the filter chips (agent_prompt_strings.xml:59-61)
    "all": "Alle Einträge zeigen", "none": "Einträge ohne Ordner zeigen", "folder": "Ordner %s zeigen"}
ROW_PREFIX = "Eintrag einfügen: "  # description of a prompt row in the window (agent_prompt_strings.xml:47)


def _kb_labels(screen):
    """The texts and descriptions of the visible nodes of the keyboard app (settings page or window), no keys."""
    out = []
    for n in screen.nodes:
        if n.package == KEYBOARD_PACKAGE and n.visible and not n.cls.endswith(".Key"):
            for label in (n.text, n.desc):
                if label and label not in out:
                    out.append(label)
    return out


def _find_labeled(screen, wanted):
    """The first visible non-key node of the keyboard app whose text or description is exactly one of the wanted."""
    for n in screen.nodes:
        if n.package == KEYBOARD_PACKAGE and n.visible and not n.cls.endswith(".Key") \
                and (n.text in wanted or n.desc in wanted):
            return n
    return None


def _tap_labeled(wanted, what, limit=8.0):
    """Taps the node with exactly one of these labels; waits for it up to the limit."""
    deadline = time.time() + limit
    while True:
        screen = dump()
        node = _find_labeled(screen, wanted)
        if node is not None:
            tap(*node.center)
            time.sleep(1.0)
            return node
        if time.time() > deadline:
            raise CheckFailed("no %s (%s) on the screen; it shows %s" % (what, " / ".join(wanted), _kb_labels(screen)[:24]))
        time.sleep(0.8)


def _window_rows(screen):
    """The labels of the prompt rows the store window shows now, in screen order."""
    return [n.desc[len(ROW_PREFIX):] for n in screen.nodes
            if n.package == KEYBOARD_PACKAGE and n.visible and n.desc.startswith(ROW_PREFIX)]


SCAN_TRACE = []  # diagnostics: what each swipe of the row scan did (shown when a later tap fails)


def _swipe_list(screen, up):
    """Scrolls the window's list by one swipe (the window shows only two or three rows)."""
    save = _find_labeled(screen, ("Kopiertes in der Ablage speichern",))
    rows = [n for n in screen.nodes if n.package == KEYBOARD_PACKAGE and n.visible and n.desc.startswith(ROW_PREFIX)]
    if save is None or not rows:
        return
    x, low = save.center[0], max(n.bottom for n in rows)
    # A list that fits does not scroll, and a swipe over a row of it is taken as a tap on the row (run 47:
    # the row was inserted and the window closed). Swipe only when another row would not fit any more.
    views = [n for n in screen.nodes if n.package == KEYBOARD_PACKAGE and n.cls.endswith("ScrollView")
             and not n.cls.endswith("HorizontalScrollView") and n.visible and n.contains(x, save.center[1])]
    bottom = min((n.bottom for n in views), default=None)
    if bottom is None:
        frames = [n.bottom for n in screen.nodes if n.package == KEYBOARD_PACKAGE and n.rid.endswith("main_keyboard_frame")]
        bottom = frames[0] if frames else 0
    height = max(n.bottom - n.top for n in rows)
    if low + height + 8 <= bottom:
        SCAN_TRACE.append("no swipe: rows end %d, view end %d (%s), row %d" % (low, bottom, "scrollview" if views else "frame", height))
        return
    top = save.bottom + 8
    start, end = (low - 6, top) if up else (top, low - 6)
    shell("input swipe %d %d %d %d 400" % (x, start, x, end), check=False)
    SCAN_TRACE.append("%s %d>%d(save %s rows %s)" % ("up" if up else "dn", start, end, save.box,
                                                      [n.box for n in rows][:3]))
    time.sleep(0.8)


def _all_rows(first=None):
    """The labels of all rows of the window, scrolling down through the list and back to its top."""
    screen = first or dump()
    seen = list(_window_rows(screen))
    steps = 0
    for _ in range(5):
        _swipe_list(screen, True)
        steps += 1
        screen = dump()
        fresh = [r for r in _window_rows(screen) if r not in seen]
        if not fresh:
            break
        seen += fresh
    for _ in range(steps + 1):
        _swipe_list(screen, False)
        screen = dump()
    return seen


def _rows_until(condition, limit=8.0):
    """All rows of the window, read again until the condition holds on them or the time is over."""
    deadline = time.time() + limit
    while True:
        rows = _all_rows()
        if condition(rows) or time.time() > deadline:
            return rows
        time.sleep(0.8)


def _open_prompt_store_window(d):
    """Holds the comma key and slides to the store's key (the real entry); waits for the window's save row."""
    d.hold_and_choose(",", ("Ablage in der Komma-Taste",))
    d.wait(lambda s: _find_labeled(s, ("Kopiertes in der Ablage speichern",)) is not None, "the store window", limit=10.0)


def _open_prompt_page(d):
    """Opens the keyboard's settings from scratch (a fresh task) and goes to Leiste & Befehle -> Ablage (until build 36 "Prompt-Ablage")."""
    shell("am start -W --activity-clear-task -n %s/helium314.keyboard.settings.SettingsActivity" % KEYBOARD_PACKAGE, timeout=60)
    time.sleep(2.0)
    for _ in range(2):  # the update dialogs, as in _open_settings_and_switch_on
        texts = [n.text for n in dump().nodes if n.package == KEYBOARD_PACKAGE and n.text]
        if any(re.match(r"(Build \d+ ist da|Neu in Build \d+|Build \d+ is available|New in build \d+)", t) for t in texts):
            shell("input keyevent KEYCODE_BACK", check=False)
            time.sleep(1.5)
    d.tap_text("Leiste & Befehle")
    d.tap_text("Ablage")
    d.wait(lambda s: _find_labeled(s, ("Neuer Ordner",)) is not None, "the prompt store page (button Neuer Ordner)")


def prompt_folders(d):
    """Folders of the prompt store (stage 77) through the real entries: a folder created on the settings
    page shows as a chip in the store window; "Kopiertes in der Ablage speichern" under that chip saves into
    the folder and a tap on the row inserts the text; the chip filters (a prompt saved under "Alle" is
    not listed under the folder, the folder's prompt is listed under "Alle"). The text copied for the
    folder's prompt is text 2 (control run: text 1, so the row is another one)."""
    first, second, third = CLIP_TEXTS
    folder_chip = FOLDER_CHIP_DESCRIPTIONS["folder"] % FOLDER_NAME
    all_chip, none_chip = FOLDER_CHIP_DESCRIPTIONS["all"], FOLDER_CHIP_DESCRIPTIONS["none"]

    # (1) the settings page: a folder with a fixed name through "Neuer Ordner"
    _open_prompt_page(d)
    if _find_labeled(dump(), (folder_chip, FOLDER_NAME)) is None:  # a folder of an earlier run is kept
        d.tap_text("Neuer Ordner")
        deadline = time.time() + 10
        while True:
            screen = dump()
            field = next((n for n in screen.nodes if n.package == KEYBOARD_PACKAGE and n.visible
                          and n.cls.endswith("EditText")), None)
            if field is not None or time.time() > deadline:
                break
            time.sleep(0.8)
        if field is None:
            raise CheckFailed("no text field for the folder name; the page shows %s" % _kb_labels(screen)[:24])
        tap(*field.center)
        d.keymap = d.wait(lambda s: len(s.keys()) >= 10, "the keyboard for the folder name").keys()
        control, d.control = d.control, False  # the name is setup, the comparisons come later
        try:
            d.type(FOLDER_NAME)
        finally:
            d.control = control
        d.tap_text("Speichern")
    deadline = time.time() + 10
    while True:
        screen = dump()
        chip = _find_labeled(screen, (folder_chip, FOLDER_NAME))
        if chip is not None or time.time() > deadline:
            break
        time.sleep(0.8)
    d.expect_equal("the folder chip %r on the settings page (page shows %s)" % (FOLDER_NAME, _kb_labels(screen)[:20]),
                   chip is not None, True)
    shell("input keyevent KEYCODE_HOME", check=False)

    # (2) the window: chips "Alle" and the folder; save the copied text under the folder chip
    d.start("prompt_folders")
    d.tap_id("copy_1" if d.control else "copy_2")
    d.focus("text")
    _open_prompt_store_window(d)
    screen = dump()
    shown = _kb_labels(screen)
    d.expect_equal("the window's chips: Alle and the folder (window shows %s)" % shown[:16],
                   (all_chip in shown) and (folder_chip in shown), True)
    _tap_labeled((folder_chip,), "folder chip of the window")
    time.sleep(0.8)
    before = _rows_until(lambda rows: True, limit=0.1)
    _tap_labeled(("Kopiertes in der Ablage speichern",), "save row")
    in_folder = _rows_until(lambda rows: second in rows)
    d.expect_equal("rows under the folder chip after saving (before: %s)" % before, sorted(set(in_folder) - set(before)),
                   [second])
    try:
        _tap_labeled((ROW_PREFIX + second,), "the new row")
    except CheckFailed as error:
        late = dump()
        raise CheckFailed("%s | field text %r | window save row %s | scan %s" % (
            str(error)[:200], d.field("text"), _find_labeled(late, ("Kopiertes in der Ablage speichern",)) is not None,
            "; ".join(SCAN_TRACE[-6:])[:420]))
    d.expect_field("text", second)

    # (3) the filter: a second prompt without folder, saved under "Alle"
    if _find_labeled(dump(), ("Kopiertes in der Ablage speichern",)) is None:  # a pick closes the window
        _open_prompt_store_window(d)
    _tap_labeled((all_chip,), "chip Alle")
    d.tap_id("copy_3")
    _tap_labeled(("Kopiertes in der Ablage speichern",), "save row")
    everything = _rows_until(lambda rows: third in rows)
    d.expect_equal("under Alle the prompt saved there is listed (rows %s)" % everything[:6], third in everything, True)
    d.expect_equal("under Alle the folder's prompt is listed too (rows %s)" % everything[:6], second in everything, True)
    _tap_labeled((folder_chip,), "folder chip of the window")
    filtered = _rows_until(lambda rows: third not in rows and second in rows)
    d.expect_equal("under the folder chip only the folder's prompt, not the one saved under Alle (rows %s)"
                   % filtered[:6], (second in filtered, third in filtered), (True, False))
    shown = _kb_labels(dump())
    d.expect_equal("the chip row now offers Ohne Ordner (window shows %s)" % shown[:16], none_chip in shown, True)
    _tap_labeled((none_chip,), "chip Ohne Ordner")
    loose = _rows_until(lambda rows: third in rows)
    d.expect_equal("under Ohne Ordner the prompt saved under Alle is listed (rows %s)" % loose[:6], third in loose, True)
    # leave the window on "Alle" (the chip is remembered while the keyboard runs) and close it
    _tap_labeled((all_chip,), "chip Alle")
    try:
        _tap_labeled(("Schließen",), "close button of the window", limit=4.0)
    except CheckFailed:
        pass  # the next scenario starts a fresh app; the window is gone with the field



# ------------------------------------------------------------------ stage 88: the bar, with real touches and pictures
# The tools of the fixed bar are views with the resource id agent_bar_tool and a description (the tool's name, for a tool
# that is off in the field "<name>. <reason>"). Customizing is done with the keyboard's own touch handling, so these
# scenarios use real touches (`input motionevent DOWN/MOVE/UP` with waits), read the order of the slots left to right
# from the accessibility dump, and check that it persisted after hiding and reopening the keyboard.

BAR_TOOL_ID = KEYBOARD_PACKAGE + ":id/agent_bar_tool"
LEFT_KEY_ID = KEYBOARD_PACKAGE + ":id/suggestions_strip_toolbar_key"
# stage 99: the circle of the last microphone tool beside the words (a second button at the same place); not a slot of the tool row
END_CIRCLE_ID = KEYBOARD_PACKAGE + ":id/agent_bar_end_circle"
MIC_LABELS = ("Spracheingabe", "Voice input", "Diktieren", "Dictate")
GRID_NODE_IDS = {"hint": "agent_tool_grid_hint", "undo": "agent_tool_grid_undo", "reset": "agent_tool_grid_reset",
                 "reset_yes": "agent_tool_grid_reset_yes", "reset_no": "agent_tool_grid_reset_no"}
EDIT_LABELS = ("Anpassen", "Customize")
IN_BAR_SUFFIXES = (", in der Leiste", ", in the bar")
SHOT_CHUNK = 4000
SHOT_MAX_WIDTH = 300
SHOTS = []  # (name, [lines]) collected during the run and written to <out>/shots.txt at its end


def bar_slots(screen):
    """The tool slots of the bar, left to right: the views with the id agent_bar_tool; if the dump does not carry that id,
    the image buttons inside the strip other than the left key."""
    found = [n for n in screen.nodes if n.rid == BAR_TOOL_ID and n.visible]
    if not found:
        strip = screen.keyboard_node("suggestion_strip_view")
        if strip is not None:
            found = [n for n in screen.nodes if n.package == KEYBOARD_PACKAGE and n.visible and n.cls.endswith("ImageButton")
                     and n.rid != LEFT_KEY_ID and n.rid != END_CIRCLE_ID and n.desc and strip.left <= n.center[0] < strip.right
                     and strip.top <= n.center[1] < strip.bottom]
    return sorted(found, key=lambda n: n.left)


def bar_order(screen):
    return [n.desc for n in bar_slots(screen)]


def left_key(screen):
    for n in screen.nodes:
        if n.rid == LEFT_KEY_ID and n.visible:
            return n
    return None


def grid_node(screen, name):
    return screen.keyboard_node(GRID_NODE_IDS[name])


def grid_pills(screen):
    """The pills of the grid below the strip: (text, node), marked ones (tools in the bar) last."""
    strip = screen.keyboard_node("suggestion_strip_view")
    top = strip.bottom if strip else 0
    pills = [n for n in screen.nodes if n.package == KEYBOARD_PACKAGE and n.visible and n.cls.endswith("Button")
             and n.text and n.top >= top and n.rid == ""]
    return pills


def first_free_pill(screen):
    for n in grid_pills(screen):
        if not n.desc.endswith(IN_BAR_SUFFIXES) and n.text not in EDIT_LABELS:
            return n
    return None


def touch_down(x, y):
    shell("input motionevent DOWN %d %d" % (x, y), timeout=30)


def touch_move(x, y):
    shell("input motionevent MOVE %d %d" % (x, y), timeout=30)


def touch_up(x, y):
    shell("input motionevent UP %d %d" % (x, y), timeout=30, check=False)


def glide(start, end, steps=6):
    """Moves a finger that is down from start to end in steps; returns the end."""
    for i in range(1, steps + 1):
        touch_move(int(start[0] + (end[0] - start[0]) * i / steps), int(start[1] + (end[1] - start[1]) * i / steps))
        time.sleep(0.08)
    return end


def bar_report(screen):
    """What the strip and the grid show, for a failure message: class#id'description or text'[box], strip first."""
    strip = screen.keyboard_node("suggestion_strip_view")
    parts = []
    for n in screen.nodes:
        if n.package != KEYBOARD_PACKAGE or n.cls.endswith(".Key") or not n.visible:
            continue
        inside = strip is not None and strip.left <= n.center[0] < strip.right and strip.top <= n.center[1] < strip.bottom
        label = (n.desc or n.text)[:24]
        if inside or label:
            parts.append("%s%s%s%s%s" % ("S:" if inside else "", n.cls.rsplit(".", 1)[-1][:14], "#" + n.rid.rsplit("/", 1)[-1] if n.rid else "",
                                         "'%s'" % label if label else "", n.box))
    return " ".join(parts)[:2400]


def wait_report(d, condition, what, limit=15.0):
    """Like Driver.wait, but a timeout is a failed check that says what the keyboard showed."""
    deadline = time.time() + limit
    while True:
        screen = dump()
        if condition(screen):
            return screen
        if time.time() > deadline:
            foreground_info("waited for " + what)  # which activity is in front (stage 87c diagnosis)
            raise CheckFailed("waited %ds for %s; the keyboard shows: %s" % (int(limit), what, bar_report(screen)))
        time.sleep(0.7)


SHOW_TOOLS_LABELS = ("Werkzeuge zeigen", "Show tools")


def ensure_tools(d):
    """The strip shows the tools (after typing, or with a clipboard chip, it shows content and the left key says "Werkzeuge zeigen")."""
    for _ in range(3):
        screen = dump()
        key = left_key(screen)
        if key is not None and key.desc in SHOW_TOOLS_LABELS:
            tap(*key.center)
            time.sleep(0.8)
        else:
            return screen
    return dump()


def _has_edit_button(screen):
    return any(n.text in EDIT_LABELS for n in screen.nodes if n.package == KEYBOARD_PACKAGE and n.visible)


def open_edit_mode(d):
    """Opens the grid with the left key and the edit mode with its button; waits for the hint. If the tap does not open
    the grid it is tried again and then as a long press (which starts the edit mode itself); what happened is in the failure."""
    ensure_tools(d)
    tried = []
    screen = dump()
    for attempt in ("tap", "tap", "long press"):
        key = left_key(screen)
        if key is None:
            foreground_info("no left key of the bar")
            raise CheckFailed("no left key of the bar; the keyboard shows: " + bar_report(screen))
        if attempt == "tap":
            tap(*key.center)
        else:
            shell("input swipe %d %d %d %d 800" % (key.center[0], key.center[1], key.center[0], key.center[1]), check=False)
        time.sleep(1.2)
        screen = dump()
        tried.append("%s at %s -> left key %r, button %s, hint %s" % (attempt, key.center, getattr(left_key(screen), "desc", None),
                                                                  _has_edit_button(screen), grid_node(screen, "hint") is not None))
        if grid_node(screen, "hint") is not None:
            return screen
        if _has_edit_button(screen):
            break
    else:
        foreground_info("the grid did not open")
        crash = shell("logcat -d -t 300 -s AndroidRuntime:E DEBUG:F", check=False)[-700:].replace("\n", " | ")
        raise CheckFailed("the grid did not open: %s; log: %s; the keyboard shows: %s" % ("; ".join(tried), crash, bar_report(screen)))
    tap(*next(n for n in screen.nodes if n.package == KEYBOARD_PACKAGE and n.visible and n.text in EDIT_LABELS).center)
    return wait_report(d, lambda s: grid_node(s, "hint") is not None, "the edit mode with its hint (%s)" % "; ".join(tried))


def finish_edit_mode(d):
    """The check button (the left key in the edit mode); waits until the grid has closed."""
    key = left_key(dump())
    if key is not None:
        tap(*key.center)
    return d.wait(lambda s: grid_node(s, "hint") is None and left_key(s) is not None, "the edit mode to end")


def reset_bar(d):
    """Zurücksetzen with its question and Ja, all inside the edit view, then the check button."""
    open_edit_mode(d)
    tap(*grid_node(dump(), "reset").center)
    screen = d.wait(lambda s: grid_node(s, "reset_yes") is not None, "the question of the reset")
    tap(*grid_node(screen, "reset_yes").center)
    time.sleep(0.6)
    finish_edit_mode(d)


def hide_and_reopen(d, field="text"):
    """The keyboard goes away (back) and comes back by a tap into the field; what was saved must still be there."""
    shell("input keyevent KEYCODE_BACK", check=False)
    d.wait(lambda s: len(s.keys()) < 10, "the keyboard to hide")
    time.sleep(0.5)
    d.focus(field)
    time.sleep(0.6)
    return ensure_tools(d)


def screen_raw():
    """The screen as RGBA pixels (screencap without -p: no PNG filtering, so it is cheap to cut and shrink). Returns (w, h, bytes) or None."""
    try:
        data = adb("exec-out", "screencap", timeout=40, binary=True)
        w, h, fmt = struct.unpack("<III", data[:12])
        for header in (12, 16):
            if len(data) - header == w * h * 4:
                return w, h, data[header:]
    except (DeviceError, struct.error):
        pass
    return None


def png_of(w, h, pixels, top, max_width=SHOT_MAX_WIDTH, bottom=None, levels=0xF0):
    """A PNG (stdlib only) of the rows from top down to bottom, shrunk by dropping pixels so that it is at most max_width
    wide; the colours are cut to 4 bits per channel so that it is small (it goes through the job log as text)."""
    factor = max(1, -(-w // max_width))
    rows = []
    mask = bytes((v & levels) for v in range(256))
    for y in range(max(0, top), min(h, bottom if bottom else h), factor):
        row = pixels[y * w * 4:(y + 1) * w * 4]
        r, g, b = (row[0::4][::factor]).translate(mask), (row[1::4][::factor]).translate(mask), (row[2::4][::factor]).translate(mask)
        out = bytearray(1 + 3 * len(r))
        out[1::3], out[2::3], out[3::3] = r, g, b
        rows.append(bytes(out))
    width = len(rows[0]) // 3 if rows else 0

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, len(rows), 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b""))


def shot(d, label):
    """A small picture of the keyboard (and 200 px above it) through the job log: SHOT <name> <i>/<n> <base64> lines.
    tools/devicetest/shots_from_log.py makes the PNG files again. Never fails a scenario."""
    if d.control:
        return
    try:
        raw = screen_raw()
        if raw is None:
            log("INFO no picture for %s (the screen could not be read)" % label)
            return
        w, h, pixels = raw
        strip = dump().keyboard_node("suggestion_strip_view")
        top = (strip.top - 120) if strip else int(h * 0.55)
        data = base64.b64encode(png_of(w, h, pixels, top, bottom=top + 700)).decode("ascii")
        parts = [data[i:i + SHOT_CHUNK] for i in range(0, len(data), SHOT_CHUNK)] or [""]
        name = "%s-%s" % (d.name, label)
        SHOTS.append((name, ["SHOT %s %d/%d %s" % (name, i, len(parts), part) for i, part in enumerate(parts, 1)]))
    except Exception as error:  # a picture is evidence, not a test
        log("INFO no picture for %s: %s" % (label, error))


def _luminance(rgb):
    def lin(v):
        v /= 255.0
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    return 0.2126 * lin(rgb[0]) + 0.7152 * lin(rgb[1]) + 0.0722 * lin(rgb[2])


def contrast(a, b):
    la, lb = _luminance(a), _luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def ink_contrast(raw, box):
    """The contrast of the core of the ink in a slot against what is behind it, or None without ink. What is behind is the
    most frequent colour of the box (the strip, or the circle of the last microphone key)."""
    w, h, pixels = raw
    left, top, right, bottom = max(0, box[0]), max(0, box[1]), min(w, box[2]), min(h, box[3])
    colours = {}
    seen = []
    for y in range(top, bottom, 2):
        for x in range(left, right, 2):
            i = (y * w + x) * 4
            c = (pixels[i], pixels[i + 1], pixels[i + 2])
            colours[c] = colours.get(c, 0) + 1
            seen.append(c)
    if not colours:
        return None
    back = max(colours, key=colours.get)
    far = max(seen, key=lambda c: max(abs(c[0] - back[0]), abs(c[1] - back[1]), abs(c[2] - back[2])))
    strongest = max(abs(far[0] - back[0]), abs(far[1] - back[1]), abs(far[2] - back[2]))
    if strongest < 24:
        return None
    core = [c for c in seen if max(abs(c[0] - back[0]), abs(c[1] - back[1]), abs(c[2] - back[2])) >= strongest * 0.9]
    mean = tuple(sum(c[i] for c in core) / len(core) for i in range(3))
    return contrast(mean, back)


def _restore_default_bar(d):
    try:
        reset_bar(d)
    except (DeviceError, CheckFailed):
        pass


def _typed_still_works(d):
    """The end of every bar scenario: the keyboard still types after the changes. (The control run leaves the last
    character out, so this is where it fails.)"""
    d.refresh_keys()
    d.type("ab")
    d.expect_field("text", "Ab")


def bar_icons_visible(d):
    """Every tool of the bar is clearly visible (contrast of the real pixels against the strip), in the day and the night
    look of the keyboard. Build 41 showed two tools as black holes on a dark design."""
    d.start("bar_icons_visible")
    d.focus("text")
    try:
        for mode in ("no", "yes"):
            shell("cmd uimode night " + mode, check=False)
            time.sleep(2.5)
            hide_and_reopen(d)
            time.sleep(0.8)
            screen = ensure_tools(d)
            slots = bar_slots(screen)
            if len(slots) < 2:
                raise CheckFailed("the bar shows %d tools; the keyboard shows: %s" % (len(slots), bar_report(screen)))
            raw = screen_raw()
            if raw is None:
                raise DeviceError("the screen could not be read")
            weakest = None
            for n in slots:
                c = ink_contrast(raw, (n.left, n.top, n.right, n.bottom))
                d.notes.append("night=%s %s: contrast %s" % (mode, n.desc, "none" if c is None else "%.1f" % c))
                if c is None:
                    raise CheckFailed("night=%s: no icon is visible in the slot %r %s" % (mode, n.desc, n.box))
                weakest = c if weakest is None else min(weakest, c)
            d.expect_equal("night=%s: the weakest icon has a contrast of at least 3:1 (%.1f)" % (mode, weakest), weakest >= 3.0, True)
            shot(d, "default_bar_night_" + mode)
    finally:
        shell("cmd uimode night auto", check=False)
    d.focus("text")
    _typed_still_works(d)


def bar_edit_drag_in(d):
    """Customizing with a real finger: hold a tool in the grid, drag it between two tools of the bar (the others make room
    while the finger is down), lift; the bar has it there, also after the keyboard was hidden and shown again."""
    d.start("bar_edit_drag_in")
    d.focus("text")
    try:
        reset_bar(d)
        shot(d, "default_bar")
        before = bar_order(dump())
        d.expect_equal("the default bar has at least four tools", len(before) >= 4, True)
        screen = open_edit_mode(d)
        shot(d, "edit_mode")
        pill = first_free_pill(screen)
        if pill is None:
            raise CheckFailed("the grid shows no tool outside the bar; it shows %s" % [n.text for n in grid_pills(screen)][:12])
        slots = bar_slots(screen)
        y = (slots[0].top + slots[0].bottom) // 2
        target = ((slots[1].left + slots[1].right) // 2 + (slots[2].left + slots[2].right) // 2) // 2
        at = pill.center
        touch_down(*at)
        try:
            time.sleep(0.6)  # the hold: the tool is in the hand
            at = glide(at, (target, y), steps=7)
            time.sleep(0.5)
            during = dump()
            order = bar_order(during)
            shot(d, "dragging")
            d.expect_equal("while the finger is down the bar has one place more", len(order), len(before) + 1)
            gap = order.index(pill.text) if pill.text in order else -1
            d.expect_equal("the gap is between two tools (place %d)" % gap, 0 < gap < len(before), True)
        finally:
            touch_up(*at)
        time.sleep(1.0)
        after = bar_order(dump())
        d.expect_equal("the tool is where the gap was", after, order)
        d.expect_equal("the others kept their order", [x for x in after if x != pill.text], before)
        shot(d, "after_drop")
        finish_edit_mode(d)
        hide_and_reopen(d)
        d.expect_equal("the order was saved", bar_order(dump()), after)
    finally:
        _restore_default_bar(d)
    d.focus("text")
    _typed_still_works(d)


def bar_edit_reorder(d):
    """The first tool of the bar is dragged to the last place with a real finger; saved."""
    d.start("bar_edit_reorder")
    d.focus("text")
    try:
        reset_bar(d)
        before = bar_order(dump())
        d.expect_equal("the default bar has at least four tools", len(before) >= 4, True)
        screen = open_edit_mode(d)
        slots = bar_slots(screen)
        y = (slots[0].top + slots[0].bottom) // 2
        start = slots[0].center
        end = (slots[-1].right - 10, y)
        touch_down(*start)
        at = start
        try:
            at = glide(start, (start[0] + 80, y), steps=2)  # beyond the touch slop: the tool is in the hand
            at = glide(at, end, steps=7)
            time.sleep(0.5)
            d.expect_equal("while the finger is down the first tool is at the end", bar_order(dump()), before[1:] + before[:1])
        finally:
            touch_up(*at)
        time.sleep(1.0)
        d.expect_equal("after the lift", bar_order(dump()), before[1:] + before[:1])
        finish_edit_mode(d)
        hide_and_reopen(d)
        d.expect_equal("the order was saved", bar_order(dump()), before[1:] + before[:1])
    finally:
        _restore_default_bar(d)
    d.focus("text")
    _typed_still_works(d)


def bar_edit_drag_out(d):
    """A tool of the bar is dragged down into the grid with a real finger: it is gone from the bar, also after reopening."""
    d.start("bar_edit_drag_out")
    d.focus("text")
    try:
        reset_bar(d)
        before = bar_order(dump())
        d.expect_equal("the default bar has at least four tools", len(before) >= 4, True)
        screen = open_edit_mode(d)
        slots = bar_slots(screen)
        victim = slots[2]
        start = victim.center
        touch_down(*start)
        at = start
        try:
            at = glide(start, (start[0], start[1] + 80), steps=2)
            keyboard = screen.keyboard_view()
            low = (start[0], (keyboard.top + keyboard.bottom) // 2 if keyboard else start[1] + 600)
            at = glide(at, low, steps=7)
            time.sleep(0.5)
            expected = before[:2] + before[3:]
            d.expect_equal("while the finger is down the bar has closed up", bar_order(dump()), expected)
        finally:
            touch_up(*at)
        time.sleep(1.0)
        d.expect_equal("the tool is gone", bar_order(dump()), before[:2] + before[3:])
        finish_edit_mode(d)
        hide_and_reopen(d)
        d.expect_equal("the removal was saved", bar_order(dump()), before[:2] + before[3:])
    finally:
        _restore_default_bar(d)
    d.focus("text")
    _typed_still_works(d)


def lowest_free_pill(screen, skip=()):
    """The tool outside the bar that lies lowest in the visible grid (fully inside the keyboard window)."""
    view = screen.keyboard_view()
    limit = view.bottom if view is not None else 10 ** 6
    free = [n for n in grid_pills(screen) if not n.desc.endswith(IN_BAR_SUFFIXES) and n.text not in EDIT_LABELS
            and n.text not in skip and n.bottom <= limit]
    return max(free, key=lambda n: n.top) if free else None


def drag_pill_into_bar(d, pill, before):
    """Holds a pill of the grid and drags it between the second and third tool of the bar (real touch); checks the gap
    while the finger is down and the bar after the lift. Returns the order of the bar."""
    slots = bar_slots(dump())
    y = (slots[0].top + slots[0].bottom) // 2
    target = ((slots[1].left + slots[1].right) // 2 + (slots[2].left + slots[2].right) // 2) // 2
    at = pill.center
    touch_down(*at)
    try:
        time.sleep(0.6)  # the hold: the tool is in the hand
        at = glide(at, (target, y), steps=8)
        time.sleep(0.5)
        during = bar_order(dump())
        d.expect_equal("while the finger is down (%s) the bar has one place more" % pill.text, len(during), len(before) + 1)
    finally:
        touch_up(*at)
    time.sleep(1.0)
    after = bar_order(dump())
    d.expect_equal("%s is where the gap was" % pill.text, after, during)
    d.expect_equal("the others kept their order", [x for x in after if x != pill.text], before)
    return after


def bar_edit_scroll_then_drag(d):
    """The grid has some 25 tools, so it is scrolled before a tool far down is dragged into the bar (real touches).
    Build 41 + stage 88 before the fix: a scroll that started on a pill left the container stuck, no drag worked
    afterwards until the edit mode was left. A second drag in the same edit mode must work too."""
    d.start("bar_edit_scroll_then_drag")
    d.focus("text")
    try:
        reset_bar(d)
        before = bar_order(dump())
        screen = open_edit_mode(d)
        first = lowest_free_pill(screen)
        if first is None:
            raise CheckFailed("the grid shows no tool outside the bar; it shows %s" % [n.text for n in grid_pills(screen)][:12])
        tops = {n.text: n.top for n in grid_pills(screen)}
        # scroll with a finger that starts on a pill, as a user does (not on the free margin)
        start = first.center
        touch_down(*start)
        try:
            glide(start, (start[0], start[1] - 650), steps=10)
        finally:
            touch_up(start[0], start[1] - 650)
        time.sleep(0.8)
        screen = dump()
        moved = [t for t in grid_pills(screen) if t.text in tops and abs(t.top - tops[t.text]) > 100]
        d.expect_equal("the grid scrolled under the finger (%d pills moved)" % len(moved), len(moved) > 0, True)
        pill = lowest_free_pill(screen)
        if pill is None:
            raise CheckFailed("no tool left in the scrolled grid")
        shot(d, "scrolled")
        after = drag_pill_into_bar(d, pill, before)
        # a second drag in the same edit mode, after the first one and the scroll
        second = lowest_free_pill(dump(), skip=(pill.text,))
        if second is None:
            raise CheckFailed("no second tool in the grid")
        after2 = drag_pill_into_bar(d, second, after)
        d.expect_equal("two tools were added", len(after2), len(before) + 2)
        finish_edit_mode(d)
        hide_and_reopen(d)
        d.expect_equal("the order was saved", bar_order(dump()), after2)
    finally:
        _restore_default_bar(d)
    d.focus("text")
    _typed_still_works(d)


def bar_edit_undo(d):
    """A tap on a tool of the grid appends it, Rückgängig takes it back, Zurücksetzen (with its question) brings the
    standard bar back."""
    d.start("bar_edit_undo")
    d.focus("text")
    try:
        reset_bar(d)
        before = bar_order(dump())
        screen = open_edit_mode(d)
        pill = first_free_pill(screen)
        if pill is None:
            raise CheckFailed("the grid shows no tool outside the bar")
        tap(*pill.center)
        time.sleep(0.8)
        d.expect_equal("a tap appends the tool", bar_order(dump()), before + [pill.text])
        tap(*grid_node(dump(), "undo").center)
        time.sleep(0.8)
        d.expect_equal("Rückgängig takes it back", bar_order(dump()), before)
        tap(*first_free_pill(dump()).center)
        time.sleep(0.8)
        d.expect_equal("appended again", len(bar_order(dump())), len(before) + 1)
        tap(*grid_node(dump(), "reset").center)
        d.wait(lambda s: grid_node(s, "reset_yes") is not None, "the question")
        tap(*grid_node(dump(), "reset_no").center)
        time.sleep(0.6)
        d.expect_equal("No keeps the bar", len(bar_order(dump())), len(before) + 1)
        tap(*grid_node(dump(), "reset").center)
        d.wait(lambda s: grid_node(s, "reset_yes") is not None, "the question")
        tap(*grid_node(dump(), "reset_yes").center)
        time.sleep(0.8)
        d.expect_equal("Zurücksetzen brings back the standard bar", bar_order(dump()), before)
        finish_edit_mode(d)
    finally:
        _restore_default_bar(d)
    d.focus("text")
    _typed_still_works(d)


def bar_shots(d):
    """Pictures of the bar with 1, 3, 5 and 8 tools (the tap in the edit mode builds them), for looking at the spacing."""
    d.start("bar_shots")
    d.focus("text")
    try:
        reset_bar(d)
        d.expect_equal("the standard bar has five tools", len(bar_order(dump())), 5)
        shot(d, "grid_closed_5")
        screen = open_edit_mode(d)
        shot(d, "grid_open")
        for _ in range(3):  # to eight
            pill = first_free_pill(dump())
            if pill is None:
                raise CheckFailed("no tool left to add")
            tap(*pill.center)
            time.sleep(0.7)
        d.expect_equal("eight tools", len(bar_order(dump())), 8)
        shot(d, "bar_8")
        for _ in range(5):  # to three
            tap(*bar_slots(dump())[-1].center)
            time.sleep(0.7)
        d.expect_equal("three tools", len(bar_order(dump())), 3)
        shot(d, "bar_3")
        for _ in range(2):  # to one
            tap(*bar_slots(dump())[-1].center)
            time.sleep(0.7)
        d.expect_equal("one tool", len(bar_order(dump())), 1)
        shot(d, "bar_1")
        finish_edit_mode(d)
    finally:
        _restore_default_bar(d)
    d.focus("text")
    _typed_still_works(d)


def _strip_kind(screen):
    """What the bar shows: T the tools, W words, E the words area with nothing in it (stage 89)."""
    if bar_slots(screen):
        return "T"
    strip = screen.keyboard_node("suggestion_strip_view")
    if strip is None:
        return "?"
    for n in screen.nodes:
        if (n.package == KEYBOARD_PACKAGE and n.visible and (n.text or "").strip() and not n.cls.endswith(".Key")
                and strip.left <= n.center[0] < strip.right and strip.top <= n.center[1] < strip.bottom):
            return "W"
    return "E"


def _end_circle(screen):
    """Stage 99: the circle at the right end: the row's last slot while the tools show, else the button beside the words (or None)."""
    beside = [n for n in screen.nodes if n.rid == END_CIRCLE_ID and n.visible]
    if beside:
        return beside[0]
    slots = bar_slots(screen)
    return slots[-1] if slots and (slots[-1].desc or "").startswith(MIC_LABELS) else None


def bar_calm_typing(d):
    """Stage 89, the quiet bar while writing, with real key touches: a sentence with punctuation, ten deletes, the tools on request.
    Stage 99: words or nothing while typing (never the tools), the microphone circle at the same place after every key when the bar
    ends with one, the tools after a pause of four seconds, the next key brings the words area back.
    The bar is read after every key (a dump takes a moment: a change that lasts less than that is not seen here; the unit test
    StripCalmLogTest samples every 10 ms). The Enter key is not typed (the test field is a single line: Enter would be the editor
    action), the paragraph is covered by the unit test."""
    d.start("bar_calm_typing")
    d.focus("text")
    ensure_tools(d)
    d.expect_equal("a new field shows the tools", _strip_kind(dump()), "T")
    first = _end_circle(dump())
    circle = None if first is None else (first.center, first.desc)
    d.notes.append("microphone circle at the end: " + ("yes, at %s" % (circle[0],) if circle else "no (the bar of this emulator does not end with one)"))
    kinds = []
    circles = []
    for char in "Hallo, wie geht es dir. Gut":
        d.type(char)
        time.sleep(0.35)
        screen = dump()
        kinds.append((char, _strip_kind(screen)))
        c = _end_circle(screen)
        circles.append(None if c is None else (c.center, c.desc))
        if char == " " and len(kinds) == 7:
            shot(d, "calm_after_space")
    d.notes.append("kinds while typing: " + "".join(k for _, k in kinds))
    # stage 99: words or nothing while typing, never the tools (a dump takes about a second, far below the pause of four seconds)
    d.expect_equal("the tools never come while typing", [c for c, k in kinds[1:] if k == "T"], [])
    # "E" (the middle empty) is allowed after a space or punctuation, not inside a word (the dictionary offers the typed word)
    d.expect_equal("no empty words area inside a word", [c for c, k in kinds[1:] if k == "E" and c.isalpha()], [])
    d.notes.append("empty words area after punctuation or space: %d of %d keys" % (sum(1 for c, k in kinds if k == "E"), len(kinds)))
    if circle is not None:
        d.expect_equal("the microphone circle stands at the same place after every key", [i for i, c in enumerate(circles) if c is None or c[0] != circle[0]], [])
    shot(d, "calm_typed")
    deletes = []
    for _ in range(10):
        d.press("delete")
        time.sleep(0.35)
        screen = dump()
        deletes.append(_strip_kind(screen))
        if circle is not None:
            c = _end_circle(screen)
            d.expect_equal("the circle stays while deleting", None if c is None else c.center, circle[0])
    d.notes.append("kinds while deleting: " + "".join(deletes))
    d.expect_equal("never the tools while deleting", [k for k in deletes if k == "T"], [])
    # the owner's case: deleting back into a word shows words for it (the cursor ends in "e" of "es")
    d.expect_equal("back inside a word the words show", deletes[-1], "W")
    shot(d, "calm_deleted")
    # stage 99: a pause of four seconds without a key: the tools; the next key: the words area again
    time.sleep(4.5)
    d.expect_equal("after a pause of four seconds the tools", _strip_kind(dump()), "T")
    shot(d, "calm_pause_tools")
    d.press("delete")
    time.sleep(0.6)
    d.expect_equal("the next key brings the words area back", _strip_kind(dump()) != "T", True)
    # the left key: the tools, and they stay until the next typed key
    key = left_key(dump())
    if key is None:
        raise CheckFailed("no left key; the keyboard shows: " + bar_report(dump()))
    tap(*key.center)
    time.sleep(0.8)
    d.expect_equal("the left key shows the tools", _strip_kind(dump()), "T")
    time.sleep(1.5)
    d.expect_equal("the tools stay", _strip_kind(dump()), "T")
    undo = [n for n in bar_slots(dump()) if (n.desc or "").startswith(("Rückgängig", "Undo"))]
    if not undo:
        raise CheckFailed("the bar has no undo tool; it shows: " + bar_report(dump()))
    tap(*undo[0].center)
    time.sleep(1.2)
    screen = dump()
    d.expect_equal("the tools stay after Rückgängig", _strip_kind(screen), "T")
    offered = [n.desc for n in bar_slots(screen) if "Wiederholen" in (n.desc or "") or "redo" in (n.desc or "").lower()]
    d.expect_equal("after Rückgängig the undo tool offers redo", len(offered), 1)
    shot(d, "calm_undo_redo")
    tap(*bar_slots(dump())[[n.desc for n in bar_slots(dump())].index(offered[0])].center)  # a second undo works without the bar changing
    time.sleep(0.8)
    d.expect_equal("a second Rückgängig keeps the tools", _strip_kind(dump()), "T")
    d.type("a")
    time.sleep(0.6)
    d.expect_equal("the next typed key brings the words area back", _strip_kind(dump()) != "T", True)
    # the field holds the sentence: the keyboard still types after it (not _typed_still_works, which expects an empty field)
    now = d.field("text")
    d.refresh_keys()
    d.type("ab")
    d.expect_field("text", now + "ab")


def revert_correction_shows_word(d):
    """Stage 89b, the owner's case: a delete right after an autocorrection gives the typed word back, and the bar shows words for it
    (before: an empty bar until one more letter was deleted)."""
    d.start("revert_correction_shows_word")
    d.focus("text")
    for char in "hallo jungs ":
        d.type(char)
    time.sleep(0.8)
    text = d.field("text")
    if not text.endswith("Jungs "):
        raise CheckFailed("no autocorrection to revert; the field holds %d chars ending %r" % (len(text), text[-6:]))
    d.press("delete")
    time.sleep(1.0)
    d.expect_field("text", text[:-6] + "jungs")
    screen = dump()
    d.notes.append("after the revert the bar shows: " + _strip_kind(screen))
    d.expect_equal("after the revert the words show", _strip_kind(screen), "W")
    shot(d, "revert_word_shown")


SCENARIOS = [keyboard_is_secondhand, key_positions_reported, typing_text, delete_key, typing_password, typing_email, typing_number,
             capital_after_period, autocorrect_works, autocorrect_typo, autocorrect_typo_in_sentence,
             autocorrect_noun, swapped_letters_keep_nouns, swapped_letters_keep_caps, slang_stays, emoji_suggestion, language_switch, clipboard_pill,
             clipboard_not_readable_in_password, learning_not_in_password, prompt_store, prompt_folders, autofill_inline,
             cursor_trackpad, cursor_trackpad_vertical,
             # stage 88: the bar with real touches; each leaves the standard bar behind
             bar_icons_visible, bar_edit_drag_in, bar_edit_reorder, bar_edit_drag_out, bar_edit_scroll_then_drag, bar_edit_undo, bar_shots,
             # stage 89: the quiet bar while writing
             bar_calm_typing, revert_correction_shows_word]


# Scenarios that fail because the keyboard does not do what was expected of it, found by these tests
# and reported to the owner. They stay in the list above and keep running; their failure is shown as
# KNOWN and does not turn the run red, like the five inherited failures of the unit tests. A KNOWN
# scenario that passes turns the run red, so the list cannot go stale.
PROBES = [reported_finding_2_apostrophe_de, reported_finding_2_apostrophe_en, reported_finding_3_capital_i,
          reported_finding_1_english_sentences, reported_finding_2_4_history_loop, reported_finding_3_level_gentle]

KNOWN_FINDINGS = {
    # empty since stage 75 (swapped letters): "idr" is expected to become "dir" again
}


# ------------------------------------------------------------------------------------------ setup

def set_locales():
    """Makes German and English the system languages. Needs root (google_apis images have it) and a
    restart of the Android framework."""
    if LOCALES.replace("-", "-r").split(",")[0] in shell("am get-config", check=False) \
            and shell("settings get system system_locales", check=False) == LOCALES:
        return
    adb("root", timeout=30)
    time.sleep(3)
    adb("wait-for-device", timeout=60)
    shell("settings put system system_locales " + LOCALES)
    shell("setprop persist.sys.locale " + LOCALES.split(",")[0])
    # Android writes its settings to disk a moment later: stopping at once lost the second language
    # in most runs (it arrived in runs 6 and 9 only).
    time.sleep(6)
    shell("sync", check=False)
    shell("stop; sleep 2; start", timeout=60)
    time.sleep(10)
    wait_for_boot()
    time.sleep(10)


def accessibility_state():
    """What the system says about its accessibility services: "bound" once the probe service runs,
    followed by the raw lines (the bound services are listed by app label, the enabled ones by class)."""
    text = shell("dumpsys accessibility", check=False, timeout=30)
    lines = [line.strip() for line in text.splitlines()
             if line.strip().startswith(("Bound services", "Enabled services", "Binding services", "Crashed services"))]
    bound = any(line.startswith("Bound services") and PROBE_LABEL in line for line in lines)
    return ("bound; " if bound else "not bound; ") + " | ".join(line[:160] for line in lines)[:520]


def setup(keyboard_apk, test_apk, info):
    wait_for_boot()
    set_locales()
    config = shell("am get-config", check=False).splitlines()
    info["android"] = "Android %s (API %s)" % (shell("getprop ro.build.version.release"),
                                              shell("getprop ro.build.version.sdk"))
    info["screen"] = "%s, %s" % (shell("wm size"), shell("wm density"))
    info["config"] = (config[0] if config else "")[:160]
    if "de-rDE" not in info["config"]:
        raise DeviceError("the system language is not German: %r" % info["config"])

    adb("install", "-r", keyboard_apk, timeout=300)
    adb("install", "-r", test_apk, timeout=120)
    version = re.search(r"versionName=(\S+)", shell("dumpsys package " + KEYBOARD_PACKAGE))
    info["keyboard"] = "%s %s" % (KEYBOARD_PACKAGE, version.group(1) if version else "?")

    # accessibility on (so the keys are in the UI dump), key names not hidden in password fields.
    # The test app is started once first: a freshly installed app is in the "stopped" state.
    shell("am start -W -n %s/.MainActivity" % TEST_PACKAGE, timeout=60)
    shell("settings put secure enabled_accessibility_services " + PROBE_SERVICE)
    shell("settings put secure accessibility_enabled 1")
    shell("settings put secure speak_password 1")
    info["accessibility"] = accessibility_state()
    deadline = time.time() + 30
    while not info["accessibility"].startswith("bound") and time.time() < deadline:
        time.sleep(2)
        info["accessibility"] = accessibility_state()
    if not info["accessibility"].startswith("bound"):
        raise DeviceError("the accessibility probe was not bound by the system: " + info["accessibility"])

    ime = ""
    for line in shell("ime list -a -s").splitlines():
        if line.strip().startswith(KEYBOARD_PACKAGE + "/"):
            ime = line.strip()
    if not ime:
        raise DeviceError("the keyboard is not among the input methods of the system")
    shell("ime enable " + ime)
    shell("ime set " + ime)
    current = shell("settings get secure default_input_method")
    if current != ime:
        raise DeviceError("the keyboard did not become the current input method: %r" % current)
    info["input method"] = ime
    shell("input keyevent KEYCODE_WAKEUP", check=False)
    shell("wm dismiss-keyguard", check=False)
    shell("input keyevent KEYCODE_MENU", check=False)


# -------------------------------------------------------------------------------------- recording

class Recorder(threading.Thread):
    """Records the screen in pieces (screenrecord stops after three minutes) until stop()."""

    def __init__(self, out_dir):
        super().__init__(daemon=True)
        self.out_dir = out_dir
        self.stopping = threading.Event()
        self.remote = []

    def run(self):
        index = 0
        while not self.stopping.is_set():
            index += 1
            remote = "%s/sh-video-%02d.mp4" % (REMOTE_TMP, index)
            self.remote.append(remote)
            started = time.time()
            if self.stopping.is_set():
                break
            try:
                adb("shell", "screenrecord --bit-rate 3000000 --time-limit 170 " + remote, timeout=200, check=False)
            except DeviceError:
                pass
            if time.time() - started < 5:  # it did not record: do not spin
                self.stopping.wait(5)

    def stop(self):
        """Ends the recording and fetches the pieces. Returns the local files that hold a video."""
        self.stopping.set()
        for _ in range(4):  # a piece may start just after the first signal: signal until the thread ended
            try:
                shell("kill -2 $(pidof screenrecord)", check=False, timeout=20)
            except DeviceError:
                pass
            self.join(8)
            if not self.is_alive():
                break
        time.sleep(3)  # screenrecord finishes the file after the signal
        files = []
        for remote in self.remote:
            local = os.path.join(self.out_dir, os.path.basename(remote).replace("sh-video", "video"))
            try:
                adb("pull", remote, local, timeout=120, check=False)
            except DeviceError:
                continue
            if os.path.isfile(local) and os.path.getsize(local) > 10000:
                files.append(local)
            elif os.path.isfile(local):
                os.remove(local)
        return files


# ------------------------------------------------------------------------------------------- main

def run_scenario(function, driver):
    """Runs one scenario and returns its result. Nothing a scenario throws is lost."""
    name = function.__name__
    started = time.time()
    try:
        function(driver)
        status, detail = "PASS", "; ".join(driver.notes)
        if driver.checks == 0:
            status, detail = "ERROR", "the scenario compared nothing"
    except CheckFailed as error:
        status, detail = "FAIL", str(error)
    except DeviceError as error:
        status, detail = "ERROR", "device: %s" % error
    except Exception as error:  # a bug in this script must not look like a pass
        status, detail = "ERROR", "script: %s: %s" % (type(error).__name__, error)
    driver.name = name
    if status != "PASS" and name.startswith("bar_"):
        shot(driver, "failed")
    shot_ok = driver.screenshot("end")
    if status != "PASS":
        try:
            driver.save_dump(dump(), "end")
        except DeviceError:
            pass
    return {"name": name, "status": status, "detail": detail[:(3000 if name.startswith("bar_") and status != "PASS" else 600)], "seconds": round(time.time() - started, 1),
            "screenshot": shot_ok, "doc": (function.__doc__ or "").strip().split("\n")[0]}


def write_reports(out_dir, info, results, problems, ok, control):
    with open(os.path.join(out_dir, "results.json"), "w", encoding="utf-8") as out:
        json.dump({"ok": ok, "control_run": control, "info": info, "results": results, "problems": problems},
                  out, ensure_ascii=False, indent=1)
    passed = sum(1 for r in results if r["status"] == "PASS")
    if control:
        lines = ["### Device tests, CONTROL RUN: %s" % (
            "as it must be, every scenario FAILED" if ok else "NOT as it must be"), "",
            "The last character of every typed text was left out on purpose. Every scenario must show FAIL "
            "below; a PASS would be a test that cannot notice an error.", ""]
    else:
        lines = ["### Device tests: %s" % ("PASSED" if ok else "FAILED"), ""]
    known = sum(1 for r in results if r["status"] == "KNOWN")
    lines += ["%d of %d scenarios passed%s." % (
        passed, len(results), ", %d failed as known findings (KNOWN, already reported)" % known if known else ""), ""]
    lines += ["| Scenario | Result | Seconds | Detail |", "| --- | --- | --- | --- |"]
    for r in results:
        detail = r["detail"].replace("|", "\\|").replace("\n", " ")
        lines.append("| %s | %s | %s | %s |" % (r["name"], r["status"], r["seconds"], detail))
    if problems:
        lines += ["", "Problems:"] + ["- " + p for p in problems]
    lines += ["", "Environment:"] + ["- %s: %s" % (k, v) for k, v in info.items()]
    with open(os.path.join(out_dir, "summary.md"), "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--keyboard-apk", required=True)
    parser.add_argument("--test-apk", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--only", default="", help="comma-separated scenario names (also the investigation probes); default: all")
    parser.add_argument("--control", action="store_true",
                        help="control run: the last typed character is left out, every scenario must fail")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    scenarios = list(SCENARIOS)
    if args.only:
        wanted = [name.strip() for name in args.only.split(",") if name.strip()]
        everything = {f.__name__: f for f in SCENARIOS + PROBES}
        scenarios = [everything[name] for name in wanted]  # an unknown name is an error, not an empty run
    info, results, problems = {}, [], []
    recorder = None
    try:
        setup(args.keyboard_apk, args.test_apk, info)
        for key, value in info.items():
            log("INFO %s: %s" % (key, value))
        recorder = Recorder(args.out)
        recorder.start()
        driver = Driver(args.out, args.control)
        device_errors = 0
        for function in scenarios:
            result = run_scenario(function, driver)
            results.append(result)
            log("RESULT %s %s (%ss) %s" % (result["status"], result["name"], result["seconds"], result["detail"]))
            device_errors = device_errors + 1 if result["detail"].startswith("device:") else 0
            if device_errors >= 3:
                problems.append("stopped after three scenarios in a row failed on the device itself")
                break
    except DeviceError as error:
        problems.append("setup: %s" % error)
    except Exception as error:
        problems.append("script: %s: %s" % (type(error).__name__, error))
    finally:
        videos = recorder.stop() if recorder else []
        info["videos"] = ", ".join(os.path.basename(v) for v in videos) or "none"
        if recorder and not videos:
            problems.append("no screen recording")
        ran = {r["name"] for r in results}
        missing = [f.__name__ for f in scenarios if f.__name__ not in ran]
        if missing:
            problems.append("not run: " + ", ".join(missing))
        if args.control:
            survived = [r["name"] for r in results if r["status"] == "PASS"]
            if survived:
                problems.append("CONTROL BROKEN, passed although its last character was not typed: " + ", ".join(survived))
            wrong = [r["name"] for r in results if r["status"] == "ERROR"]
            if wrong:
                problems.append("CONTROL UNCLEAR, ended with an error instead of a failed comparison: "
                                + ", ".join(wrong))
            failed = sum(1 for r in results if r["status"] == "FAIL")
            log("CONTROL %d of %d scenarios failed on a real difference in the field" % (failed, len(scenarios)))
        # A normal run is good when every scenario passed; a control run when every scenario failed on
        # a comparison (not on the device), so that green always means "as it must be".
        if not args.control:
            for result in results:
                reason = KNOWN_FINDINGS.get(result["name"])
                if reason and result["status"] == "FAIL":
                    result["status"] = "KNOWN"
                    result["detail"] = ("known finding (%s): %s" % (reason, result["detail"]))[:600]
                elif reason and result["status"] == "PASS":
                    problems.append("%s passes now: take it out of KNOWN_FINDINGS" % result["name"])
        wanted = ("FAIL",) if args.control else ("PASS", "KNOWN")
        ok = not problems and len(results) == len(scenarios) and all(r["status"] in wanted for r in results)
        for problem in problems:
            log("PROBLEM " + problem)
        if SHOTS:  # a file, which the workflow prints last (the end of the log is what can be read)
            with open(os.path.join(args.out, "shots.txt"), "w", encoding="utf-8") as shots_out:
                for name, lines in reversed(SHOTS):  # the first taken lies last
                    shots_out.write("\n".join(lines) + "\n")
        write_reports(args.out, info, results, problems, ok, args.control)
        passed = sum(1 for r in results if r["status"] == "PASS")
        known = sum(1 for r in results if r["status"] == "KNOWN")
        for result in results:
            if result["status"] == "KNOWN":
                log("KNOWN %s: %s" % (result["name"], KNOWN_FINDINGS[result["name"]]))
        log("SUMMARY %s passed=%d failed=%d known=%d total=%d expected=%d videos=%d" % (
            "OK" if ok else "FAILED", passed, len(results) - passed - known, known, len(results), len(scenarios),
            len(videos)))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
