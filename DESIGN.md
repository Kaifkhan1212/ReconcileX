---
name: ReconcileX
description: Automated Financial Transaction Reconciliation Engine & Studio
colors:
  primary: "oklch(0.208 0.042 265.755)"
  primary-foreground: "oklch(0.984 0.003 247.858)"
  secondary: "oklch(0.968 0.007 247.896)"
  secondary-foreground: "oklch(0.208 0.042 265.755)"
  neutral-bg: "oklch(1 0 0)"
  neutral-fg: "oklch(0.129 0.042 264.695)"
  card: "oklch(1 0 0)"
  card-fg: "oklch(0.129 0.042 264.695)"
  muted: "oklch(0.968 0.007 247.896)"
  muted-fg: "oklch(0.554 0.046 257.417)"
  accent: "oklch(0.968 0.007 247.896)"
  border: "oklch(0.929 0.013 255.508)"
  match-exact: "oklch(0.627 0.194 149.214)"
  match-tolerance: "oklch(0.769 0.188 70.08)"
  match-unmatched: "oklch(0.577 0.245 27.325)"
  destructive: "oklch(0.577 0.245 27.325)"
typography:
  display:
    fontFamily: "ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.875rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.025em"
  headline:
    fontFamily: "ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "-0.02em"
  title:
    fontFamily: "ui-sans-serif, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: "-0.01em"
  body:
    fontFamily: "ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "0em"
  label:
    fontFamily: "ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.35
    letterSpacing: "0.02em"
  mono:
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: "0em"
rounded:
  sm: "6px"
  md: "8px"
  lg: "10px"
  xl: "14px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.primary-foreground}"
    rounded: "{rounded.md}"
    padding: "8px 16px"
    height: "36px"
  button-secondary:
    backgroundColor: "{colors.secondary}"
    textColor: "{colors.secondary-foreground}"
    rounded: "{rounded.md}"
    padding: "8px 16px"
    height: "36px"
  button-sm:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.primary-foreground}"
    rounded: "{rounded.md}"
    padding: "6px 12px"
    height: "32px"
  badge-exact:
    backgroundColor: "{colors.match-exact}"
    textColor: "{colors.neutral-bg}"
    rounded: "{rounded.md}"
    padding: "2px 10px"
  badge-mismatch:
    backgroundColor: "{colors.match-tolerance}"
    textColor: "{colors.neutral-bg}"
    rounded: "{rounded.md}"
    padding: "2px 10px"
  badge-missing:
    backgroundColor: "{colors.match-unmatched}"
    textColor: "{colors.neutral-bg}"
    rounded: "{rounded.md}"
    padding: "2px 10px"
  card:
    backgroundColor: "{colors.card}"
    textColor: "{colors.card-fg}"
    rounded: "{rounded.lg}"
    padding: "24px"
---

# ReconcileX Design System

<!-- impeccable:design-schema 2 -->

## Overview

ReconcileX operates as a **Mission-Critical Fintech Workspace**. Its visual character prioritizes extreme data clarity, rapid status comprehension, and unadorned precision for treasury analysts and compliance auditors handling high-volume transaction batches.

The visual language balances dense tabular ledgers with crisp, high-contrast semantic badges. Every screen is designed around verifiable certainty: exact numbers, deterministic rule match classifications, and immutable audit logs.

## Colors

The palette employs a restrained slate foundation accented with strictly semantic financial status tokens.

### Primary
- **Deep Navy Slate** (`oklch(0.208 0.042 265.755)` / Dark: `oklch(0.929 0.013 255.508)`): Used for primary action buttons, brand marks, and dominant navigation accents. Never used decoratively.

### Semantic Status
- **Match Exact (Emerald)** (`oklch(0.627 0.194 149.214)` / `bg-emerald-500/10` `border-emerald-500/30`): Applied solely to transactions and runs with exact 1-to-1 reconciliation (`EXACT_MATCH`).
- **Match Tolerance (Amber)** (`oklch(0.769 0.188 70.08)` / `bg-amber-500/10` `border-amber-500/30`): Highlights date settlement differences (1–3 days) or gateway commission variances (`DATE_MISMATCH`, `AMOUNT_MISMATCH`).
- **Discrepancy Alarm (Rose)** (`oklch(0.577 0.245 27.325)` / `bg-rose-500/10` `border-rose-500/30`): Flags unmatched records, unbilled debits, or ledger duplicates (`MISSING_IN_BANK`, `MISSING_IN_INTERNAL`, `UNEXPECTED_BANK_DEBIT`).

### Neutral
- **Neutral Canvas** (`oklch(1 0 0)` / Dark: `oklch(0.129 0.042 264.695)`): Workspace background.
- **Card Surface** (`oklch(1 0 0)` / Dark: `oklch(0.208 0.042 265.755)`): Elevated data surface.
- **Muted Foreground** (`oklch(0.554 0.046 257.417)` / Dark: `oklch(0.704 0.04 256.788)`): Labels, column headers, metadata, and helper text.
- **Divider Border** (`oklch(0.929 0.013 255.508)` / Dark: `oklch(1 0 0 / 10%)`): 1px subtle separation for table cells and card containers.

### Named Rules
**The Semantic Sanctity Rule:** Colors outside slate/neutral must carry strict financial meaning. Green, amber, and rose are reserved exclusively for match status and error states; they may never be used for generic illustration or brand flair.

