import { useState, useEffect } from "react";
import { useAuth } from "@/lib/auth-context";
import {
  getRunSummaryApi,
  getRunResultsApi,
  downloadReportCsvApi,
  type ResultsFilterParams,
} from "@/lib/api";
import type { RunSummaryResponse, ReconciliationResultDto } from "@/lib/types";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  BarChart3,
  Download,
  Filter,
  RefreshCw,
  Search,
  CheckCircle,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  DollarSign,
  FileSpreadsheet,
} from "lucide-react";

interface ResultsViewProps {
  initialRunId?: number;
}

export function ResultsView({ initialRunId }: ResultsViewProps) {
  const { token, isAuthenticated } = useAuth();

  const [runIdInput, setRunIdInput] = useState<string>(
    initialRunId ? initialRunId.toString() : "1"
  );
  const [activeRunId, setActiveRunId] = useState<number>(initialRunId || 1);

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

  const loadResults = async (id: number, pageNum = 0) => {
    setResultsLoading(true);
    try {
      const filters: ResultsFilterParams = {
        page: pageNum,
        size: 20,
      };
      if (resultTypeFilter) filters.resultType = resultTypeFilter;
      if (fromDateFilter) filters.fromDate = fromDateFilter;
      if (toDateFilter) filters.toDate = toDateFilter;

      const res = await getRunResultsApi(id, filters, token);
      setResults(res.content);
      setPage(res.number);
      setTotalPages(res.totalPages || 1);
      setTotalElements(res.totalElements);
    } catch {
      setResults([]);
    } finally {
      setResultsLoading(false);
    }
  };

  useEffect(() => {
    if (activeRunId) {
      loadSummary(activeRunId);
      loadResults(activeRunId, 0);
    }
  }, [activeRunId, token]);

  const handleRunSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const id = parseInt(runIdInput, 10);
    if (!isNaN(id) && id > 0) {
      setActiveRunId(id);
    }
  };

  const handleApplyFilters = (e: React.FormEvent) => {
    e.preventDefault();
    loadResults(activeRunId, 0);
  };

  const handleDownloadCsv = async () => {
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
    if (val === undefined || val === null) return "$0.00";
    return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(val);
  };

  const getResultBadgeColor = (type: string) => {
    if (type === "EXACT_MATCH") return "bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/30";
    if (type.includes("MISMATCH")) return "bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/30";
    if (type.includes("MISSING") || type.includes("DUPLICATE")) return "bg-rose-500/10 text-rose-700 dark:text-rose-400 border-rose-500/30";
    return "bg-secondary text-secondary-foreground border-border";
  };

  return (
    <div className="space-y-6">
      {/* Top Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">
            Run Results & Financial Summary
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Analyze deterministic multi-pass matches, investigate financial variances, and export reports.
          </p>
        </div>

        <form onSubmit={handleRunSearch} className="flex items-center gap-2">
          <Label htmlFor="run-id" className="text-xs whitespace-nowrap">Run ID:</Label>
          <Input
            id="run-id"
            type="number"
            min="1"
            value={runIdInput}
            onChange={(e) => setRunIdInput(e.target.value)}
            className="w-24 h-9"
          />
          <Button type="submit" size="sm" variant="secondary" className="h-9 gap-1">
            <Search className="h-3.5 w-3.5" />
            Load
          </Button>
          <Button
            type="button"
            size="sm"
            onClick={handleDownloadCsv}
            disabled={csvDownloading || !summary}
            className="h-9 gap-1.5"
          >
            <Download className="h-3.5 w-3.5" />
            {csvDownloading ? "Exporting..." : "Export CSV"}
          </Button>
        </form>
      </div>

      {/* Financial Summary */}
      {summary && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
            <Card className="border-emerald-500/30 bg-emerald-500/5">
              <CardContent className="p-4">
                <span className="text-[11px] font-medium text-emerald-700 dark:text-emerald-400 uppercase">
                  Matched Value
                </span>
                <div className="text-lg font-bold text-foreground mt-1">
                  {formatCurrency(summary.matchedValue)}
                </div>
                <p className="text-[10px] text-muted-foreground mt-0.5">Paired matching results</p>
              </CardContent>
            </Card>

            <Card className="border-rose-500/30 bg-rose-500/5">
              <CardContent className="p-4">
                <span className="text-[11px] font-medium text-rose-700 dark:text-rose-400 uppercase">
                  Unmatched Internal
                </span>
                <div className="text-lg font-bold text-foreground mt-1">
                  {formatCurrency(summary.unmatchedInternalValue)}
                </div>
                <p className="text-[10px] text-muted-foreground mt-0.5">Ledger rows missing in bank</p>
              </CardContent>
            </Card>

            <Card className="border-amber-500/30 bg-amber-500/5">
              <CardContent className="p-4">
                <span className="text-[11px] font-medium text-amber-700 dark:text-amber-400 uppercase">
                  Unmatched Bank (Cr)
                </span>
                <div className="text-lg font-bold text-foreground mt-1">
                  {formatCurrency(summary.unmatchedBankCreditValue)}
                </div>
                <p className="text-[10px] text-muted-foreground mt-0.5">Deposits missing in internal</p>
              </CardContent>
            </Card>

            <Card className="border-amber-500/30 bg-amber-500/5">
              <CardContent className="p-4">
                <span className="text-[11px] font-medium text-amber-700 dark:text-amber-400 uppercase">
                  Unmatched Bank (Dr)
                </span>
                <div className="text-lg font-bold text-foreground mt-1">
                  {formatCurrency(summary.unmatchedBankDebitValue)}
                </div>
                <p className="text-[10px] text-muted-foreground mt-0.5">Withdrawals missing in internal</p>
              </CardContent>
            </Card>

            <Card className="border-indigo-500/30 bg-indigo-500/5">
              <CardContent className="p-4">
                <span className="text-[11px] font-medium text-indigo-700 dark:text-indigo-400 uppercase">
                  Explained Value
                </span>
                <div className="text-lg font-bold text-foreground mt-1">
                  {formatCurrency(summary.explainedValue)}
                </div>
                <p className="text-[10px] text-muted-foreground mt-0.5">Bank charges / ignored statuses</p>
              </CardContent>
            </Card>
          </div>

          {/* Counts Per Rule Badge Row */}
          {summary.countPerResultType && (
            <Card>
              <CardContent className="p-3">
                <div className="text-xs font-semibold text-muted-foreground mb-2">
                  Result Types Breakdown:
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {Object.entries(summary.countPerResultType).map(([type, count]) => (
                    <Badge
                      key={type}
                      variant="outline"
                      className={`text-[11px] cursor-pointer hover:opacity-80 ${
                        resultTypeFilter === type ? "ring-2 ring-primary" : ""
                      }`}
                      onClick={() => {
                        setResultTypeFilter(resultTypeFilter === type ? "" : type);
                      }}
                    >
                      {type}: <strong className="ml-1">{count}</strong>
                    </Badge>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      )}

      {/* Results Filter & Table */}
      <Card>
        <CardHeader className="pb-3">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <CardTitle className="text-base">Results Table ({totalElements})</CardTitle>
              <CardDescription className="text-xs">
                Detailed outcomes from deterministic multi-pass rules
              </CardDescription>
            </div>

            <form onSubmit={handleApplyFilters} className="flex flex-wrap items-center gap-2">
              <Input
                placeholder="Filter by Result Type..."
                value={resultTypeFilter}
                onChange={(e) => setResultTypeFilter(e.target.value)}
                className="h-8 w-44 text-xs"
              />
              <Input
                type="date"
                value={fromDateFilter}
                onChange={(e) => setFromDateFilter(e.target.value)}
                className="h-8 w-32 text-xs"
                placeholder="From date"
              />
              <Input
                type="date"
                value={toDateFilter}
                onChange={(e) => setToDateFilter(e.target.value)}
                className="h-8 w-32 text-xs"
                placeholder="To date"
              />
              <Button type="submit" size="sm" variant="outline" className="h-8 text-xs gap-1">
                <Filter className="h-3 w-3" />
                Filter
              </Button>
            </form>
          </div>
        </CardHeader>

        <CardContent>
          {results.length === 0 ? (
            <div className="text-center py-10 text-sm text-muted-foreground">
              {resultsLoading ? "Loading results..." : "No results match the specified criteria."}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-border bg-muted/40 font-semibold text-muted-foreground">
                    <th className="py-2.5 px-3">ID</th>
                    <th className="py-2.5 px-3">Result Type</th>
                    <th className="py-2.5 px-3">Rule Name</th>
                    <th className="py-2.5 px-3">Diff</th>
                    <th className="py-2.5 px-3">Internal IDs</th>
                    <th className="py-2.5 px-3">Bank Refs</th>
                    <th className="py-2.5 px-3">Explanation</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {results.map((r) => (
                    <tr key={r.id} className="hover:bg-muted/30 transition-colors">
                      <td className="py-2.5 px-3 font-medium">#{r.id}</td>
                      <td className="py-2.5 px-3">
                        <span className={`inline-block px-2 py-0.5 rounded text-[10px] font-semibold border ${getResultBadgeColor(r.resultType)}`}>
                          {r.resultType}
                        </span>
                      </td>
                      <td className="py-2.5 px-3 font-medium text-foreground">{r.ruleName}</td>
                      <td className="py-2.5 px-3">
                        {r.amountDifference !== 0 ? (
                          <span className="text-amber-600 font-semibold">
                            {formatCurrency(r.amountDifference)}
                          </span>
                        ) : (
                          <span className="text-muted-foreground">$0.00</span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-muted-foreground max-w-[150px] truncate" title={r.internalTransactionIds || ""}>
                        {r.internalTransactionIds || "—"}
                      </td>
                      <td className="py-2.5 px-3 text-muted-foreground max-w-[150px] truncate" title={r.bankReferences || ""}>
                        {r.bankReferences || "—"}
                      </td>
                      <td className="py-2.5 px-3 text-muted-foreground max-w-[250px] truncate" title={r.explanation}>
                        {r.explanation}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {totalPages > 1 && (
            <div className="flex items-center justify-between pt-4 border-t border-border mt-4 text-xs text-muted-foreground">
              <span>
                Page {page + 1} of {totalPages}
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0 || resultsLoading}
                  onClick={() => loadResults(activeRunId, page - 1)}
                  className="h-8 w-8 p-0"
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1 || resultsLoading}
                  onClick={() => loadResults(activeRunId, page + 1)}
                  className="h-8 w-8 p-0"
                >
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
