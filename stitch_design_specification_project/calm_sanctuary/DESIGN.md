---
name: Calm Sanctuary
colors:
  surface: '#f7f9fb'
  surface-dim: '#d8dadc'
  surface-bright: '#f7f9fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f4f6'
  surface-container: '#eceef0'
  surface-container-high: '#e6e8ea'
  surface-container-highest: '#e0e3e5'
  on-surface: '#191c1e'
  on-surface-variant: '#3e4947'
  inverse-surface: '#2d3133'
  inverse-on-surface: '#eff1f3'
  outline: '#6e7977'
  outline-variant: '#bdc9c6'
  surface-tint: '#006a63'
  primary: '#005c55'
  on-primary: '#ffffff'
  primary-container: '#0f766e'
  on-primary-container: '#a3faef'
  inverse-primary: '#80d5cb'
  secondary: '#216963'
  on-secondary: '#ffffff'
  secondary-container: '#a8ece5'
  on-secondary-container: '#266d68'
  tertiary: '#445266'
  on-tertiary: '#ffffff'
  tertiary-container: '#5c6a7f'
  on-tertiary-container: '#dfeaff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#9cf2e8'
  primary-fixed-dim: '#80d5cb'
  on-primary-fixed: '#00201d'
  on-primary-fixed-variant: '#00504a'
  secondary-fixed: '#abefe8'
  secondary-fixed-dim: '#8fd3cc'
  on-secondary-fixed: '#00201e'
  on-secondary-fixed-variant: '#00504b'
  tertiary-fixed: '#d5e3fc'
  tertiary-fixed-dim: '#b9c7df'
  on-tertiary-fixed: '#0d1c2e'
  on-tertiary-fixed-variant: '#3a485b'
  background: '#f7f9fb'
  on-background: '#191c1e'
  surface-variant: '#e0e3e5'
  text-primary: '#0F172A'
  text-secondary: '#475569'
  text-tertiary: '#64748B'
  surface-card: '#FFFFFF'
  surface-subtle: '#F1F5F9'
  border-subtle: '#E2E8F0'
  border-strong: '#CBD5E1'
  status-warning: '#D97706'
  status-warning-surface: '#FEF3C7'
  status-critical: '#B91C1C'
  status-critical-surface: '#FEE2E2'
  status-success: '#15803D'
  status-success-surface: '#DCFCE7'
  privacy-badge-bg: '#E0F2FE'
  privacy-badge-text: '#0369A1'
typography:
  headline-lg:
    fontFamily: Manrope
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 38px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Manrope
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Manrope
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  headline-sm:
    fontFamily: Manrope
    fontSize: 17px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
  code-sm:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-mobile: 0.75rem
  margin: 1.5rem
  margin-mobile: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
  space-2xl: 2rem
---

## Brand & Style

This design system delivers a secure, respectful, and dignified on-device workspace for users managing sensitive evidence. The emotional response must be reassuring, grounded, and clinical without feeling sterile or intimidating. Users interact with this interface during moments of stress or trauma; visual drama, accusatory styling, and high-anxiety red alerts are deliberately avoided.

The visual style combines **Corporate / Modern** discipline with **tactile Material 3 utility**:
- **Controlled Calm:** Restful slate and deep teal tones instill composure, privacy, and institutional trust.
- **Explicit Attribution:** Strict visual differentiation between what was observed, what AI suggested, and what the user explicitly verified.
- **Protected Content:** Sensitive imagery and excerpts remain shielded behind tap-to-reveal affordances.
- **Restraint Over Alarmism:** Severity states use measured warnings and desaturated indicators rather than aggressive hazard patterns.

## Colors

The palette establishes an unshakeable sense of security. `#0F766E` serves as the primary touchpoint, driving interactive states, key toggles, and primary navigation tokens. The deeper `#115E59` stabilizes high-emphasis headers and pressed states.

### Role Assignments
- **Primary (`#0F766E`):** Floating action buttons, primary action pills, active navigation targets, and verified status badges.
- **Secondary (`#115E59`):** High-density app bars, key metadata groupings, and focused interactive borders.
- **Tertiary (`#475569`):** Subordinate metadata, timestamps, hash fingerprints, and inactive navigational items.
- **Neutral Canvas (`#F8FAFC`):** Base screen substrate, providing gentle contrast without the harsh glare of stark white.

### Severity & State Rules
- **Critical (`#B91C1C`):** Reserved strictly for irreversible destructive actions (e.g., purge vault) and corroborated escalation warnings. Never used for unreviewed AI inference.
- **Warning (`#D97706`):** Indicates pending user reviews, missing metadata, or unconfirmed pattern clusters.
- **Success (`#15803D`):** Encrypted integrity seals, verified hash matches, and exported report confirmations.
- **Privacy Indicator (`#0369A1` on `#E0F2FE`):** Unambiguous system-level tag designating local on-device processing and offline vault operations.

## Typography

The typographic hierarchy prioritizes rapid scanning, effortless legibility under duress, and absolute factual clarity. 

