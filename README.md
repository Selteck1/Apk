# Telegram Web Gift Scanner (Windows)

A Windows helper that watches cards exposed on the currently open Telegram Web collectible-gift resale page and notifies your private Telegram bot. It has no automated purchasing.

## Important limits
This is a user-interface automation MVP, not an official Telegram market API. Telegram Web can change its DOM, not every gift card exposes its number/price as readable text, and a scanner can only inspect items loaded in the browser. Keep the wanted gift type's resale list open in the controlled browser. The collection command is a filter/check; it does not navigate Telegram menus for you.

When a number is detected, the program forms a link using the common pattern https://t.me/nft/CollectionSlug-Number. Check the first generated link against Telegram's Copy Link menu before relying on it. Unknown cards are skipped rather than fabricating a number. There are no automated purchases.

## Setup
1. Install Python 3.11 or newer on Windows.
2. Create a notification bot with @BotFather and copy its bot token.
3. Extract the ZIP to a folder you control.
4. Run start.bat. First run creates config.json and opens it in Notepad.
5. Paste the token into the bot_token field. Save and close Notepad. Never share config.json.
6. The app installs dependencies and Playwright Chromium, then launches a controlled browser.
7. Sign in to Telegram Web manually in that browser window and open the desired collection's resale list.
8. Open your notification bot and send /start. The first account to start it claims admin access when admin_id is 0.

## Commands
- /help: list commands
- /collection Chill Flame: expected collection name in the page
- /slug ChillFlame: custom slug used in gift links; /slug auto resets to an automatically generated slug
- /price 500: maximum price in Stars
- /minprice 100: minimum price in Stars
- /model Dungeon: model filter; /model off clears it
- /backdrop Burgundy: backdrop/color filter; /backdrop off clears it
- /pattern Holy Grail: pattern filter; /pattern off clears it
- /interval 15: scan interval from 10 to 300 seconds
- /scan: scan now
- /startscan and /stopscan: start or stop monitoring
- /status: configuration and browser status
- /openweb: open Telegram Web
- /resetseen: clear duplicate-suppression history

## Privacy
The browser profile stores the Telegram Web login locally in browser-profile/. Keep it private and never upload/share it. config.json contains the bot token and must remain private. Both are excluded from Git. The app will not ask for your Telegram password or login code in chat.

## Troubleshooting
If the browser asks you to log in, do so manually. If /scan says the collection is not open, navigate to its resale list in the controlled browser. If no cards are detected, Telegram Web may have changed its DOM or not exposed the data as text. This MVP cannot guarantee instant discovery or full-market coverage.
