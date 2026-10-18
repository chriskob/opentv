/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.torrent

import java.util.Locale

/**
 * The page a phone sees after scanning the QR code on the Torrents screen.
 *
 * Deliberately one self-contained document: no fonts, scripts, images or stylesheets from
 * anywhere else. The phone is talking to a television over the local network, which may have no
 * route to the internet at all, and a page that needs a CDN would simply fail. A plain form POST
 * is used rather than JavaScript so the page also works when the phone's browser is old or
 * aggressive about scripting.
 */
object PhoneAddPage {

    /** What the TV did with a link the phone submitted. */
    sealed interface Result {
        data class Added(val title: String?) : Result
        data class Refused(val reason: String) : Result
        data object InvalidLink : Result
        data object EngineUnavailable : Result
    }

    /** The submission form, optionally with an error above it and the shortcut recipe below it. */
    fun form(token: String, error: String?, shortcutUrl: String? = null): String {
        val copy = copy()
        return page(
            title = copy.title,
            language = language(),
            content = """
                <h1>${escape(copy.title)}</h1>
                <p class="sub">${escape(copy.sub)}</p>
                ${if (error != null) "<div class=\"error\" role=\"alert\">${escape(error)}</div>" else ""}
                <form method="post" action="/submit?t=${escape(token)}" autocomplete="off">
                  <input type="hidden" name="t" value="${escape(token)}">
                  <label for="link">${escape(copy.label)}</label>
                  <textarea id="link" name="link" rows="4" required autofocus
                            autocapitalize="off" autocorrect="off" spellcheck="false"
                            placeholder="${escape(copy.placeholder)}"></textarea>
                  <p class="hint">${escape(copy.hint)}</p>
                  <button type="submit">${escape(copy.button)}</button>
                </form>
                ${shortcutSection(copy, shortcutUrl)}
                <p class="privacy">${escape(copy.privacy)}</p>
            """.trimIndent(),
        )
    }

    /**
     * The iPhone recipe, shown only when the server knows its own one-shot address.
     *
     * A `<details>` block rather than a second page: the phone reading it is the phone about to have
     * the shortcut built on it, and the address it needs is already on screen. Collapsed by default
     * so the paste form stays the page's one obvious action.
     */
    private fun shortcutSection(copy: Copy, shortcutUrl: String?): String {
        if (shortcutUrl.isNullOrBlank()) return ""
        return """
            <details class="shortcut">
              <summary>${escape(copy.shortcutTitle)}</summary>
              <p class="hint">${escape(copy.shortcutIntro)}</p>
              <code>${escape(shortcutUrl)}</code>
              <ol>
                <li>${escape(copy.shortcutStep1)}</li>
                <li>${escape(copy.shortcutStep2)}</li>
                <li>${escape(copy.shortcutStep3)}</li>
              </ol>
              <p class="hint">${escape(copy.shortcutNote)}</p>
            </details>
        """.trimIndent()
    }

    /** The page after a link was accepted; the TV is already starting it. */
    fun done(token: String, title: String?): String {
        val copy = copy()
        val line = if (title.isNullOrBlank()) {
            copy.started
        } else {
            String.format(Locale.getDefault(), copy.startedWith, title)
        }
        return page(
            title = copy.sentTitle,
            language = language(),
            content = """
                <div class="tick" aria-hidden="true">&#10003;</div>
                <h1>${escape(copy.sentTitle)}</h1>
                <p class="sub">${escape(line)}</p>
                <a class="again" href="/?t=${escape(token)}">${escape(copy.again)}</a>
            """.trimIndent(),
        )
    }

    /** The message to put above the form when [result] was not accepted. */
    fun errorFor(result: Result): String? = when (result) {
        is Result.Added -> null
        is Result.Refused -> result.reason
        Result.InvalidLink -> copy().invalid
        Result.EngineUnavailable -> copy().engineDown
    }

    fun missingLink(): String = copy().missing

    private data class Copy(
        val title: String,
        val sub: String,
        val label: String,
        val placeholder: String,
        val hint: String,
        val button: String,
        val privacy: String,
        val sentTitle: String,
        val started: String,
        val startedWith: String,
        val again: String,
        val invalid: String,
        val engineDown: String,
        val missing: String,
        val shortcutTitle: String,
        val shortcutIntro: String,
        val shortcutStep1: String,
        val shortcutStep2: String,
        val shortcutStep3: String,
        val shortcutNote: String,
    )

    private fun language(): String = if (Locale.getDefault().language == "pl") "pl" else "en"

