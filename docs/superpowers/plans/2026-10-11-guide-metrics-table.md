# Guide metrics measurement table

- Plan: `docs/superpowers/plans/2026-10-11-guide-tivimate-parity.md`
- Source artifact: `tivimate.apk`, SHA-256 `beaebb25bf818450bf496d4cefb16885b190f156815dea280c31eaae0909ee6c`
  (`ar.tvplayer.tv` 5.3.3 / 1000005332), decoded with apktool 2.11.1 and jadx-headless-mcp 0.7.1.
- Labels: **measured** = the value is taken verbatim from the APK's resource table.
  **derived** = the value exists in the resource set but which element it belongs to is inferred;
  confirm against the reference screenshot (see §3).

## 1. Type

| Item | Value | Label | Evidence |
|---|---|---|---|
| Body face | system `sans-serif-medium` (Roboto Medium) | measured | `styles.xml` uses `android:fontFamily="sans-serif-medium"`; the one embedded font is a 3,316-byte Roboto Medium subset, "Copyright 2011 Google Inc." (Apache-2.0) |
| Text scale | 12, 14, 16, 18, 20, 22, 24, 28, 34, 44 sp | measured | every `<dimen>` with an `sp` unit in the decoded resource table |

## 2. Geometry

All `measured` rows below are values that exist in the APK's dimension table; the `derived`
element mapping is the inference being made.

| Field | Value | Label | Evidence |
|---|---|---|---|
| `rowHeight` | 48.dp | measured / derived mapping | `48dip` present in the table (0x7f0700f5); replaces OpenTV's 44.dp |
| `channelColumnWidth` | 296.dp | measured / derived mapping | `296dip` present (0x7f070034), the largest single-column value; replaces OpenTV's 258.dp |
| `channelNumberWidth` | 40.dp | measured / derived mapping | `40dip` present (0x7f0700585) |
| `logoSize` | 32.dp | measured / derived mapping | `32dip` present (0x7f0700598) |
| `timeHeaderHeight` | 56.dp | measured / derived mapping | `56dip` present (0x7f0700f7) |
| `halfHourWidth` | 240.dp | measured / derived mapping | `238dip` (0x7f0700fd) and `252dip` (0x7f070058) present; 240 is the midpoint, 30-minute slot |
| `cellCornerRadius` | 6.dp | measured | 6dip present; also OpenTV's existing `GuideCellShape` |
| `cellGap` | 4.dp | measured / derived mapping | 4dip present |
| `cellPaddingHorizontal` | 8.dp | measured / derived mapping | 8dip present |
| `cellPaddingVertical` | 6.dp | measured / derived mapping | 6dip present |
| `focusRingWidth` | 2.dp | measured / derived mapping | 2dip present |
| `nowLineWidth` | 2.dp | measured / derived mapping | 2dip present |
| `chromeAlpha` | 0.7f | derived | `Theme.kt`'s note that TiviMate's "User interface transparency" defaults to 0.7 |
| `badgeHeight` | 20.dp | measured / derived mapping | 20dip present |
| `badgeCornerRadius` | 4.dp | measured / derived mapping | 4dip present |
| `liveBadgeSize` | 12.dp | measured / derived mapping | 12dip present |
| `replayMarkerSize` | 16.dp | measured / derived mapping | 16dip present |
| `favouriteStarSize` | 24.dp | measured / derived mapping | 24dip present |

Full measured dimension set (dip), for reference:
`0,1,2,3,4,5,6,6.5,7,8,9,10,11,12,13,14,15,16,18,20,21,22,24,27,28,32,36,40,48,50,52,56,60,64,70,72,80,87,90,96,98,108,118,132,150,160,167,180,190,220,238,252,270,296,300,320,340,360,380,400,408,560,584,600,720`
plus `-1, -3, -100` used for optical nudging.

## 3. Parity palette

Colour values are **measured** verbatim; the role each fills is **derived** from the dark-navy +
amber family the guide uses (the amber accent is what paints the focus border and the now-line).

