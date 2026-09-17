# Owner CSV Export

## Goal

Implement Owner story O-P2-01 by exporting the currently displayed Member, Income Payment, and Visit records without
adding persistence queries, schema changes, or a third-party CSV dependency.

## Important prompts and decisions

- Export the active card-list contents so searches and Visit tabs determine the resulting rows.
- Keep Expenses, Memberships, Announcements, and internal numeric IDs outside this story.
- Use UTF-8 CSV with CRLF records, portable ISO dates, UTC timestamps, and fixed two-decimal SGD amounts.
- Protect spreadsheet users by quoting CSV syntax and prefixing formula-like cells beginning with `=`, `+`, `-`, or
  `@` with an apostrophe.
- Use a native save chooser, append `.csv` when necessary, and perform file writes in an existing JavaFX background
  task while preserving inline success and error feedback.

## Skills and implementation

Superpowers planning and test-driven development established the file formats and edge cases before UI wiring.
Ponytail minimalism kept the implementation to one package-private exporter operating on existing loaded projections.
The Members, Finances Income, and Visits screens snapshot their displayed items before opening the background write.

## Corrections and review

The first Member test expected a `+65` phone number unchanged. The approved formula-injection rule also applies to
leading plus signs, so the expectation was corrected to require the protective apostrophe. The Finances status message
was placed inside the Income tab so a completed export cannot display Income-specific feedback while Expenses is open.

## Test evidence

- Exporter tests cover all three schemas, optional blanks, fixed decimals, ISO formatting, completed and open Visits,
  quoting, Unicode, CR/LF content, formula hardening, extension handling, source-list immutability, and propagated I/O
  failure.
- Java and test Checkstyle plus the complete automated suite are run before handoff.
- Release JAR construction verifies that the feature does not disturb supported packaging.

## Manual review checklist

Export full and searched Member and Income lists, then export both Visit tabs. Check cancel behavior, empty states,
Unicode and quoted fields in a spreadsheet application, minimum-window layout, and both application themes before the
feature commit is approved.