    private fun copy(): Copy = if (language() == "pl") {
        Copy(
            title = "Dodaj do OpenTV",
            sub = "Wklej link magnet — odtwarzanie zacznie się na telewizorze.",
            label = "Link magnet lub adres .torrent",
            placeholder = "magnet:?xt=urn:btih:…",
            hint = "Przytrzymaj pole, aby wkleić. Działa magnet i link .torrent.",
            button = "Wyślij do telewizora",
            privacy = "Tę stronę serwuje Twój telewizor w domowej sieci. Link trafia prosto do " +
                "telewizora — nigdzie indziej.",
            sentTitle = "Wysłano do telewizora",
            started = "Zaczyna się odtwarzać na telewizorze.",
            startedWith = "%s zaczyna się odtwarzać na telewizorze.",
            again = "Dodaj kolejny link",
            invalid = "To nie wygląda na link magnet ani adres .torrent.",
            engineDown = "Silnik torrentów w telewizorze nie działa. Otwórz Torrents na " +
                "telewizorze i spróbuj ponownie.",
            missing = "Najpierw wklej link.",
            shortcutTitle = "iPhone: wysyłaj linki bez otwierania tej strony",
            shortcutIntro = "Ustaw raz w aplikacji Skróty. W skrócie użyj tego adresu:",
            shortcutStep1 = "Nowy skrót; w jego ustawieniach włącz „Pokaż w arkuszu " +
                "udostępniania” dla adresów URL i tekstu.",
            shortcutStep2 = "Dodaj akcję „Tekst” z tym adresem i wstaw zaraz za nim " +
                "„Wejście skrótu”.",
            shortcutStep3 = "Dodaj akcję „Pobierz zawartość adresu URL”. Udostępnienie magneta " +
                "skrótowi wyśle go do telewizora.",
            shortcutNote = "Aby kopiować magnet i uruchamiać skrót z ekranu głównego, zamiast " +
                "wejścia skrótu odczytaj schowek. Telewizor musi mieć otwartą aplikację OpenTV — " +
                "dowolny ekran wystarczy.",
        )
    } else {
        Copy(
            title = "Add to OpenTV",
            sub = "Paste a magnet link — it starts playing on the TV.",
            label = "Magnet link or .torrent URL",
            placeholder = "magnet:?xt=urn:btih:…",
            hint = "Long-press the field to paste. A magnet or a .torrent link both work.",
            button = "Send to TV",
            privacy = "This page is served by your television over your own network. The link " +
                "goes straight to the TV — nowhere else.",
            sentTitle = "Sent to the TV",
            started = "It's starting on the TV.",
            startedWith = "%s is starting on the TV.",
            again = "Add another link",
            invalid = "That doesn't look like a magnet link or a .torrent URL.",
            engineDown = "The TV's torrent engine isn't running. Open Torrents on the TV and " +
                "try again.",
            missing = "Paste a link first.",
            shortcutTitle = "iPhone: send links without opening this page",
            shortcutIntro = "Set this up once in the Shortcuts app. In the shortcut, use this " +
                "address:",
            shortcutStep1 = "New shortcut. In its settings, turn on \"Show in Share Sheet\", " +
                "accepting URLs and Text.",
            shortcutStep2 = "Add a Text action with that address, and the Shortcut Input right " +
                "after it.",
            shortcutStep3 = "Add \"Get Contents of URL\". Sharing a magnet to the shortcut " +
                "sends it to the TV.",
            shortcutNote = "To copy a magnet and run it from the Home Screen instead, read the " +
                "Clipboard in place of the share input. The TV must have OpenTV open — any screen " +
                "will do.",
        )
    }

    private fun page(title: String, language: String, content: String): String = """
        <!DOCTYPE html>
        <html lang="$language">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
        <meta name="robots" content="noindex, nofollow">
        <meta name="theme-color" content="#1c232c">
        <title>${escape(title)}</title>
        <style>
          :root { --bg:#1c232c; --surface:#242b33; --line:#38424c; --text:#edecee;
                  --muted:#b6c5d2; --accent:#00bcd4; --on-accent:#15141b; --err:#ff6b6b; }
          * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
          body { margin:0; background:var(--bg); color:var(--text); padding:26px 18px 64px;
                 font:16px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif; }
          .wrap { max-width:460px; margin:0 auto; }
          h1 { font-size:1.6rem; line-height:1.25; margin:0 0 8px; letter-spacing:-.02em; }
          .sub { color:var(--muted); margin:0 0 22px; }
          label { display:block; font-size:.9rem; font-weight:600; margin:0 0 8px; }
          textarea {
            width:100%; padding:14px; font-size:16px; line-height:1.45; resize:vertical;
            background:var(--surface); color:var(--text); border:1px solid var(--line);
            border-radius:12px; font-family:ui-monospace,SFMono-Regular,Menlo,monospace;
          }
          textarea:focus { outline:none; border-color:var(--accent); }
          .hint { color:var(--muted); font-size:.85rem; margin:8px 0 0; }
          button, .again {
            display:block; width:100%; margin-top:24px; padding:16px; font-size:17px;
            font-weight:600; text-align:center; text-decoration:none; border:0; border-radius:12px;
            background:var(--accent); color:var(--on-accent);
          }
          button:active, .again:active { opacity:.85; }
          .error { background:#2a1416; border:1px solid var(--err); color:#ffc9c9;
                   padding:12px 14px; border-radius:10px; margin-bottom:18px; font-size:.92rem; }
          .privacy { color:var(--muted); font-size:.82rem; margin-top:28px; }
          .tick { width:52px; height:52px; border-radius:50%; background:var(--accent);
                  color:var(--on-accent); font-size:30px; font-weight:700; line-height:52px;
                  text-align:center; margin:8px 0 18px; }
          details.shortcut { margin-top:26px; background:var(--surface); border:1px solid var(--line);
                             border-radius:12px; padding:14px; }
          details.shortcut summary { font-weight:600; cursor:pointer; }
          details.shortcut code { display:block; margin:12px 0; padding:12px; background:var(--bg);
                                  border:1px solid var(--line); border-radius:10px; font-size:.82rem;
                                  font-family:ui-monospace,SFMono-Regular,Menlo,monospace;
                                  word-break:break-all; }
          details.shortcut ol { margin:12px 0 0; padding-left:22px; color:var(--muted);
                                font-size:.9rem; }
          details.shortcut li { margin:6px 0; }
          details.shortcut .hint { margin:10px 0 0; }
        </style>
        </head>
        <body><div class="wrap">
        $content
        </div></body>
        </html>
    """.trimIndent()

    /** Minimal HTML escaping. Everything interpolated into the page goes through here. */
    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
