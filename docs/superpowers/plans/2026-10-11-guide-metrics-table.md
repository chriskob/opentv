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

## 4. Open confirmations

1. **Reference screenshot** (1920×1080 if available) — pins the derived element mappings in §2
   (which dip is the row height, the column width, the 30-minute slot) and the roles in §3.
   Until it arrives, the values above are the working numbers; the Task 8 delta log measures the
   drift.
2. **`chromeAlpha`** — 0.7 matches TiviMate's stated default. A more opaque guide (1.0) is a
   one-line change if the maintainer prefers OpenTV's current reading.
3. **Contrast check** — `onCellFocus` (`#111111`) on `cellFocus` (`#ffc107`) is high-contrast;
   `onSurface` (`#eeeeee`) on `surface` (`#1f415c`) likewise. `GuidePaletteContrastTest` in
   Task 3 encodes this as an invariant.