## Typography

**Primary System Font:** `ui-sans-serif, system-ui, sans-serif`
**Financial & Monospace Font:** `ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace`

**Character:** Clean, structural, high-legibility sans-serif paired with dense monospace figures for monetary amounts, transaction references, and SHA-256 hashes.

### Hierarchy
- **Display** (Bold 700, 1.875rem / 30px, line-height 1.2, tracking -0.025em): View titles and primary metric aggregates.
- **Headline** (Bold 700, 1.5rem / 24px, line-height 1.25, tracking -0.02em): Section headers (`Run Results & Financial Summary`).
- **Title** (Semibold 600, 1rem / 16px, line-height 1.4): Card titles, modal headers, filter group titles.
- **Body** (Regular 400, 0.875rem / 14px, line-height 1.5): Descriptive explanations, audit details, table cell contents.
- **Label** (Medium 500, 0.75rem / 12px, tracking 0.02em, uppercase where noted): Column headers, input labels, badge text.
- **Mono** (Medium 500, 0.75rem / 12px, tabular numbers): Currency amounts (`$1,420.50`), timestamps, transaction IDs (`TXN-8842`).

### Named Rules
**The Tabular Number Rule:** All financial amounts, percentages, and balances must render with tabular numeric figures (`font-mono` or `font-feature-settings: 'tnum'`) aligned right in table columns.

## Layout

- **Container Model:** Fixed fluid viewport with max-w-7xl auto-centered canvas and `px-4 sm:px-6` horizontal breathing room.
- **Navigation:** Sticky top-0 navigation bar (`h-16`) with subtle backdrop blur (`bg-background/95 backdrop-blur`) and border-b.
- **Data Grids:** Multi-column summary KPI cards on top (`grid grid-cols-1 md:grid-cols-4 gap-4`), followed by interactive filter ribbons and full-width dense tables.
- **Rhythm:** Multiples of 4px (`gap-1.5`, `gap-2`, `gap-4`, `p-6`).

## Elevation & Depth

ReconcileX uses **Tonal Layering with Flat Boundaries** rather than heavy drop shadows:
- Default surfaces are completely flat with crisp 1px borders (`border border-border`).
- Interactive elements (cards, buttons) carry subtle ambient lift (`shadow-sm` = `0 1px 2px 0 rgb(0 0 0 / 0.05)`).
- Dialogs and popovers use `shadow-lg` (`0 10px 15px -3px rgb(0 0 0 / 0.1)`) over a semi-transparent backdrop overlay.

### Named Rules
**The Flat Precision Rule:** Elevate surfaces through border contrast and background tone shifts (`bg-muted/40`, `bg-card`) rather than dramatic drop shadows.

## Shapes

- **Base Radius:** `0.625rem` (10px).
- **Controls & Buttons:** `rounded-md` (8px).
- **Badges & Tags:** `rounded-md` (8px) with internal padding `px-2.5 py-0.5`.
- **Card Containers:** `rounded-lg` (10px) with hairline border (`border border-border`).

## Components

### Buttons
- **Shape:** `rounded-md` (8px), height `36px` (`h-9 px-4 py-2`), compact `32px` (`h-8 px-3 text-xs`).
- **Primary:** `bg-primary text-primary-foreground shadow hover:bg-primary/90`.
- **Secondary:** `bg-secondary text-secondary-foreground shadow-sm hover:bg-secondary/80`.
- **Destructive:** `bg-destructive text-destructive-foreground shadow-sm hover:bg-destructive/90`.
- **Outline:** `border border-input bg-background hover:bg-accent hover:text-accent-foreground`.

### Badges / Status Pills
- **Exact Match:** `bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30`.
- **Tolerance / Mismatch:** `bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-500/30`.
- **Missing / Duplicate:** `bg-rose-500/10 text-rose-700 dark:text-rose-400 border border-rose-500/30`.
- **Neutral / Informational:** `bg-secondary text-secondary-foreground border border-border`.

### Metric KPI Cards
- **Structure:** `Card` with `CardHeader` (title uppercase tracking-wider text-xs muted) and `CardContent` (bold tabular figure text-2xl).
- **Surface:** `bg-card text-card-foreground border-border shadow-sm`.

### Data Table
- **Header:** Sticky or top row with `bg-muted/40 font-semibold text-xs text-muted-foreground uppercase`.
- **Rows:** `border-b border-border hover:bg-muted/30 transition-colors`.
- **Alignment:** Text left, dates and status center, currency numbers strictly right-aligned with monospace formatting.

## Do's and Don'ts

### Do:
- **Do** align monetary figures to the right with monospace numbers (`font-mono`).
- **Do** pair every semantic color (green, amber, red) with an accompanying descriptive label or icon for accessibility.
- **Do** maintain high contrast (>4.5:1) between text and background in both light and dark modes.
- **Do** use `border-border` hairline borders to define container boundaries cleanly.

### Don't:
- **Don't** use arbitrary colored gradients, glowing cards, or saturated brand backdrops on financial tables.
- **Don't** center monetary amounts or date columns in data tables.
- **Don't** use decorative shadows that exceed `shadow-sm` on in-flow page cards.
- **Don't** hide critical reconciliation discrepancy reasons inside tooltips without immediate surface visibility.

