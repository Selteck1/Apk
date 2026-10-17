from __future__ import annotations

import asyncio
import json
import logging
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any

from aiogram import Bot, Dispatcher
from aiogram.filters import Command, CommandObject
from aiogram.types import InlineKeyboardButton, InlineKeyboardMarkup, Message
from playwright.async_api import BrowserContext, Page, async_playwright

ROOT = Path(__file__).resolve().parent
CONFIG_PATH = ROOT / "config.json"
EXAMPLE_PATH = ROOT / "config.example.json"
PROFILE_DIR = ROOT / "yandex-browser-profile"
STATE_PATH = ROOT / "state.json"

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
log = logging.getLogger("gift-scanner")

DEFAULTS: dict[str, Any] = {
    "bot_token": "",
    "admin_id": 0,
    "collection": "Chill Flame",
    "collection_slug": "",
    "min_price": 0,
    "max_price": 500,
    "model": "",
    "backdrop": "",
    "pattern": "",
    "interval_seconds": 15,
    "max_alerts_per_scan": 5,
}


def find_yandex_executable() -> Path:
    """Find the installed Yandex Browser executable on Windows."""
    roots = [
        os.environ.get("LOCALAPPDATA", ""),
        os.environ.get("PROGRAMFILES", ""),
        os.environ.get("PROGRAMFILES(X86)", ""),
    ]
    candidates = [
        Path(root) / "Yandex" / "YandexBrowser" / "Application" / "browser.exe"
        for root in roots if root
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise SystemExit(
        "Yandex Browser was not found. Install/start Yandex Browser once, "
        "then run start.bat again. This scanner needs the Yandex browser with your VPN extension."
    )


def yandex_is_running() -> bool:
    """Check for Yandex processes before copying its profile, to avoid a partial copy."""
    script = (
        "$p = Get-CimInstance Win32_Process -Filter \\"Name='browser.exe'\\" "
        "-ErrorAction SilentlyContinue | Where-Object { $_.ExecutablePath -like '*YandexBrowser*' }; "
        "if ($p) { exit 0 } else { exit 1 }"
    )
    try:
        result = subprocess.run(
            ["powershell.exe", "-NoProfile", "-Command", script],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=8,
            check=False,
        )
        return result.returncode == 0
    except (OSError, subprocess.SubprocessError):
        return False


def selected_profile_name(user_data: Path) -> str:
    """Read the active Yandex profile folder from Chromium's Local State file."""
    try:
        state = json.loads((user_data / "Local State").read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        state = {}
    profile_state = state.get("profile", {}) if isinstance(state, dict) else {}
    last_used = str(profile_state.get("last_used") or "Default")
    if (user_data / last_used).is_dir():
        return last_used
    for name in (profile_state.get("info_cache") or {}):
        if (user_data / str(name)).is_dir():
            return str(name)
    if (user_data / "Default").is_dir():
        return "Default"
    raise SystemExit(
        f"Could not find a Yandex profile folder inside: {user_data}\\n"
        "Open Yandex Browser, make sure your VPN extension is installed, then close the browser and retry."
    )


def ignore_yandex_cache(_directory: str, names: list[str]) -> set[str]:
    # Keep cookies, extension settings and local storage; omit bulky disposable caches.
    disposable = {
        "Cache", "Code Cache", "GPUCache", "GrShaderCache", "ShaderCache",
        "DawnCache", "Crashpad", "Crash Reports", "BrowserMetrics",
        "component_crx_cache", "Safe Browsing", "OptimizationGuidePredictionModels",
        "Media Cache", "SingletonLock", "SingletonCookie", "SingletonSocket",
    }
    return {name for name in names if name in disposable}


def prepare_yandex_profile() -> tuple[Path, str]:
    """
    Create an isolated copy of the user's selected Yandex profile.
    The original profile is not launched or modified by the scanner.
    """
    source = Path(os.environ.get("LOCALAPPDATA", "")) / "Yandex" / "YandexBrowser" / "User Data"
    target = PROFILE_DIR

    # Reuse an already prepared scanner profile on later launches.
    if (target / "Local State").is_file():
        try:
            active = selected_profile_name(target)
            if (target / active).is_dir():
                return target, active
        except SystemExit:
            pass

    if not (source / "Local State").is_file():
        raise SystemExit(
            f"Yandex profile data was not found at:\\n{source}\\n"
            "Open Yandex Browser at least once and make sure your VPN extension is installed."
        )

    profile_name = selected_profile_name(source)
    if yandex_is_running():
        raise SystemExit(
            "For the first setup, fully close Yandex Browser first (Ctrl+Shift+Q), "
            "including background processes. Then run start.bat again.\\n"
            "The scanner will copy the selected Yandex profile into its own folder; "
            "your normal Yandex profile will remain unchanged."
        )

    if target.exists():
        shutil.rmtree(target, ignore_errors=True)
    target.mkdir(parents=True, exist_ok=True)
    try:
        shutil.copy2(source / "Local State", target / "Local State")
        shutil.copytree(
            source / profile_name,
            target / profile_name,
            ignore=ignore_yandex_cache,
            dirs_exist_ok=True,
        )
    except Exception as exc:
        shutil.rmtree(target, ignore_errors=True)
        raise SystemExit(
            "Could not copy the Yandex profile. Close Yandex completely and run start.bat again. "
            f"Details: {type(exc).__name__}: {exc}"
        ) from exc

    log.info("Copied Yandex profile '%s' into an isolated scanner profile.", profile_name)
    log.info("The copy stays local in %s and is excluded from Git.", target)
    return target, profile_name


def load_config() -> dict[str, Any]:
    if not CONFIG_PATH.exists():
        CONFIG_PATH.write_text(EXAMPLE_PATH.read_text(encoding="utf-8"), encoding="utf-8")
        raise SystemExit("Created config.json. Paste your @BotFather token into it, save, and start again.")
    try:
        data = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as exc:
        raise SystemExit(f"Cannot read config.json: {exc}") from exc
    cfg = {**DEFAULTS, **data}
    token = str(cfg.get("bot_token", "")).strip()
    if not token or token == "PASTE_BOTFATHER_TOKEN_HERE":
        raise SystemExit("Set bot_token in config.json using @BotFather. Never share that token.")
    if not re.fullmatch(r"\d+:[A-Za-z0-9_-]+", token):
        raise SystemExit("The bot_token format looks invalid; copy it again from @BotFather.")
    cfg["bot_token"] = token
    return cfg

CFG = load_config()
BOT = Bot(token=CFG["bot_token"])
DP = Dispatcher()
PAGE: Page | None = None
CONTEXT: BrowserContext | None = None
SCANNING = False
LOCK = asyncio.Lock()
LAST_ERROR = ""
SEEN: set[str] = set()

def save_config() -> None:
    CONFIG_PATH.write_text(json.dumps(CFG, ensure_ascii=False, indent=2), encoding="utf-8")

def load_state() -> None:
    global SEEN
    try:
        data = json.loads(STATE_PATH.read_text(encoding="utf-8"))
        SEEN = set(str(x) for x in data.get("seen", []))
    except (OSError, json.JSONDecodeError, TypeError):
        SEEN = set()

def save_state() -> None:
    if len(SEEN) > 20000:
        SEEN.clear()
    STATE_PATH.write_text(json.dumps({"seen": sorted(SEEN)}, ensure_ascii=False, indent=2), encoding="utf-8")

async def admin_only(message: Message) -> bool:
    user = message.from_user
    if not user:
        return False
    admin_id = int(CFG.get("admin_id") or 0)
    if admin_id == 0 and message.chat.type == "private":
        CFG["admin_id"] = user.id
        save_config()
        log.info("First private /start registered as admin id %s", user.id)
        return True
    if user.id != int(CFG.get("admin_id") or 0):
        await message.answer("⛔ This scanner is private. Access denied.")
        return False
    return True

def make_slug(name: str) -> str:
    words = re.sub(r"[^A-Za-z0-9]+", " ", name).strip().split()
    return "".join(word[:1].upper() + word[1:] for word in words)

def make_link(collection: str, number: str, direct: str = "") -> str:
    if direct.startswith("https://t.me/nft/"):
        return direct
    num = re.sub(r"\D", "", str(number))
    slug = str(CFG.get("collection_slug") or "").strip() or make_slug(collection)
    return f"https://t.me/nft/{slug}-{num}" if slug and num else ""

def between_price(price: float) -> bool:
    return float(CFG["min_price"]) <= price <= float(CFG["max_price"])

CARD_SCAN_JS = r"""
() => {
  const vis = e => {
    if (!e || !e.isConnected) return false;
    const r = e.getBoundingClientRect(), s = getComputedStyle(e);
    return r.width > 0 && r.height > 0 && s.display !== 'none' && s.visibility !== 'hidden';
  };
  const norm = s => (s || '').replace(/\s+/g,' ').trim();
  document.querySelectorAll('[data-gs-card]').forEach(e => e.removeAttribute('data-gs-card'));
  const output = [], used = new Set();
  let idx = 0;
  for (const e of document.querySelectorAll('span,div,button,[role="button"]')) {
    if (!vis(e)) continue;
    const raw = norm(e.innerText || e.textContent);
    const m = raw.match(/^(?:⭐|★|🌟)?\s*(\d{2,7})\+?$/);
    if (!m) continue;
    const price = Number(m[1]);
    const host = e.closest('button,[role="button"]') || e.parentElement || e;
    const hasStar = /[⭐★🌟]/.test(raw) ||
      !!host.querySelector('svg,img,[class*="star" i],[aria-label*="star" i]');
    if (!hasStar) continue;
    let card = host;
    for (let k = 0; k < 8 && card.parentElement; k++) {
      const r = card.getBoundingClientRect(), t = norm(card.innerText || card.textContent);
      if (r.width >= 75 && r.width <= 380 && r.height >= 80 && r.height <= 460 &&
          t.length <= 280 && card.querySelector('img,canvas,video,svg')) break;
      card = card.parentElement;
    }
    if (!vis(card)) continue;
    const r = card.getBoundingClientRect();
    const txt = norm(card.innerText || card.textContent);
    if (r.width < 70 || r.width > 450 || r.height < 70 || r.height > 500 || txt.length > 350) continue;
    const all = txt + ' ' + [...card.querySelectorAll('img[alt]')].map(x => x.alt).join(' ');
    const match = all.match(/(?:#|№)\s*([0-9][0-9,]{3,})/);
    let number = match ? match[1].replace(/\D/g,'') : '';
    const anchor = card.querySelector('a[href*="t.me/nft/"]');
    const directLink = anchor ? anchor.href : '';
    if (!number && directLink) {
      const lm = directLink.match(/\/nft\/[^/]*?-(\d+)(?:$|[?#])/);
      if (lm) number = lm[1];
    }
    if (!number) continue;
    const key = number + ':' + price;
    if (used.has(key)) continue;
    used.add(key);
    idx += 1;
    const marker = 'gift-' + idx + '-' + number;
    card.setAttribute('data-gs-card', marker);
    output.push({marker, number, price, directLink, text: txt.slice(0,220)});
  }
  return output;
}
"""

SCROLL_JS = r"""
() => {
  const els = [...document.querySelectorAll('div,section,main')].filter(el => {
    const r = el.getBoundingClientRect();
    return r.width > 180 && r.height > 180 &&
      r.left > window.innerWidth * .20 && r.right < window.innerWidth * .98 &&
      el.scrollHeight > el.clientHeight + 100 &&
      /auto|scroll/.test(getComputedStyle(el).overflowY);
  });
  if (!els.length) return false;
  els.sort((a,b) => a.getBoundingClientRect().width*a.getBoundingClientRect().height -
                     b.getBoundingClientRect().width*b.getBoundingClientRect().height);
  const target = els[0], old = target.scrollTop;
  target.scrollTop = Math.min(target.scrollHeight, old + Math.max(180, target.clientHeight*.7));
  return target.scrollTop > old;
}
"""

def detail_value(text: str, labels: tuple[str, ...]) -> str:
    lines = [re.sub(r"\s+", " ", line).strip() for line in text.splitlines()]
    lines = [line for line in lines if line]
    known_labels = ("Модель", "Фон", "Узор", "Model", "Backdrop", "Pattern", "Наличие", "Стоимость", "Owner")
    for i, line in enumerate(lines):
        for label in labels:
            m = re.match(rf"^{re.escape(label)}\s*:?\s*(.*)$", line, re.I)
            if m:
                val = m.group(1).strip()
                if val:
                    return val
                if i + 1 < len(lines) and not any(
                    re.match(rf"^{re.escape(x)}\b", lines[i+1], re.I) for x in known_labels
                ):
                    return lines[i+1]
    return ""

async def read_details(page: Page, marker: str) -> dict[str, str]:
    result = {"model": "", "backdrop": "", "pattern": "", "link": ""}
    try:
        await page.locator(f'[data-gs-card="{marker}"]').click(timeout=2500)
        await page.wait_for_timeout(350)
        text = await page.locator("body").inner_text(timeout=5000)
        result["model"] = detail_value(text, ("Модель", "Model"))
        result["backdrop"] = detail_value(text, ("Фон", "Backdrop"))
        result["pattern"] = detail_value(text, ("Узор", "Pattern"))
        result["link"] = await page.evaluate("""() => {
          const a = [...document.querySelectorAll('a[href*="t.me/nft/"]')].find(x => x.getClientRects().length);
          return a ? a.href : '';
        }""")
    except Exception as exc:
        log.debug("Cannot read gift details %s: %s", marker, exc)
    finally:
        try:
            await page.keyboard.press("Escape")
            await page.wait_for_timeout(200)
        except Exception:
            pass
    return result

def filter_matches(value: str, wanted: str) -> bool:
    return not wanted.strip() or wanted.casefold() in value.casefold()

async def scan_once(force: bool = False) -> tuple[int, int, str]:
    global LAST_ERROR
    if PAGE is None or PAGE.is_closed():
        return 0, 0, "Browser page is not available. Use /openweb or restart the app."
    async with LOCK:
        try:
            body = await PAGE.locator("body").inner_text(timeout=5000)
            collection = str(CFG.get("collection", "")).strip()
            if collection and collection.casefold() not in body.casefold():
                return 0, 0, f'Collection "{collection}" is not visible. Open its resale list in the browser first.'
            cards = await PAGE.evaluate(CARD_SCAN_JS)
            if not cards:
                return 0, 0, "No readable gift cards detected. Keep the resale grid open; the Web DOM may differ."
            sent = 0
            examined = 0
            cap = max(1, min(15, int(CFG.get("max_alerts_per_scan", 5))))
            model = str(CFG.get("model", "")).strip()
            backdrop = str(CFG.get("backdrop", "")).strip()
            pattern = str(CFG.get("pattern", "")).strip()
            needs_details = bool(model or backdrop or pattern)
            for card in cards[:80]:
                price = float(card["price"])
                if not between_price(price):
                    continue
                link = make_link(collection or "Telegram Gift", card["number"], card.get("directLink", ""))
                if not link:
                    continue
                details = {"model": "", "backdrop": "", "pattern": "", "link": ""}
                if needs_details:
                    details = await read_details(PAGE, card["marker"])
                    if not any(details[k] for k in ("model", "backdrop", "pattern")):
                        continue
                    if not filter_matches(details["model"], model): continue
                    if not filter_matches(details["backdrop"], backdrop): continue
                    if not filter_matches(details["pattern"], pattern): continue
                    link = make_link(collection or "Telegram Gift", card["number"], details.get("link") or card.get("directLink", "")) or link
                examined += 1
                key = f"{link}|{int(price)}"
                if not force and key in SEEN:
                    continue
                if sent >= cap:
                    continue
                lines = [
                    "🎯 <b>Gift matches your filters</b>", "",
                    f"🎁 {collection or 'Telegram Gift'} #{card['number']}",
                    f"💰 Price: <b>{int(price)} ⭐</b>",
                ]
                if details["model"]: lines.append(f"🧩 Model: {details['model']}")
                if details["backdrop"]: lines.append(f"🎨 Backdrop: {details['backdrop']}")
                if details["pattern"]: lines.append(f"🔣 Pattern: {details['pattern']}")
                lines += ["", "Manual purchase only; no automatic purchase is implemented."]
                kb = InlineKeyboardMarkup(inline_keyboard=[[
                    InlineKeyboardButton(text="⭐ Open gift", url=link)
                ]])
                await BOT.send_message(int(CFG["admin_id"]), "\n".join(lines),
                                       reply_markup=kb, parse_mode="HTML",
                                       disable_web_page_preview=True)
                SEEN.add(key)
                sent += 1
            save_state()
            LAST_ERROR = ""
            try:
                scrolled = await PAGE.evaluate(SCROLL_JS)
                scroll_note = "scrolled list" if scrolled else "could not find a scroll container"
            except Exception:
                scroll_note = "scroll status unknown"
            return sent, examined, f"Visible cards: {len(cards)}. Alerts sent: {sent}. {scroll_note}."
        except Exception as exc:
            LAST_ERROR = f"{type(exc).__name__}: {exc}"
            log.exception("Scan failed")
            return 0, 0, LAST_ERROR

async def scan_loop() -> None:
    while True:
        if SCANNING:
            sent, count, info = await scan_once()
            if sent:
                log.info("%s", info)
            elif "No readable" in info or "not visible" in info or LAST_ERROR:
                log.info("%s", info)
        await asyncio.sleep(max(10, min(300, int(CFG.get("interval_seconds", 15)))))

def summary() -> str:
    return (
        f"Коллекция: {CFG.get('collection') or 'не задана'}\n"
        f"Цена: {CFG['min_price']}–{CFG['max_price']} ⭐\n"
        f"Модель: {CFG.get('model') or 'любая'}\n"
        f"Фон: {CFG.get('backdrop') or 'любой'}\n"
        f"Узор: {CFG.get('pattern') or 'любой'}\n"
        f"Интервал: {CFG['interval_seconds']} сек.\n"
        f"Мониторинг: {'🟢 включён' if SCANNING else '⏸ выключен'}"
    )

@DP.message(Command("start"))
async def start(message: Message) -> None:
    if not await admin_only(message): return
    await message.answer(
        "⭐ <b>Telegram Web Gift Scanner</b>\n"
        "Сканирует карточки в открытом списке перепродаж Telegram Web и присылает ссылки. Покупок нет.\n\n"
        + summary() + "\n\nUse /help for commands.",
        parse_mode="HTML",
    )

@DP.message(Command("help"))
async def help_command(message: Message) -> None:
    if not await admin_only(message): return
    await message.answer(
        "Настройки:\n"
        "/collection Chill Flame — коллекция, которая должна быть открыта в браузере\n"
        "/slug ChillFlame — задать slug ссылки; /slug auto — автоматический\n"
        "/price 500 — максимум ⭐\n/minprice 100 — минимум ⭐\n"
        "/model Dungeon — модель; /model off — сброс\n"
        "/backdrop Burgundy — фон; /backdrop off — сброс\n"
        "/pattern Holy Grail — узор; /pattern off — сброс\n"
        "/interval 15 — интервал 10–300 секунд\n\n"
        "Сканирование:\n/scan — сканировать сейчас\n/startscan — включить\n/stopscan — остановить\n"
        "/status — статус\n/openweb — открыть Telegram Web\n/resetseen — сброс истории уведомлений"
    )

@DP.message(Command("collection"))
async def collection_command(message: Message, command: CommandObject) -> None:
    if not await admin_only(message): return
    value = (command.args or "").strip()
    if not value:
        await message.answer("Example: /collection Chill Flame")
        return
    CFG["collection"] = value
    save_config()
    await message.answer(
        f"✅ Collection filter: {value}\n"
        "Now open this collection's resale list in the controlled browser. This command does not click Telegram's menus."
    )

@DP.message(Command("slug"))
async def slug_command(message: Message, command: CommandObject) -> None:
    if not await admin_only(message): return
    value = (command.args or "").strip()
    if not value:
        await message.answer("Example: /slug ChillFlame or /slug auto")
        return
    CFG["collection_slug"] = "" if value.casefold() == "auto" else value
    save_config()
    await message.answer("✅ Slug override: " + (CFG["collection_slug"] or "automatic"))

@DP.message(Command("price"))
async def price_command(message: Message, command: CommandObject) -> None:
    if not await admin_only(message): return
    try:
        value = int((command.args or "").strip())
        if value < int(CFG["min_price"]) or value < 0: raise ValueError
    except ValueError:
        await message.answer("Price must be an integer not below the minimum. Example: /price 500")
        return
    CFG["max_price"] = value
    save_config()
    await message.answer(f"✅ Maximum price: {value} ⭐")

@DP.message(Command("minprice"))
async def minprice_command(message: Message, command: CommandObject) -> None:
    if not await admin_only(message): return
    try:
        value = int((command.args or "").strip())
        if value < 0 or value > int(CFG["max_price"]): raise ValueError
    except ValueError:
        await message.answer("Enter a number from 0 to the maximum price. Example: /minprice 100")
        return
    CFG["min_price"] = value
    save_config()
    await message.answer(f"✅ Minimum price: {value} ⭐")

async def set_filter(message: Message, command: CommandObject, key: str, label: str) -> None:
    if not await admin_only(message): return
    value = (command.args or "").strip()
    if value.casefold() == "off": value = ""
    CFG[key] = value
    save_config()
    await message.answer(f"✅ {label}: {value or 'any'}")

@DP.message(Command("model"))
async def model_command(message: Message, command: CommandObject) -> None:
    await set_filter(message, command, "model", "Model")

@DP.message(Command("backdrop"))
async def backdrop_command(message: Message, command: CommandObject) -> None:
    await set_filter(message, command, "backdrop", "Backdrop")

@DP.message(Command("pattern"))
async def pattern_command(message: Message, command: CommandObject) -> None:
    await set_filter(message, command, "pattern", "Pattern")

@DP.message(Command("interval"))
async def interval_command(message: Message, command: CommandObject) -> None:
    if not await admin_only(message): return
    try:
        value = int((command.args or "").strip())
        if not 10 <= value <= 300: raise ValueError
    except ValueError:
        await message.answer("Interval must be between 10 and 300 seconds. Example: /interval 15")
        return
    CFG["interval_seconds"] = value
    save_config()
    await message.answer(f"✅ Interval: {value} seconds")

@DP.message(Command("scan"))
async def scan_command(message: Message) -> None:
    if not await admin_only(message): return
    await message.answer("🔎 Scanning the current page...")
    sent, count, info = await scan_once(force=True)
    await message.answer(f"{info}\nAlerts sent: {sent}; matching cards processed: {count}.")

@DP.message(Command("startscan"))
async def startscan_command(message: Message) -> None:
    global SCANNING
    if not await admin_only(message): return
    SCANNING = True
    await message.answer("🟢 Monitoring enabled. Keep the wanted resale list open.\n\n" + summary())

@DP.message(Command("stopscan"))
async def stopscan_command(message: Message) -> None:
    global SCANNING
    if not await admin_only(message): return
    SCANNING = False
    await message.answer("⏸ Monitoring stopped.")

@DP.message(Command("pause"))
async def pause_command(message: Message) -> None:
    await stopscan_command(message)

@DP.message(Command("resume"))
async def resume_command(message: Message) -> None:
    await startscan_command(message)

@DP.message(Command("status"))
async def status_command(message: Message) -> None:
    if not await admin_only(message): return
    url = PAGE.url if PAGE and not PAGE.is_closed() else "no page"
    await message.answer(summary() + f"\nBrowser URL: {url}\n" + (f"Last error: {LAST_ERROR}" if LAST_ERROR else ""))

@DP.message(Command("openweb"))
async def openweb_command(message: Message) -> None:
    global PAGE, LAST_ERROR
    if not await admin_only(message): return
    if CONTEXT is None:
        await message.answer("Browser is starting. Try again in a few seconds.")
        return
    if PAGE is None or PAGE.is_closed():
        PAGE = await CONTEXT.new_page()
    try:
        await PAGE.goto("https://web.telegram.org/k/", wait_until="domcontentloaded", timeout=45000)
        LAST_ERROR = ""
        await message.answer("Telegram Web opened. Sign in if needed, then open a gift resale list.")
    except Exception as exc:
        LAST_ERROR = f"{type(exc).__name__}: {exc}"
        log.warning("Telegram Web did not load: %s", LAST_ERROR)
        await message.answer(
            "Не удалось загрузить Telegram Web. Проверь, что VPN-расширение включено в окне Яндекс Браузера, "
            "и повтори /openweb.\n\n"
            f"Ошибка: {type(exc).__name__}: {exc}"
        )

@DP.message(Command("resetseen"))
async def resetseen_command(message: Message) -> None:
    if not await admin_only(message): return
    SEEN.clear()
    save_state()
    await message.answer("✅ Duplicate history cleared.")

async def main() -> None:
    global PAGE, CONTEXT, LAST_ERROR
    load_state()
    executable = find_yandex_executable()
    profile_dir, profile_name = prepare_yandex_profile()
    log.info("No my.telegram.org API ID/Hash is used. Sign in manually in the browser.")
    log.info("Launching Yandex Browser so its VPN extension can be used.")
    async with async_playwright() as p:
        CONTEXT = await p.chromium.launch_persistent_context(
            user_data_dir=str(profile_dir),
            executable_path=str(executable),
            headless=False,
            viewport=None,
            # Playwright disables extensions by default; re-enable the extensions in the copied Yandex profile.
            ignore_default_args=["--disable-extensions"],
            args=[f"--profile-directory={profile_name}", "--start-maximized"],
        )
        PAGE = CONTEXT.pages[0] if CONTEXT.pages else await CONTEXT.new_page()
        if PAGE.url == "about:blank":
            try:
                await PAGE.goto("https://web.telegram.org/k/", wait_until="domcontentloaded", timeout=45000)
            except Exception as exc:
                LAST_ERROR = f"{type(exc).__name__}: {exc}"
                log.warning(
                    "Telegram Web did not load at startup: %s. The bot will still start; "
                    "enable the VPN extension in the Yandex window, then send /openweb.",
                    LAST_ERROR,
                )
        bot_task = asyncio.create_task(DP.start_polling(BOT, drop_pending_updates=True))
        scan_task = asyncio.create_task(scan_loop())
        log.info("Yandex Browser opened. Make sure its VPN extension is connected, sign in if asked, and open the gift resale list.")
        try:
            await asyncio.gather(bot_task, scan_task)
        finally:
            for task in (bot_task, scan_task):
                task.cancel()
            await CONTEXT.close()
            await BOT.session.close()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Scanner stopped.")
        sys.exit(0)