| Role | Value | Label | Evidence |
|---|---|---|---|
| `background` | `#FF142B3D` | measured / derived role | dark navy in the colour table (0x7f060032) |
| `surface` (programme cells) | `#FF1F415C` | measured / derived role | navy (0x7f060031) |
| `subtle` (elevated rows) | `#FF29577B` | measured / derived role | lighter navy (0x7f060033) |
| `primary` / focus accent | `#FFFFC107` | measured / derived role | amber (0x7f06001d) |
| `primaryHover` | `#FFFF9800` | measured / derived role | deeper amber (from the amber family) |
| `primaryActive` | `#FFFFE082` | measured / derived role | pale amber (0x7f06001c) |
| focus accent at 60% | `#60FFC107` | measured | exact 60%-alpha entry present in the table (0x7f06001e) |
| `onPrimary` | `#FF111111` | measured / derived role | dark ink on the amber accent |
| `onSurface` | `#FFEEEEEE` | measured / derived role | near-white text colour present in the table |
| `onSurfaceVariant` | `#FF90CAF9` | measured / derived role | pale blue present in the table (0x7f06002e) |
| `live` | `#FF00BCD4` | measured / derived role | cyan present (0x7f060019 family) |
| `recording` | `#FFFF5252` | measured / derived role | red present in the table |
| `favourite` | `#FFFFC107` | measured / derived role | amber (shares the accent role) |
| `outline` | `#FF29577B` | measured / derived role | navy border tone |
| `cellFocus` | `#FFFFC107` | measured / derived role | amber: the focused cell is amber-bordered per the guide screenshot |
| `onCellFocus` | `#FF111111` | measured / derived role | dark ink on amber |

## 4b. Reference screenshot (supplied 2026-10-11) — corrections applied

A TiviMate guide screenshot (1920×1080 TV panel, read-only reference; never committed) was measured
against the seeds above. Two values were wrong and have been corrected and pinned:

| Field | Was | Now | How it was measured |
|---|---|---|---|
| `halfHourWidth` | 240.dp | **168.dp** | The visible timeline spans ~2 h (06:30→08:30) across ~70 % of a 1920 px panel ≈ 1344 px → 336 px per half-hour → ~168 dp at ×2 density. The 240 dp seed made cells ~45 % too wide. |
| `channelColumnWidth` | 296.dp | **270.dp** | The channel column (number + logo + two-line name) measures ~28 % of the panel ≈ 538 px → ~270 dp. |

Confirmed unchanged by the reference: `rowHeight` 48.dp (7 rows in ~60 % of 1080 px ≈ 92 px/row ≈
46 dp), the header's date in the channel column with the 30-minute ruler on the same line, the
`New` superscript chip, the ▶ indicator on the playing cell, and the detail panel's
"time – time ▬ duration / episode / description / ☆ / category" arrangement.

Residual uncertainty: these are derived from proportions in a photograph of a TV, so treat them as
±5 dp. `rowHeight` may be 46 rather than 48; the logo is 32.dp in both channel rows and matches.

1. **Reference screenshot** (1920×1080 if available) — pins the derived element mappings in §2
   (which dip is the row height, the column width, the 30-minute slot) and the roles in §3.
   Until it arrives, the values above are the working numbers; the Task 8 delta log measures the
   drift.
2. **`chromeAlpha`** — 0.7 matches TiviMate's stated default. A more opaque guide (1.0) is a
   one-line change if the maintainer prefers OpenTV's current reading.
3. **Contrast check** — `onCellFocus` (`#111111`) on `cellFocus` (`#ffc107`) is high-contrast;
   `onSurface` (`#eeeeee`) on `surface` (`#1f415c`) likewise. `GuidePaletteContrastTest` in
   Task 3 encodes this as an invariant.

## 4c. Native 1920x1080 capture from the Onn 4K box (2026-10-11) - exact measurements

