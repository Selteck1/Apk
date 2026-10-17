# Telegram Web Gift Scanner (Windows)

A Windows helper that watches the gift cards currently loaded on a Telegram Web resale page and sends matching links to your private Telegram notification bot. It does not buy anything automatically.

## Important limits
This is a user-interface automation MVP, not an official Telegram market API. Telegram Web can change its DOM, not every gift card exposes its number/price as readable text, and the scanner only sees cards loaded in the current page. Keep the desired collection's resale list open in the browser. The collection command filters/checks the page; it does not navigate Telegram menus for you.

When a number is detected, the program forms a link using the common pattern `https://t.me/nft/CollectionSlug-Number`. Verify the first generated link against Telegram's Copy Link menu before relying on it. Unknown cards are skipped rather than fabricating a number. There are no automated purchases.

## Setup
1. Use Python 3.12 on Windows. The launcher checks common Python 3.12 install paths and tries to install it with `pymanager` if available.
2. Create a notification bot with @BotFather and copy its token. Do not share this token.
3. Extract the ZIP to a folder you control.
4. **Before the first launch only, completely close Yandex Browser** (press Ctrl+Shift+Q, then make sure it is no longer running in the background). This is needed so the app can make a consistent copy of the selected Yandex profile.
5. Run `start.bat`. On first run it creates `config.json` and opens it in Notepad. Paste your @BotFather token into the `bot_token` field, save, and close Notepad.
6. The scanner locates the installed Yandex Browser and copies the selected local Yandex profile into `yandex-browser-profile/`. It uses that separate copy so it can preserve the installed VPN extension and its local settings without taking over or changing your normal browser profile.
7. In the Yandex Browser window opened by the scanner, make sure the VPN extension is switched on. If Telegram Web asks you to sign in, do so manually. Then open the collection's resale list.
8. Open your notification bot in Telegram and send `/start`. If `admin_id` is `0`, the first private account to run `/start` becomes the bot admin.

On later starts, the scanner reuses its own copied profile. Your usual Yandex window can stay separate. If the VPN extension requires a fresh sign-in in the scanner window, sign in there once and reconnect it.

## Commands
- `/help`: list commands
- `/collection Chill Flame`: expected collection name in the page
- `/slug ChillFlame`: custom slug used in gift links; `/slug auto` resets it to an automatically generated slug
- `/price 500`: maximum price in Stars
- `/minprice 100`: minimum price in Stars
- `/model Dungeon`: model filter; `/model off` clears it
- `/backdrop Burgundy`: backdrop/color filter; `/backdrop off` clears it
- `/pattern Holy Grail`: pattern filter; `/pattern off` clears it
- `/interval 15`: scan interval from 10 to 300 seconds
- `/scan`: scan now
- `/startscan` and `/stopscan`: start or stop monitoring
- `/status`: configuration and browser status
- `/openweb`: retry opening Telegram Web after enabling the VPN extension
- `/resetseen`: clear duplicate-suppression history

## Privacy
The copied browser profile stores Telegram Web login data and VPN-extension state locally. Treat `yandex-browser-profile/` as private and never upload or share it. `config.json` contains the bot token and must remain private. These files are excluded from Git. The app will never ask for your Telegram password or login code in the bot chat.

## Troubleshooting
- If the first start says to close Yandex Browser, close every Yandex window with Ctrl+Shift+Q and check that it has exited in the background; then start again.
- If Telegram Web times out, enable/connect the VPN extension in the scanner's Yandex window and send `/openweb` to retry. A browser extension routes traffic only in the browser where it is running; the scanner now launches Yandex Browser instead of Playwright's separate Chromium.
- If the extension or Telegram session was not copied, sign in/connect it in the scanner's Yandex window once. Your everyday Yandex profile is not changed by the scanner.
- If `/scan` says the collection is not open, navigate to its resale list in the controlled browser. If no cards are detected, Telegram Web may have changed its DOM or not exposed the data as text. This MVP cannot guarantee instant discovery or full-market coverage.
