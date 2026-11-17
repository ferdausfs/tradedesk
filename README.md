# TradeDesk

Native Android trading terminal (crypto v1) — Kotlin + Jetpack Compose, no WebView.
Live data: Binance public market data (REST + WebSocket), no API key needed.

- Candlestick + volume chart (custom Canvas renderer): pinch-zoom, pan, crosshair
- Timeframes: 1m 5m 15m 1h 4h 1d 1w
- Overlays: EMA(21), Bollinger(20,2)
- Watchlist with live prices, symbol search
- Local price alerts with notifications
- APK releases built automatically by GitHub Actions on every push to main
