import { useState, useEffect } from "react";
import { useAuth } from "@/lib/auth-context";
import { getAuditEventsApi, getHealthApi, type AuditFilterParams } from "@/lib/api";
import { AUDIT_EVENT_TYPES } from "@/lib/matching-rules";
import type { AuditEventDto } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  History,
  Activity,
  Filter,
  RefreshCw,
  Database,
  ChevronLeft,
  ChevronRight,
  FileCheck2,
  RotateCcw,
  ChevronDown,
  ChevronUp,
} from "lucide-react";

export function AuditView() {
  const { token } = useAuth();

  const [events, setEvents] = useState<AuditEventDto[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  const [runIdFilter, setRunIdFilter] = useState<string>("");
  const [eventTypeFilter, setEventTypeFilter] = useState<string>("");

  const [healthStatus, setHealthStatus] = useState<string>("UP");
  const [healthLoading, setHealthLoading] = useState(false);
  const [expandedRowId, setExpandedRowId] = useState<number | null>(null);

  const checkHealth = async () => {
    setHealthLoading(true);
    try {
      const res = await getHealthApi();
      setHealthStatus(res.status);
    } catch {
      setHealthStatus("DOWN");
    } finally {
      setHealthLoading(false);
    }
  };

  const loadAuditEvents = async (pageNum = 0) => {
    setLoading(true);
    try {
      const params: AuditFilterParams = {
        page: pageNum,
        size: 20,
      };
      if (runIdFilter) {
        const id = parseInt(runIdFilter, 10);
        if (!isNaN(id)) params.runId = id;
      }
      if (eventTypeFilter.trim()) params.eventType = eventTypeFilter.trim();

      const res = await getAuditEventsApi(params, token);
      setEvents(res.content);
      setPage(res.number);
      setTotalPages(res.totalPages || 1);
      setTotalElements(res.totalElements);
    } catch {
      setEvents([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    checkHealth();
    loadAuditEvents(0);
  }, [token]);

  const handleFilter = (e: React.FormEvent) => {
    e.preventDefault();
    loadAuditEvents(0);
  };

  const handleResetFilters = () => {
    setRunIdFilter("");
    setEventTypeFilter("");
    setLoading(true);
    getAuditEventsApi({ page: 0, size: 20 }, token)
      .then((res) => {
        setEvents(res.content);
        setPage(res.number);
        setTotalPages(res.totalPages || 1);
        setTotalElements(res.totalElements);
      })
      .finally(() => setLoading(false));
  };

  const getEventTypeBadgeStyle = (eventType: string) => {
    if (eventType.includes("RUN")) {
      return "bg-indigo-500/10 text-indigo-400 border-indigo-500/20";
    }
    if (eventType.includes("IMPORT")) {
      return "bg-emerald-500/10 text-emerald-400 border-emerald-500/20";
    }
    if (eventType.includes("MATCH")) {
      return "bg-blue-500/10 text-blue-400 border-blue-500/20";
    }
    if (eventType.includes("USER") || eventType.includes("LOGIN")) {
      return "bg-amber-500/10 text-amber-400 border-amber-500/20";
    }
    return "bg-slate-800 text-slate-300 border-slate-700";
  };

  const formatJsonDetails = (raw: string | undefined): string => {
    if (!raw) return "—";
    try {
      const parsed = JSON.parse(raw);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return raw;
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 p-5 rounded-xl border border-slate-800 bg-slate-900/50 shadow-xs">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5 flex-wrap">
            <h1 className="text-lg font-semibold tracking-tight text-white">
              Audit Trail &amp; Compliance Logs
            </h1>
            <span className="inline-flex items-center rounded-md border border-slate-700 bg-slate-800 px-2 py-0.5 text-xs font-mono font-medium text-slate-300">
              Append-Only Log
            </span>
            <span className="inline-flex items-center rounded-md border border-slate-700 bg-slate-800 text-slate-300 px-2 py-0.5 text-xs font-mono font-medium uppercase">
              ADMIN
            </span>
          </div>
          <p className="text-xs text-slate-400 max-w-2xl leading-relaxed">
            Append-only compliance log recording file ingestions, deterministic matching runs, and security events for regulatory audit.
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            checkHealth();
            loadAuditEvents(page);
          }}
          disabled={loading || healthLoading}
          className="h-8 text-xs font-medium gap-1.5 border-slate-800 bg-slate-900 text-slate-300 hover:text-white shrink-0"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading || healthLoading ? "animate-spin" : ""}`} />
          <span>Refresh Audit Log</span>
        </Button>
      </div>

      {/* Activity & Compliance Telemetry Rail */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Metric 1 */}
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 shadow-xs">
          <div className="flex items-center justify-between pb-2">
            <span className="text-xs font-medium text-slate-400">Compliance Records</span>
            <FileCheck2 className="h-4 w-4 text-indigo-400" />
          </div>
          <div className="text-xl font-bold text-white font-mono tabular-nums">
            {totalElements}
          </div>
          <p className="text-[11px] text-slate-400 mt-1">
            Total immutable compliance records
          </p>
        </div>

        {/* Metric 2 */}
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 shadow-xs">
          <div className="flex items-center justify-between pb-2">
            <span className="text-xs font-medium text-slate-400">Storage Policy</span>
            <Database className="h-4 w-4 text-emerald-400" />
          </div>
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-emerald-500" />
            <span className="text-base font-semibold text-emerald-400 font-mono">
              Append-Only
            </span>
          </div>
          <p className="text-[11px] text-slate-400 mt-1">
            Enforced by PostgreSQL V4 trigger
          </p>
        </div>

        {/* Metric 3 */}
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 shadow-xs">
          <div className="flex items-center justify-between pb-2">
            <span className="text-xs font-medium text-slate-400">Backend Connectivity</span>
            <Activity className="h-4 w-4 text-indigo-400" />
          </div>
          <div className="flex items-center gap-2">
            <span
              className={`h-2 w-2 rounded-full ${
                healthStatus === "UP" ? "bg-emerald-500 animate-pulse" : "bg-rose-500"
              }`}
            />
            <span className="text-base font-semibold text-white font-mono">
              {healthStatus === "UP" ? "Reachable" : "Offline"}
            </span>
          </div>
          <p className="text-[11px] text-slate-400 mt-1">
            GET /api/health responding with HTTP 200
          </p>
        </div>
      </div>

      {/* Audit Log Table & Filters */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 shadow-xs overflow-hidden">
        {/* Header Row */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/40">
          <div className="flex items-center gap-2.5">
            <div className="h-7 w-7 rounded-lg bg-slate-800 flex items-center justify-center text-slate-300">
              <History className="h-4 w-4" />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-white">
                Audit Trail Records ({totalElements})
              </h2>
              <p className="text-xs text-slate-400 mt-0.5">
                Append-only chronological decision trail (newest first)
              </p>
            </div>
          </div>

          {(runIdFilter || eventTypeFilter) && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleResetFilters}
              className="h-7 text-xs text-slate-400 hover:text-white gap-1"
            >
              <RotateCcw className="h-3 w-3" />
              <span>Reset</span>
            </Button>
          )}
        </div>

        {/* Filter Toolbar */}
        <div className="p-3 border-b border-slate-800 bg-slate-950/60">
          <form onSubmit={handleFilter} className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2 w-full sm:w-auto">
              <label htmlFor="audit-run-id" className="text-xs text-slate-400 font-medium shrink-0">
                Run ID:
              </label>
              <Input
                id="audit-run-id"
                type="number"
                min="1"
                placeholder="e.g. 1"
                value={runIdFilter}
                onChange={(e) => setRunIdFilter(e.target.value)}
                className="h-8 flex-1 sm:w-24 font-mono text-xs bg-slate-900 border-slate-800 text-white"
              />
            </div>

            <div className="flex items-center gap-2 w-full sm:w-auto sm:min-w-[240px]">
              <label htmlFor="audit-event-type" className="text-xs text-slate-400 font-medium shrink-0">
                Event Type:
              </label>
              <select
                id="audit-event-type"
                value={eventTypeFilter}
                onChange={(e) => setEventTypeFilter(e.target.value)}
                className="h-8 flex-1 rounded-md bg-slate-900 border border-slate-800 text-white text-xs px-2.5 py-1 focus:outline-none focus:ring-1 focus:ring-indigo-500 font-mono"
              >
                <option value="">All Event Types</option>
                {AUDIT_EVENT_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>

            <Button
              type="submit"
              size="sm"
              className="h-8 text-xs font-medium gap-1.5 bg-slate-800 border border-slate-700 text-white hover:bg-slate-700 shadow-xs px-3.5 w-full sm:w-auto"
            >
              <Filter className="h-3 w-3 text-indigo-400" />
              <span>Filter</span>
            </Button>
          </form>

          <p className="text-[11px] text-slate-500 mt-2 font-mono">
            Note: All audit events are stored chronologically and ordered by occurred_at DESC.
          </p>
        </div>

        {/* Events Table */}
        <div>
          {loading ? (
            <div className="p-8 text-center text-xs text-slate-400 font-mono">
              Loading audit events...
            </div>
          ) : events.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-400">
              No audit events found matching the filter criteria.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-800 bg-slate-950/60 text-slate-400 font-mono text-[11px]">
                    <th className="py-2.5 px-3 font-semibold">EVENT ID</th>
                    <th className="py-2.5 px-3 font-semibold">RUN ID</th>
                    <th className="py-2.5 px-3 font-semibold">EVENT TYPE</th>
                    <th className="py-2.5 px-3 font-semibold">ACTOR</th>
                    <th className="py-2.5 px-3 font-semibold">DETAILS</th>
                    <th className="py-2.5 px-3 font-semibold">TIMESTAMP</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-mono text-xs">
                  {events.map((ev) => {
                    const isExpanded = expandedRowId === ev.id;
                    return (
                      <tr
                        key={ev.id}
                        className="hover:bg-slate-850/40 transition-colors cursor-pointer"
                        onClick={() => setExpandedRowId(isExpanded ? null : ev.id)}
                      >
                        <td className="py-2.5 px-3 font-semibold text-white">
                          #{ev.id}
                        </td>
                        <td className="py-2.5 px-3 text-slate-400">
                          {ev.runId ? `#${ev.runId}` : "—"}
                        </td>
                        <td className="py-2.5 px-3">
                          <span
                            className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold border ${getEventTypeBadgeStyle(
                              ev.eventType,
                            )}`}
                          >
                            {ev.eventType}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-slate-300 font-medium">
                          {ev.actor}
                        </td>
                        <td className="py-2.5 px-3 text-slate-400 max-w-xs">
                          {isExpanded ? (
                            <div className="space-y-1">
                              <pre className="p-2 rounded bg-slate-950 border border-slate-800 text-[10px] text-slate-300 overflow-x-auto max-h-40 whitespace-pre-wrap">
                                {formatJsonDetails(ev.details)}
                              </pre>
                              <span className="text-[10px] text-indigo-400 inline-flex items-center gap-0.5">
                                <ChevronUp className="h-3 w-3" /> Click to collapse
                              </span>
                            </div>
                          ) : (
                            <div className="flex items-center justify-between gap-2">
                              <span className="truncate block font-mono text-[11px]">
                                {ev.details || "—"}
                              </span>
                              <ChevronDown className="h-3 w-3 shrink-0 text-slate-500" />
                            </div>
                          )}
                        </td>
                        <td className="py-2.5 px-3 text-slate-400 tabular-nums text-[11px] whitespace-nowrap">
                          {new Date(ev.occurredAt).toLocaleString(undefined, {
                            month: "short",
                            day: "2-digit",
                            hour: "2-digit",
                            minute: "2-digit",
                            second: "2-digit",
                          })}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between p-3 border-t border-slate-800 text-xs text-slate-400 font-mono bg-slate-950/40">
              <span>
                Page {page + 1} of {totalPages} ({totalElements} events)
              </span>
              <div className="flex items-center gap-1.5">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0 || loading}
                  onClick={() => loadAuditEvents(page - 1)}
                  className="h-7 w-7 p-0 bg-slate-900 border-slate-800 text-white"
                >
                  <ChevronLeft className="h-3.5 w-3.5" />
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1 || loading}
                  onClick={() => loadAuditEvents(page + 1)}
                  className="h-7 w-7 p-0 bg-slate-900 border-slate-800 text-white"
                >
                  <ChevronRight className="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
