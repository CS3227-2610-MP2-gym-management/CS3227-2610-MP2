# Owner Expense Management Interaction Summary

## Request and decisions

Dylan requested Owner entry of maintenance, utility, and similar operating expenditures. The design keeps membership
income and expenses in separate tabs under a renamed Finances page. Expense fields are date, positive SGD amount,
payment method, category, and optional description. Future dates, editing, deletion, receipts, budgets, and recurring
expenses are outside the feature.

The supported categories are Maintenance, Utilities, Equipment, Supplies, Rent, and Other. Owner Overview shows
all recorded income, expenses, and calculated net.

## Agent work

- Added failing tests before the schema, model, store, and service implementation.
- Added an idempotent version-4 Expense schema using integer cents and active-Owner authorization.
- Added Expense creation, category filtering, newest-first ordering, and all-recorded-time totals.
- Renamed the Owner Payments destination to Finances and retained the existing Income search and ledger.
- Added a styled, failure-retaining Add Expense dialog and a category-filtered Expenses table.
- Reused the shared two-decimal formatter and calendar-only control instead of duplicating form rules.
- Added wrapping Owner Overview cards for income, expenses, and net.

## Verification required

Dylan should verify all categories, invalid and future values, category filtering, negative net display, table
scrolling, minimum-window layout, and persistence after restarting the release JAR.
