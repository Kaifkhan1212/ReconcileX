import { useState, useEffect, Fragment } from "react";
import { useAuth } from "@/lib/auth-context";
import {
  getRunSummaryApi,
  getRunResultsApi,
  downloadReportCsvApi,
  listReconciliationRunsApi,
  type ResultsFilterParams,
} from "@/lib/api";
import { MATCHING_RULES } from "@/lib/matching-rules";
import type { RunSummaryResponse, ReconciliationResultDto } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  BarChart3,
  Download,
  Filter,
  Search,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  RotateCcw,
  LogIn,
  Info,
  ChevronDown,
  ChevronUp,
} from "lucide-react";

interface ResultsViewProps {
  initialRunId?: number | undefined;
}

export function ResultsView({ initialRunId }: ResultsViewProps) {
  const { token, isAuthenticated, openLoginModal } = useAuth();

  const [activeRunId, setActiveRunId] = useState<number | null>(initialRunId || null);
  const [runIdInput, setRunIdInput] = useState<string>(
    initialRunId ? initialRunId.toString() : "",
  );

  const [summary, setSummary] = useState<RunSummaryResponse | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  const [results, setResults] = useState<ReconciliationResultDto[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [resultsLoading, setResultsLoading] = useState(false);

  // Filters
  const [resultTypeFilter, setResultTypeFilter] = useState<string>("");
  const [fromDateFilter, setFromDateFilter] = useState<string>("");
  const [toDateFilter, setToDateFilter] = useState<string>("");

  const [csvDownloading, setCsvDownloading] = useState(false);
  const [expandedRowId, setExpandedRowId] = useState<number | null>(null);
  const [showFormulaTooltip, setShowFormulaTooltip] = useState(false);

  // If no initialRunId was provided, resolve the latest run automatically
  useEffect(() => {
    if (!isAuthenticated || !token) return;

    if (!activeRunId) {
      listReconciliationRunsApi(0, 1, token)
        .then((res) => {
          if (res.content && res.content.length > 0) {
            const latestId = res.content[0]!.id;
            setActiveRunId(latestId);
            setRunIdInput(latestId.toString());
          }
        })
        .catch(() => {
          // If runs cannot be fetched
        });
    }
  }, [isAuthenticated, token, activeRunId]);

  const loadSummary = async (id: number) => {
    setSummaryLoading(true);
    setSummaryError(null);
    try {
      const res = await getRunSummaryApi(id, token);
      setSummary(res);
    } catch (err: unknown) {
      setSummaryError(err instanceof Error ? err.message : "Failed to load run summary");
      setSummary(null);
    } finally {
      setSummaryLoading(false);
    }
  };

  const fetchResults = async (
    id: number,
    pageNum = 0,
    typeParam?: string,
    fromParam?: string,
    toParam?: string,
  ) => {
    setResultsLoading(true);
    try {
      const currentType = typeParam !== undefined ? typeParam : resultTypeFilter;
      const currentFrom = fromParam !== undefined ? fromParam : fromDateFilter;
      const currentTo = toParam !== undefined ? toParam : toDateFilter;

      const filters: ResultsFilterParams = {
        page: pageNum,
        size: 50,
      };
      if (currentType && currentType.trim()) filters.resultType = currentType.trim();
      if (currentFrom && currentFrom.trim()) filters.fromDate = currentFrom.trim();
      if (currentTo && currentTo.trim()) filters.toDate = currentTo.trim();

      const res = await getRunResultsApi(id, filters, token);
      setResults(res.content || []);
      setPage(res.number || 0);
      setTotalPages(res.totalPages || 1);
      setTotalElements(res.totalElements || 0);
    } catch (err) {
      console.error("Failed to load results:", err);
      setResults([]);
    } finally {
      setResultsLoading(false);
    }
  };

  useEffect(() => {
    if (activeRunId && token) {
      loadSummary(activeRunId);
      fetchResults(activeRunId, 0, resultTypeFilter, fromDateFilter, toDateFilter);
    }
  }, [activeRunId, token]);

  const handleRunSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const id = parseInt(runIdInput, 10);
    if (!isNaN(id) && id > 0) {
      setActiveRunId(id);
    }
  };

  const handleTypeSelect = (newType: string) => {
    setResultTypeFilter(newType);
    if (activeRunId) {
      fetchResults(activeRunId, 0, newType, fromDateFilter, toDateFilter);
    }
  };

  const handleFromDateChange = (newDate: string) => {
    setFromDateFilter(newDate);
    if (activeRunId) {
      fetchResults(activeRunId, 0, resultTypeFilter, newDate, toDateFilter);
    }
  };

  const handleToDateChange = (newDate: string) => {
    setToDateFilter(newDate);
    if (activeRunId) {
      fetchResults(activeRunId, 0, resultTypeFilter, fromDateFilter, newDate);
    }
  };

  const handleApplyFilters = (e: React.FormEvent) => {
    e.preventDefault();
    if (activeRunId) {
      fetchResults(activeRunId, 0, resultTypeFilter, fromDateFilter, toDateFilter);
    }
  };

  const handleResetFilters = () => {
    setResultTypeFilter("");
    setFromDateFilter("");
    setToDateFilter("");
    if (activeRunId) {
      fetchResults(activeRunId, 0, "", "", "");
    }
  };

  const handleDownloadCsv = async () => {
    if (!activeRunId) return;
    setCsvDownloading(true);
    try {
      const blob = await downloadReportCsvApi(activeRunId, token);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `reconciliation-run-${activeRunId}-report.csv`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Download failed");
    } finally {
      setCsvDownloading(false);
    }
  };

  const formatCurrency = (val?: number | null) => {
    const numericValue = val !== undefined && val !== null ? val : 0;
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      maximumFractionDigits: 2,
    }).format(numericValue);
  };

  const getResultBadgeStyle = (type: string) => {
    if (type === "EXACT_MATCH") {
      return "bg-emerald-500/10 text-emerald-400 border-emerald-500/25";
    }
    if (type.includes("MISMATCH") || type.includes("TOLERANCE") || type.includes("BATCH")) {
      return "bg-amber-500/10 text-amber-400 border-amber-500/25";
    }
    if (type.includes("MISSING") || type.includes("DUPLICATE") || type.includes("UNEXPECTED")) {
      return "bg-rose-500/10 text-rose-400 border-rose-500/25";
    }
    return "bg-slate-800 text-slate-300 border-slate-700";
  };

  // Calculate matching percentage strictly by INR monetary value
  const totalValueCalculated = summary
    ? (summary.matchedValue || 0) +
      (summary.unmatchedInternalValue || 0) +
      (summary.unmatchedBankCreditValue || 0) +
      (summary.unmatchedBankDebitValue || 0)
    : 0;

  const matchRatio =
    totalValueCalculated > 0 && summary?.matchedValue
      ? Math.round((summary.matchedValue / totalValueCalculated) * 100)
      : 0;

  // Guest State: Clean sign in prompt, no 401 error banner, no fake counts
  if (!isAuthenticated) {
    return (
      <div className="space-y-6 max-w-3xl mx-auto">
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-8 shadow-xs text-center space-y-4">
          <div className="h-10 w-10 rounded-xl bg-slate-800 border border-slate-700 mx-auto flex items-center justify-center text-slate-300">
            <BarChart3 className="h-5 w-5" />
          </div>
          <div className="space-y-1.5">
            <h2 className="text-base font-semibold text-white">
              Sign In to View Reconciliation Results
            </h2>
            <p className="text-xs text-slate-400 max-w-md mx-auto leading-relaxed">
              Granular transaction outcome classifications, variance breakdowns, and report exports
              require an active operator session. Sign in to inspect financial run summaries.
            </p>
          </div>
          <Button
            onClick={openLoginModal}
            className="h-9 px-6 text-xs font-semibold gap-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-md shadow-sm"
          >
            <LogIn className="h-3.5 w-3.5" />
            <span>Sign In to ReconcileX</span>
          </Button>
        </div>
      </div>
    );
  }

  // No runs found in the system
  if (!activeRunId) {
    return (
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-8 text-center space-y-3">
        <p className="text-xs text-slate-400 max-w-sm mx-auto">
          No reconciliation runs found in the system. Ingest your bank statement and internal ledger to execute a run.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Signature Header Command Toolbar */}
      <div className="p-5 rounded-xl border border-slate-800 bg-slate-900/50 shadow-xs flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5 flex-wrap">
            <h1 className="text-lg font-semibold tracking-tight text-white flex items-center gap-2">
              <span>Reconciliation Results &amp; Reports</span>
            </h1>
            <span className="inline-flex items-center rounded-md border border-slate-700 bg-slate-800 px-2 py-0.5 text-xs font-mono font-medium text-slate-200">
              Run #{activeRunId}
            </span>
            {summary && (
              <span className="inline-flex items-center gap-1 rounded-md border border-emerald-500/20 bg-emerald-500/10 px-2 py-0.5 text-xs font-mono font-medium text-emerald-400">
                <CheckCircle2 className="h-3 w-3" />
                {summary.status}
              </span>
            )}
          </div>
          <p className="text-xs text-slate-400 max-w-2xl leading-relaxed">
            Deterministic multi-pass outcome classifications, financial variance analysis, and streaming CSV export in INR.
          </p>
        </div>

        {/* Run Selector & CSV Download Button */}
        <div className="flex items-center gap-2.5 shrink-0 flex-wrap w-full lg:w-auto justify-between lg:justify-end">
          <form onSubmit={handleRunSearch} className="flex items-center gap-1.5">
            <Input
              id="run-id-input"
              type="number"
              min="1"
              value={runIdInput}
              onChange={(e) => setRunIdInput(e.target.value)}
              className="w-20 h-8 font-mono text-xs bg-slate-950/60 border-slate-800 text-white"
              placeholder="Run ID"
              aria-label="Run ID"
            />
            <Button
              type="submit"
              size="sm"
              variant="secondary"
              className="h-8 text-xs font-medium gap-1 bg-slate-800 hover:bg-slate-700 text-white border border-slate-700"
            >
              <Search className="h-3.5 w-3.5" />
              <span>Load</span>
            </Button>
          </form>

          <Button
            type="button"
            size="sm"
            onClick={handleDownloadCsv}
            disabled={csvDownloading || !summary}
            className="h-8 text-xs font-semibold gap-1.5 bg-indigo-600 hover:bg-indigo-500 text-white shadow-xs"
          >
            <Download className={`h-3.5 w-3.5 ${csvDownloading ? "animate-bounce" : ""}`} />
            <span>{csvDownloading ? "Exporting..." : "Export CSV Report"}</span>
          </Button>
        </div>
      </div>

      {/* Financial Overview KPI Rail */}
      {summaryLoading ? (
        <div className="p-8 text-center text-xs text-slate-400 font-mono rounded-xl border border-slate-800 bg-slate-900/40">
          Loading financial ledger for Run #{activeRunId}...
        </div>
      ) : summaryError ? (
        <div className="p-4 rounded-xl border border-rose-500/25 bg-rose-500/10 text-rose-400 text-xs font-mono">
          {summaryError}
        </div>
      ) : summary ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3.5">
          {/* 1. Matched Value (Emerald) */}
          <div className="rounded-xl border border-emerald-500/25 bg-emerald-500/5 p-4 shadow-xs relative">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold text-emerald-400 uppercase tracking-wide">
                Matched Value
              </span>
              <button
                type="button"
                onClick={() => setShowFormulaTooltip(!showFormulaTooltip)}
                className="text-slate-400 hover:text-emerald-300 transition-colors p-0.5"
                title="View paired percentage formula"
              >
                <Info className="h-3 w-3" />
              </button>
            </div>
            <div className="text-xl font-bold tracking-tight text-emerald-400 font-mono tabular-nums text-right mt-1.5">
              {formatCurrency(summary.matchedValue)}
            </div>
            <div className="flex items-center justify-between text-[11px] text-slate-400 mt-2 font-mono">
              <span>Deterministic</span>
              <span className="text-emerald-400 font-medium">{matchRatio}% Paired</span>
            </div>

            {showFormulaTooltip && (
              <div className="absolute top-full left-0 right-0 z-30 mt-1 p-2.5 rounded-lg bg-slate-950 border border-slate-800 text-[10px] text-slate-300 shadow-xl leading-relaxed font-sans">
                <strong>% Paired Formula (by INR monetary value):</strong>
                <p className="mt-1 font-mono text-[9px] text-emerald-400">
                  matchedValue ÷ (matchedValue + unmatchedInternal + unmatchedBankCr + unmatchedBankDr) × 100
                </p>
                <p className="mt-1 text-slate-400">Calculated by total INR amount, not transaction count.</p>
              </div>
            )}
          </div>

          {/* 2. Explained Value */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 shadow-xs">
            <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide">
              Explained Value
            </div>
            <div className="text-xl font-bold tracking-tight text-white font-mono tabular-nums text-right mt-1.5">
              {formatCurrency(summary.explainedValue)}
            </div>
            <p className="text-[10px] text-slate-400 mt-2 truncate" title="Bank service charges and ignored failed payments">
              Bank charges &amp; excluded failed txns
            </p>
          </div>

          {/* 3. Unmatched Internal */}
          <div className="rounded-xl border border-rose-500/25 bg-rose-500/5 p-4 shadow-xs">
            <div className="text-[11px] font-semibold text-rose-400 uppercase tracking-wide">
              Unmatched Internal
            </div>
            <div className="text-xl font-bold tracking-tight text-rose-400 font-mono tabular-nums text-right mt-1.5">
              {formatCurrency(summary.unmatchedInternalValue)}
            </div>
            <p className="text-[10px] text-slate-400 mt-2">Internal items missing in bank</p>
          </div>

          {/* 4. Unmatched Bank Credits */}
          <div className="rounded-xl border border-amber-500/25 bg-amber-500/5 p-4 shadow-xs">
            <div className="text-[11px] font-semibold text-amber-400 uppercase tracking-wide">
              Unmatched Bank (Cr)
            </div>
            <div className="text-xl font-bold tracking-tight text-amber-400 font-mono tabular-nums text-right mt-1.5">
              {formatCurrency(summary.unmatchedBankCreditValue)}
            </div>
            <p className="text-[10px] text-slate-400 mt-2">Bank credits missing in internal</p>
          </div>

          {/* 5. Unmatched Bank Debits */}
          <div className="rounded-xl border border-rose-500/25 bg-rose-500/5 p-4 shadow-xs">
            <div className="text-[11px] font-semibold text-rose-400 uppercase tracking-wide">
              Unmatched Bank (Dr)
            </div>
            <div className="text-xl font-bold tracking-tight text-rose-400 font-mono tabular-nums text-right mt-1.5">
              {formatCurrency(summary.unmatchedBankDebitValue)}
            </div>
            <p className="text-[10px] text-slate-400 mt-2">Direct debits missing in internal</p>
          </div>
        </div>
      ) : null}

      {/* 2-Column Signature Workspace: Left 8-Cols Table, Right 4-Cols Sticky Analytics */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Column: Transaction Outcomes Table */}
        <div className="lg:col-span-8 rounded-xl border border-slate-800 bg-slate-900/40 shadow-xs overflow-hidden">
          {/* Table Header Row */}
          <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/40">
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h2 className="text-sm font-bold text-white flex items-center gap-2">
                  <span>Transaction Outcomes</span>
                  <span className="text-xs font-mono font-medium text-slate-400">
                    ({totalElements})
                  </span>
                </h2>
                {resultTypeFilter && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 font-mono text-[10px]">
                    Filtered: {resultTypeFilter}
                  </span>
                )}
              </div>
              <p className="text-[11px] text-slate-400 mt-0.5">
                Exact pairings, settlement variances, and exception classifications
              </p>
            </div>

            {(resultTypeFilter || fromDateFilter || toDateFilter) && (
              <Button
                type="button"
                size="sm"
                variant="ghost"
                onClick={handleResetFilters}
                className="h-7 px-2 text-xs text-slate-400 hover:text-white gap-1"
              >
                <RotateCcw className="h-3 w-3" />
                <span>Reset Filters</span>
              </Button>
            )}
          </div>

          {/* Filter Ribbon Row */}
          <div className="px-4 py-2.5 border-b border-slate-800 bg-slate-950/60">
            <form onSubmit={handleApplyFilters} className="flex flex-wrap items-center gap-3">
              {/* Outcome Type Selector */}
              <div className="flex items-center gap-1.5 w-full sm:w-auto sm:min-w-[210px]">
                <label htmlFor="result-type-select" className="text-[11px] text-slate-400 font-medium shrink-0">
                  Outcome:
                </label>
                <select
                  id="result-type-select"
                  value={resultTypeFilter}
                  onChange={(e) => handleTypeSelect(e.target.value)}
                  className="h-8 flex-1 rounded-md bg-slate-900 border border-slate-800 text-white text-xs px-2.5 py-1 focus:outline-none focus:ring-1 focus:ring-indigo-500 font-mono"
                >
                  <option value="">All Outcomes</option>
                  {MATCHING_RULES.map((r) => (
                    <option key={r.resultType} value={r.resultType}>
                      {r.resultType} — {r.label}
                    </option>
                  ))}
                  <option value="REFUND_PENDING_BANK">REFUND_PENDING_BANK — Pending</option>
                </select>
              </div>

              {/* From Date */}
              <div className="flex items-center gap-1.5 w-full sm:w-auto">
                <label htmlFor="from-date-input" className="text-[11px] text-slate-400 font-medium shrink-0">
                  From:
                </label>
                <input
                  id="from-date-input"
                  type="date"
                  value={fromDateFilter}
                  onChange={(e) => handleFromDateChange(e.target.value)}
                  className="h-8 flex-1 sm:w-36 rounded-md bg-slate-900 border border-slate-800 text-white text-xs px-2.5 py-1 focus:outline-none focus:ring-1 focus:ring-indigo-500 font-mono [color-scheme:dark]"
                />
              </div>

              {/* To Date */}
              <div className="flex items-center gap-1.5 w-full sm:w-auto">
                <label htmlFor="to-date-input" className="text-[11px] text-slate-400 font-medium shrink-0">
                  To:
                </label>
                <input
                  id="to-date-input"
                  type="date"
                  value={toDateFilter}
                  onChange={(e) => handleToDateChange(e.target.value)}
                  className="h-8 flex-1 sm:w-36 rounded-md bg-slate-900 border border-slate-800 text-white text-xs px-2.5 py-1 focus:outline-none focus:ring-1 focus:ring-indigo-500 font-mono [color-scheme:dark]"
                />
              </div>

              <Button
                type="submit"
                size="sm"
                className="h-8 text-xs font-medium gap-1.5 bg-slate-800 border border-slate-700 text-white hover:bg-slate-700 shadow-xs px-3.5 w-full sm:w-auto shrink-0"
              >
                <Filter className="h-3 w-3 text-indigo-400" />
                <span>Apply</span>
              </Button>
            </form>
          </div>

          {/* Table Viewport */}
          <div>
            {resultsLoading ? (
              <div className="p-8 text-center text-xs text-slate-400 font-mono">
                Loading transaction records...
              </div>
            ) : results.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-400">
                No transactions match the selected filter criteria for Run #{activeRunId}.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-slate-800 bg-slate-950/60 text-slate-400 font-mono text-[11px]">
                      <th className="py-2.5 px-3 font-semibold">ID</th>
                      <th className="py-2.5 px-3 font-semibold">OUTCOME</th>
                      <th className="py-2.5 px-3 font-semibold">RULE</th>
                      <th className="py-2.5 px-3 font-semibold text-right">VARIANCE</th>
                      <th className="py-2.5 px-3 font-semibold">INTERNAL TX</th>
                      <th className="py-2.5 px-3 font-semibold">BANK REF</th>
                      <th className="py-2.5 px-3 font-semibold">EXPLANATION</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono text-xs">
                    {results.map((r) => {
                      const isExpanded = expandedRowId === r.id;
                      return (
                        <Fragment key={r.id}>
                          <tr
                            className="hover:bg-slate-850/40 transition-colors cursor-pointer group"
                            onClick={() => setExpandedRowId(isExpanded ? null : r.id)}
                          >
                          <td className="py-2.5 px-3 font-semibold text-slate-400">
                            #{r.id}
                          </td>
                          <td className="py-2.5 px-3">
                            <span
                              className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold border ${getResultBadgeStyle(
                                r.resultType,
                              )}`}
                            >
                              {r.resultType}
                            </span>
                          </td>
                          <td className="py-2.5 px-3 font-medium text-slate-200 text-[11px] truncate max-w-[140px]">
                            {r.ruleName}
                          </td>
                          <td className="py-2.5 px-3 tabular-nums text-right font-medium">
                            {r.amountDifference !== 0 &&
                            r.amountDifference !== null &&
                            r.amountDifference !== undefined ? (
                              <span className="text-amber-400">
                                {formatCurrency(r.amountDifference)}
                              </span>
                            ) : (
                              <span className="text-slate-500">{formatCurrency(0)}</span>
                            )}
                          </td>
                          <td
                            className="py-2.5 px-3 text-slate-400 max-w-[110px] truncate"
                            title={r.internalTransactionIds || ""}
                          >
                            {r.internalTransactionIds || "—"}
                          </td>
                          <td
                            className="py-2.5 px-3 text-slate-400 max-w-[110px] truncate"
                            title={r.bankReferences || ""}
                          >
                            {r.bankReferences || "—"}
                          </td>
                          <td className="py-2.5 px-3 text-slate-400 max-w-[180px] truncate text-[11px] font-sans">
                            <div className="flex items-center justify-between gap-1">
                              <span className="truncate">{r.explanation}</span>
                              {isExpanded ? (
                                <ChevronUp className="h-3 w-3 shrink-0 text-slate-500" />
                              ) : (
                                <ChevronDown className="h-3 w-3 shrink-0 text-slate-500 opacity-0 group-hover:opacity-100" />
                              )}
                            </div>
                          </td>
                        </tr>
                        {isExpanded && (
                          <tr className="bg-slate-900/60 border-b border-slate-800">
                            <td colSpan={7} className="p-3 text-xs text-slate-300 space-y-1.5 font-sans">
                              <div className="font-semibold text-white">Full Explanation:</div>
                              <p className="text-slate-400">{r.explanation}</p>
                              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-1 font-mono text-[11px]">
                                <div>
                                  <span className="text-slate-500">Internal Tx IDs:</span>{" "}
                                  <span className="text-indigo-300">{r.internalTransactionIds || "None"}</span>
                                </div>
                                <div>
                                  <span className="text-slate-500">Bank References:</span>{" "}
                                  <span className="text-emerald-300">{r.bankReferences || "None"}</span>
                                </div>
                              </div>
                            </td>
                          </tr>
                        )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between p-3 border-t border-slate-800 text-xs text-slate-400 font-mono bg-slate-950/40">
                <span>
                  Page {page + 1} of {totalPages} ({totalElements} total items)
                </span>
                <div className="flex items-center gap-1.5">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page === 0 || resultsLoading}
                    onClick={() => fetchResults(activeRunId, page - 1)}
                    className="h-7 w-7 p-0 bg-slate-900 border-slate-800 text-white"
                  >
                    <ChevronLeft className="h-3.5 w-3.5" />
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page >= totalPages - 1 || resultsLoading}
                    onClick={() => fetchResults(activeRunId, page + 1)}
                    className="h-7 w-7 p-0 bg-slate-900 border-slate-800 text-white"
                  >
                    <ChevronRight className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Sticky Analysis & Distribution Rail */}
        <div className="lg:col-span-4 space-y-4 sticky top-24">
          {/* Outcome Category Distribution */}
          {summary?.countPerResultType && Object.keys(summary.countPerResultType).length > 0 && (
            <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 shadow-xs space-y-3">
              <div className="flex items-center justify-between pb-1.5 border-b border-slate-800">
                <h3 className="text-xs font-semibold text-white uppercase tracking-wider font-mono">
                  Outcome Categories
                </h3>
                <span className="text-[10px] font-mono text-slate-400">Interactive Filter</span>
              </div>

              <div className="space-y-1">
                {Object.entries(summary.countPerResultType).map(([type, count]) => {
                  const isActive = resultTypeFilter === type;
                  return (
                    <button
                      key={type}
                      type="button"
                      onClick={() => handleTypeSelect(isActive ? "" : type)}
                      className={`w-full flex items-center justify-between p-2 rounded-lg text-xs font-mono transition-all border text-left ${
                        isActive
                          ? "bg-indigo-600/20 text-indigo-300 border-indigo-500/40"
                          : "bg-slate-950/40 text-slate-300 border-slate-800 hover:bg-slate-850"
                      }`}
                    >
                      <span className="font-sans font-medium text-[11px] truncate mr-2">
                        {type}
                      </span>
                      <span className="font-mono font-semibold tabular-nums px-1.5 py-0.5 rounded bg-slate-900 border border-slate-800 text-white text-[11px]">
                        {count}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Financial Reconciliation Ratio Card */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 shadow-xs space-y-3">
            <div className="flex items-center justify-between pb-1.5 border-b border-slate-800">
              <h3 className="text-xs font-semibold text-white uppercase tracking-wider font-mono">
                Matching Efficiency
              </h3>
              <span className="text-[10px] font-mono text-emerald-400">INR Balance</span>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-400">Total Run Volume</span>
                <span className="font-mono text-white font-semibold">
                  {formatCurrency(totalValueCalculated)}
                </span>
              </div>
              <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden flex">
                <div
                  className="bg-emerald-500 h-full transition-all duration-300"
                  style={{ width: `${matchRatio}%` }}
                  title={`Matched: ${matchRatio}%`}
                />
                <div
                  className="bg-rose-500 h-full transition-all duration-300"
                  style={{ width: `${100 - matchRatio}%` }}
                  title={`Variance: ${100 - matchRatio}%`}
                />
              </div>
              <div className="flex items-center justify-between text-[11px] font-mono text-slate-400">
                <span className="flex items-center gap-1">
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
                  Matched: {matchRatio}%
                </span>
                <span className="flex items-center gap-1">
                  <span className="h-1.5 w-1.5 rounded-full bg-rose-400" />
                  Variance: {100 - matchRatio}%
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