A `screencap` of TiviMate's guide taken from the maintainer's own Onn 4K Streaming Box
(192.168.1.128:5555, 1920x1080 @ density 320, so **1dp = 2px exactly**), then measured pixel-by-pixel
with System.Drawing. These replace the photo-derived values in 4b:

| Field | 4b value | Measured | How it was measured |
|---|---|---|---|
| `rowHeight` | 48.dp | **44.dp** | Programme cells run 82px of cell background with a 6px gap between rows = 88px |
| `halfHourWidth` | 168.dp | **160.dp** | Ruler label left edges at x = 542, 862, 1182, 1502, 1822 -> exactly 320px apart |
| `channelColumnWidth` | 270.dp | **270.dp** | Channel column ends / first cell begins at x = 540 |
| `timeHeaderHeight` | 38.dp | **25.dp** | Ruler text occupies y=427..447 and the divider sits at y=462 |
| `cellGap` | 4.dp | **3.dp** | The 6px gutter between adjacent timeline cells (3dp each side) |
| `timeRulerSp` | 12.sp | **12.sp** | Inked glyph height 20px at x2 = a 12sp label |

Confirmed as-is: `logoSize` 32.dp (logo images vary in height - 28px for the NBC mark, 36px for abc7 -
so they are fit inside a container rather than sized by it), `channelNumberWidth` 40.dp, and the
focused cell being a light plain fill (#DFE1E4) with dark ink against a dark programme cell
(#242B33) on a #1C232C page - which is what the parity palette already does (light cursor, dark cells).

Colours observed in the capture, for future reference: page background `#1C232C`, programme cell
`#242B33`, the cell slightly lighter while its programme is airing `#2C323A`, focused cell `#DFE1E4`.

## 4d. Device render vs the decoded layout (2026-10-16)

The element mirror (`2026-10-16-guide-element-mirror.md`) re-measured the reference on the box at
1920x1080 @ density 320 and found one value where the APK's *declaration* and its own *render*
disagree, plus the icon slots the declaration cannot assign. The render is the reference; this table
follows it.

| Field | Decoded layout says | Render measures | Verdict |
|---|---|---|---|
| `channelNumberSp` | 24.0sp (`0x7f0e008e`) | digits 22px tall, 14px wide, beside the 14sp name's 21px caps | **16sp** - the declared 24sp draws 34px, ~1.5x the reference |
| `channelNameSp` | 14.0sp | caps 20-21px | 14sp confirmed |
| status slot 20dip `0x7f0b022a` | 20x20dip, marginEnd 6dip, dynamic tint | playing mark: ink 17x21px, `#2196F3` (the accent), 6dip before the clock | play takes this slot, leftmost |
| status slot 16dip `0x7f0b0219` | 16x16dip, `alpha=0.6`, marginEnd 8dip | catch-up clock: ink 27x24px, `#A1A2A3` (= white at 0.6) | catch-up takes this slot |
| status slot 18dip `0x7f0b0230` | 18x18dip, `gone` | never drawn in the capture | favourite takes the unused slot |
| number and name colour | style textColor `#FFFFFFFF` | both at full `#FFFFFF` on an unfocused row | both white; the number is not muted |

Also measured and deliberately left alone: the row pitch in this render is 78px (39dp) where the
2026-10-11 capture measured 88px (44dp), and the header clock there is `#90CAF9` against OpenTV's own
accent. Both are open questions in the mirror plan, not silent changes.

The build measured here is the box's installed `ar.tvplayer.tv` (5.3.3, versionCode 1000005332, md5
`fd71f925767fd27ff1f5202f6ed5e3b9`). Its own `res/layout/APKTOOL_RENAMED_0x7f0e008e.xml` still
declares the same 24sp the decoded copy does, which is why the number's smaller draw is the code's
doing and not a different resource table.

The number's *width* is the one residual: the reference's digits are 14px wide and OpenTV's 16px,
because OpenTV composes the number from `labelLarge` (Medium) where the reference's style is plain
`sans-serif`. At 2-3px of ink it is below what a viewer can see across a room, and it is noted rather
than chased.