- **Display & Section Headers:** **Manrope** provides a geometric, balanced, and reassuring structure without appearing playful or decorative. It anchors screens with institutional weight.
- **Body & Controls:** **Inter** is applied universally across conversational transcripts, incident descriptions, data fields, and controls for optimal optical rendering at small scales.
- **Integrity Manifests & Hashes:** Monospaced data points (SHA-256 tokens, encryption keys, and raw device logs) utilize a specialized code level to distinguish system integrity proofs from human narratives.

## Layout & Spacing

The layout adheres to an Android-first 4-column fluid mobile grid expanding to an 8-column layout on foldables and tablets.

### Rhythm & Alignment
- **Base Grid:** 8px base spatial grid with 4px sub-increments for compact badges and list metadata.
- **Screen Margins:** Fixed `16px` (`margin-mobile`) horizontal canvas margin on standard mobile screens, expanding to `24px` (`margin`) on large screens or landscape views.
- **Component Padding:** Standard cards enforce `16px` (`space-lg`) internal padding to keep dense logs breathable.
- **Touch Targets:** All tap targets conform to Android accessibility standards with a strict minimum dimension of 48×48dp, even when visual badge elements render smaller.

## Elevation & Depth

To cultivate trust and clarity, this design system avoids heavy shadows, floating atmospheric blurs, or theatrical glows. Depth is communicated almost exclusively via **tonal layering** and **refined hairline boundaries**.

- **Canvas (Level 0):** Background uses `#F8FAFC`.
- **Card & Sheet Surface (Level 1):** Solid `#FFFFFF` enclosed by a crisp, low-contrast border (`1px solid #E2E8F0`). Flat elevation with an ultra-subtle ambient shadow: `0px 1px 3px rgba(15, 23, 42, 0.04)`.
- **Active / Dragged Element (Level 2):** Subtle elevation for reordering evidence items in report builders: `0px 4px 12px rgba(15, 23, 42, 0.08)` with `#CBD5E1` border emphasis.
- **Modals & Bottom Sheets (Level 3):** Grounded `#FFFFFF` surface accompanied by a soft non-alarming backdrop scrim: `rgba(15, 23, 42, 0.4)`. No colored glow effects.

## Shapes

The design system uses structured, predictable curvature that strikes a balance between approachable ergonomics and professional document integrity.

- **Base Radius (0.5rem / 8px):** Standard inputs, metadata tags, and inline badges.
- **Large Radius (1rem / 16px):** Primary evidence cards, timeline group containers, and notification previews.
- **Extra Large Radius (1.5rem / 24px):** Bottom sheet dialogs and persistent action drawers.
- **Full Pill Radius (9999px):** Floating Action Buttons (FAB), quick filters, and status chips.

## Components

### Buttons
- **Primary:** Filled `#0F766E` with `#FFFFFF` text. Height: 48dp. Roundedness: 12dp. Pressed state shifts to `#115E59`.
- **Secondary / Outlined:** 1px border `#CBD5E1` with `#0F766E` text and `#FFFFFF` background.
- **Subtle / Text:** Flat presentation with `#475569` text, triggering an 8% `#0F766E` tint container on tap.

### Privacy & State Badges
- **Local-Only Vault Badge:** Pill container in `#E0F2FE` with `#0369A1` label and lock icon.
- **Tri-State Attribution Badges:**
  - *Observed:* Bordered `#CBD5E1`, neutral gray background `#F1F5F9`, `#0F172A` text.
  - *AI Inference:* Distinct dotted outline `#0F766E`, `#F0FDFA` background, `#0F766E` text with subtle spark indicator.
  - *User Confirmed:* Solid `#0F766E` surface with `#FFFFFF` text and checkmark indicator.

### Evidence & Incident Cards
- Structured container with `1px solid #E2E8F0` and `16px` border-radius.
- Contains three distinct visual zones:
  1. Header: Timestamp, data source indicator (e.g., Screenshot, Notification), and integrity checkmark.
  2. Body: Blurred/shielded sensitive media thumbnail or redacted textual excerpt with a clear "Tap to view" pill.
  3. Footer: Hash status indicator (`SHA-256 Verified`) and user confirmation action buttons.

### Form Inputs & Text Fields
- Clean outline variant: `1px solid #CBD5E1` border resting, transition to `2px solid #0F766E` on focus.
- Placeholder text in `#64748B`. Integrated trailing icons for clear actions and privacy visibility toggles.

### Selection Controls
- **Checkboxes & Radios:** Teal `#0F766E` fill on active selection with high-contrast white tick. 24dp size set inside 48dp accessible touch target.
- **Switches:** Android Material 3 track switch; inactive track in `#E2E8F0`, active track in `#0F766E`.

### Incident Timeline Node
- Left-aligned continuous vertical line (`2px solid #E2E8F0`).
- Timeline anchor dot: 12dp circle with `#0F766E` fill for user-confirmed items, `#D97706` for unreviewed escalation signals, and `#CBD5E1` for passive metadata records.