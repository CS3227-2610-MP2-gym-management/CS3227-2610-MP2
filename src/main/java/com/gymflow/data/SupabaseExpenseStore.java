package com.gymflow.data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymflow.expense.AddExpenseRequest;
import com.gymflow.model.Expense;
import com.gymflow.model.ExpenseCategory;

/** Supabase-backed Owner expense operations. */
public final class SupabaseExpenseStore {
    private static final String FIELDS = "id,expense_date,amount_cents,method,category,description,"
            + "recorded_by_account_id,created_at";
    private final SupabaseDataClient client;

    /** Creates the store. */
    public SupabaseExpenseStore(SupabaseDataClient client) {
        this.client = client;
    }

    /** Adds one expense. */
    public Expense add(AddExpenseRequest request, String description, long ownerAccountId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expense_date", request.expenseDate().toString());
        body.put("amount_cents", request.amount().movePointRight(2).longValueExact());
        body.put("method", request.method().name());
        body.put("category", request.category().name());
        body.put("description", description.isEmpty() ? null : description);
        body.put("recorded_by_account_id", ownerAccountId);
        JsonNode rows = client.post("expenses?select=" + FIELDS, body);
        return SupabaseRows.expense(single(rows));
    }

    /** Lists all expenses, optionally restricted to a category. */
    public List<Expense> list(ExpenseCategory category) {
        String filter = category == null ? "" : "&category=eq." + category.name();
        JsonNode rows = client.get("expenses?select=" + FIELDS + filter
                + "&order=expense_date.desc,id.desc");
        return StreamSupport.stream(rows.spliterator(), false)
                .map(SupabaseRows::expense).toList();
    }

    /** Totals all visible expenses. */
    public BigDecimal total() {
        return list(null).stream().map(Expense::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static JsonNode single(JsonNode rows) {
        if (!rows.isArray() || rows.size() != 1) {
            throw new IllegalStateException("Expense was not saved");
        }
        return rows.get(0);
    }
}
