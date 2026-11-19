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
only if every scenario ran and passed and the recording exists.

--control is the control run that shows the scenarios can fail: the last character of every typed
text is left out, so the field really holds something else than expected and every scenario must end
with a failed comparison. A scenario that still passes cannot turn red and is reported as
"CONTROL BROKEN".

Only the standard library is used. Nothing typed here is secret; the texts are fixed test words.
"""

import argparse
import base64
import json
import os
import re
import subprocess
import sys
import struct
import threading
import time
import zlib

KEYBOARD_PACKAGE = "dev.personal.agentkeyboard.debug"
TEST_PACKAGE = "dev.personal.secondhand.devicetest"
PROBE_SERVICE = TEST_PACKAGE + "/" + TEST_PACKAGE + ".ProbeAccessibilityService"
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
    "enter": ("Eingabetaste", "Return"),
    "symbols": ("Symbole", "Symbols"),
    "letters": ("Buchstaben", "Letters"),
}
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
        self.shifted = False
        strip = self.keyboard_node("suggestion_strip_view")
        if keys and strip is not None and min(key.top for key in keys) < strip.bottom - 8:
            # The keys cannot lie above the suggestion strip. Run 8 showed the keyboard reporting them
            # (and its own view) at the top of the screen; the strip, an ordinary view, is where it is
            # drawn, and the keys start right below it.
            origin = view.top if view is not None else min(key.top for key in keys)
            for key in keys:
                key.move(0, strip.bottom - origin)
            self.shifted = True
        found = {}
        for node in keys:
            found.setdefault(plain_key_name(node.desc), node)
        return found

    def keyboard_view(self):
        """The view that draws the keys (the visible one; the emoji and clipboard views have their own)."""
        return self.keyboard_node("keyboard_view")

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
        self.thumbnails = 0
        self.keymap = {}
        self.name = ""
        self.checks = 0
        self.notes = []

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
        self.wait(lambda screen: screen.by_id("field_text") is not None, "the test app with its fields")

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
                raise DeviceError("waited %ss for %s; on screen: %s" % (int(limit), what, describe(screen)))
            time.sleep(0.7)

    def focus(self, field):
        """Taps into the field and waits for the keyboard with its keys."""
        screen = dump()
        node = screen.by_id("field_" + field)
        if node is None:
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
                sample.desc[:12], sample.box, ", keys moved below the strip" if screen.shifted else ""))
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
        state is, so automatic capitalisation shows in the result; an upper-case letter gets a tap on
        shift first. A character without a key in the current layout fails the scenario."""
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
            if node is None:
                raise CheckFailed("no key for %r on the keyboard; keys: %s" % (char, self.key_list()))
            if char.isalpha() and char.isupper():
                self.press("shift")
            tap(*node.center)

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

    def thumbnail(self):
        """Prints a small grey picture of the screen into the log (base64 PNG between THUMB lines):
        the artifacts cannot be fetched from every place the log can be read from."""
        try:
            raw = adb("exec-out", "screencap", timeout=30, binary=True)
            width, height = struct.unpack("<II", raw[:8])
            pixels = raw[len(raw) - width * height * 4:]
            if width <= 0 or height <= 0 or len(pixels) != width * height * 4:
                return
            step = 6
            rows = []
            for y in range(0, height, step):
                line = pixels[y * width * 4:(y + 1) * width * 4]
                red, green, blue = line[0::4 * step], line[1::4 * step], line[2::4 * step]
                rows.append(b"\x00" + bytes((r * 3 + g * 6 + b) // 10 for r, g, b in zip(red, green, blue)))
            small_width = len(rows[0]) - 1

            def chunk(kind, data):
                body = kind + data
                return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

            png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", small_width, len(rows), 8, 0, 0, 0, 0))
                   + chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b""))
            text = base64.b64encode(png).decode("ascii")
            log("THUMB-BEGIN %s %dx%d" % (self.name, small_width, len(rows)))
            for start in range(0, len(text), 160):
                log("THUMB " + text[start:start + 160])
            log("THUMB-END")
        except (DeviceError, struct.error, IndexError):
            pass

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


def tap(x, y):
    shell("input tap %d %d" % (x, y), timeout=30)
    time.sleep(0.15)


# -------------------------------------------------------------------------------------- scenarios
# Every scenario starts from a fresh test app. The expectations are what a user must see in the field.

def keyboard_is_secondhand(d):
    """Control: the keyboard on the screen is the installed SecondHand build, not another input method."""
    d.start("keyboard_is_secondhand")
    screen = d.focus("text")
    d.expect_equal("current input method", shell("settings get secure default_input_method").split("/")[0],
                   KEYBOARD_PACKAGE)
    packages = sorted({n.package for n in screen.nodes if n.cls.endswith(".Key")})
    d.expect_equal("package of the keys", ",".join(packages), KEYBOARD_PACKAGE)
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


SCENARIOS = [keyboard_is_secondhand, typing_text, delete_key, typing_password, typing_email, typing_number]


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
            try:
                adb("shell", "screenrecord --bit-rate 3000000 --time-limit 170 " + remote, timeout=200, check=False)
            except DeviceError:
                pass
            if time.time() - started < 5:  # it did not record: do not spin
                self.stopping.wait(5)

    def stop(self):
        """Ends the recording and fetches the pieces. Returns the local files that hold a video."""
        self.stopping.set()
        try:
            shell("kill -2 $(pidof screenrecord)", check=False, timeout=20)
        except DeviceError:
            pass
        self.join(30)
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
    shot = driver.screenshot("end")
    if driver.thumbnails < 2 and (status != "PASS" or driver.thumbnails == 0):
        driver.thumbnails += 1
        driver.thumbnail()
    if status != "PASS":
        try:
            driver.save_dump(dump(), "end")
        except DeviceError:
            pass
    return {"name": name, "status": status, "detail": detail[:600], "seconds": round(time.time() - started, 1),
            "screenshot": shot, "doc": (function.__doc__ or "").strip().split("\n")[0]}


def write_reports(out_dir, info, results, problems, ok, control):
    with open(os.path.join(out_dir, "results.json"), "w", encoding="utf-8") as out:
        json.dump({"ok": ok, "control_run": control, "info": info, "results": results, "problems": problems},
                  out, ensure_ascii=False, indent=1)
    passed = sum(1 for r in results if r["status"] == "PASS")
    lines = ["### Device tests: %s" % ("PASSED" if ok else "FAILED"), ""]
    if control:
        lines += ["**Control run:** the last character of every typed text was left out on purpose, so this run must fail.", ""]
    lines += ["%d of %d scenarios passed." % (passed, len(results)), ""]
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
    parser.add_argument("--control", action="store_true",
                        help="control run: the last typed character is left out, every scenario must fail")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

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
        for function in SCENARIOS:
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
        if not videos:
            problems.append("no screen recording")
        ran = {r["name"] for r in results}
        missing = [f.__name__ for f in SCENARIOS if f.__name__ not in ran]
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
            log("CONTROL %d of %d scenarios failed on a real difference in the field" % (failed, len(SCENARIOS)))
        ok = not problems and bool(results) and all(r["status"] == "PASS" for r in results)
        for problem in problems:
            log("PROBLEM " + problem)
        write_reports(args.out, info, results, problems, ok, args.control)
        passed = sum(1 for r in results if r["status"] == "PASS")
        log("SUMMARY %s passed=%d failed=%d total=%d expected=%d videos=%d" % (
            "OK" if ok else "FAILED", passed, len(results) - passed, len(results), len(SCENARIOS), len(videos)))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
