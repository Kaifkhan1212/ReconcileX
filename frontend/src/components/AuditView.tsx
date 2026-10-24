import { useState, useEffect } from "react";
import { useAuth } from "@/lib/auth-context";
import { getAuditEventsApi, getHealthApi, type AuditFilterParams } from "@/lib/api";
import type { AuditEventDto } from "@/lib/types";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  History,
  Activity,
  Filter,
  RefreshCw,
  ShieldCheck,
  Server,
  Database,
  ExternalLink,
  ChevronLeft,
  ChevronRight,
  ShieldAlert,
} from "lucide-react";

export function AuditView() {
  const { token, isAdmin, isAuthenticated } = useAuth();

  const [events, setEvents] = useState<AuditEventDto[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  const [runIdFilter, setRunIdFilter] = useState<string>("");
  const [eventTypeFilter, setEventTypeFilter] = useState<string>("");

  const [healthStatus, setHealthStatus] = useState<string>("CHECKING");
  const [healthLoading, setHealthLoading] = useState(false);

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
      if (eventTypeFilter) params.eventType = eventTypeFilter;

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

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">
            Audit Trail & System Health
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Immutable decision log and live health monitoring for the ReconcileX payment engine.
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
          className="gap-1.5 self-start sm:self-auto"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading || healthLoading ? "animate-spin" : ""}`} />
          Refresh
        </Button>
      </div>

      {/* System Health Diagnostics Card */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <CardTitle className="text-xs font-semibold text-muted-foreground uppercase">
                Backend Service
              </CardTitle>
              <Server className="h-4 w-4 text-primary" />
            </div>
          </CardHeader>
          <CardContent className="space-y-1">
            <div className="flex items-center gap-2">
              <span className={`h-2.5 w-2.5 rounded-full ${healthStatus === "UP" ? "bg-emerald-500 animate-pulse" : "bg-destructive"}`} />
              <span className="font-bold text-foreground">Spring Boot 4 Engine</span>
            </div>
            <p className="text-xs text-muted-foreground">
              Port: 8081 • Context: /api/health ({healthStatus})
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <CardTitle className="text-xs font-semibold text-muted-foreground uppercase">
                Database Store
              </CardTitle>
              <Database className="h-4 w-4 text-indigo-500" />
            </div>
          </CardHeader>
          <CardContent className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="h-2.5 w-2.5 rounded-full bg-emerald-500" />
              <span className="font-bold text-foreground">PostgreSQL 16</span>
            </div>
            <p className="text-xs text-muted-foreground">
              Container: reconcilex-postgres-1 • Port 5433
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <CardTitle className="text-xs font-semibold text-muted-foreground uppercase">
                API Docs & Specifications
              </CardTitle>
              <ExternalLink className="h-4 w-4 text-muted-foreground" />
            </div>
          </CardHeader>
          <CardContent className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="font-bold text-foreground">OpenAPI 3.1.0</span>
            </div>
            <p className="text-xs text-muted-foreground">
              <a
                href="http://localhost:8081/swagger-ui/index.html"
                target="_blank"
                rel="noreferrer"
                className="text-primary hover:underline"
              >
                Open Swagger UI ↗
              </a>
              {" • "}
              <a
                href="http://localhost:8081/v3/api-docs"
                target="_blank"
                rel="noreferrer"
                className="text-primary hover:underline"
              >
                /v3/api-docs ↗
              </a>
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Audit Log Table */}
      <Card>
        <CardHeader className="pb-3">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <div className="flex items-center gap-2">
                <History className="h-5 w-5 text-muted-foreground" />
                <CardTitle className="text-base">Audit Trail Records ({totalElements})</CardTitle>
              </div>
              <CardDescription className="text-xs">
                Immutable chronological event trail written directly to PostgreSQL
              </CardDescription>
            </div>

            <form onSubmit={handleFilter} className="flex flex-wrap items-center gap-2">
              <Input
                placeholder="Run ID (optional)"
                type="number"
                min="1"
                value={runIdFilter}
                onChange={(e) => setRunIdFilter(e.target.value)}
                className="h-8 w-28 text-xs"
              />
              <Input
                placeholder="Event Type filter..."
                value={eventTypeFilter}
                onChange={(e) => setEventTypeFilter(e.target.value)}
                className="h-8 w-44 text-xs"
              />
              <Button type="submit" size="sm" variant="outline" className="h-8 text-xs gap-1">
                <Filter className="h-3 w-3" />
                Filter
              </Button>
            </form>
          </div>
        </CardHeader>

        <CardContent>
          {!isAdmin && (
            <div className="flex items-center gap-3 p-3 mb-4 rounded-lg border border-amber-500/20 bg-amber-500/10 text-amber-800 dark:text-amber-300 text-xs">
              <ShieldAlert className="h-4 w-4 shrink-0" />
              <span>
                {isAuthenticated
                  ? "Viewing audit logs requires an ADMIN role. Please sign in as an administrator."
                  : "Sign in with an ADMIN account to view the audit log."}
              </span>
            </div>
          )}

          {events.length === 0 ? (
            <div className="text-center py-10 text-sm text-muted-foreground">
              {loading ? "Loading audit events..." : "No audit events recorded."}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-border bg-muted/40 font-semibold text-muted-foreground">
                    <th className="py-2.5 px-3">Event ID</th>
                    <th className="py-2.5 px-3">Run ID</th>
                    <th className="py-2.5 px-3">Event Type</th>
                    <th className="py-2.5 px-3">Actor</th>
                    <th className="py-2.5 px-3">Details</th>
                    <th className="py-2.5 px-3">Timestamp</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {events.map((evt) => (
                    <tr key={evt.id} className="hover:bg-muted/30 transition-colors">
                      <td className="py-2.5 px-3 font-medium">#{evt.id}</td>
                      <td className="py-2.5 px-3">
                        {evt.runId ? (
                          <Badge variant="outline" className="text-[10px]">
                            Run #{evt.runId}
                          </Badge>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </td>
                      <td className="py-2.5 px-3">
                        <Badge variant="secondary" className="text-[10px] font-mono">
                          {evt.eventType}
                        </Badge>
                      </td>
                      <td className="py-2.5 px-3 font-medium">{evt.actor}</td>
                      <td className="py-2.5 px-3 text-muted-foreground max-w-[300px] truncate" title={evt.details}>
                        {evt.details}
                      </td>
                      <td className="py-2.5 px-3 text-muted-foreground whitespace-nowrap">
                        {new Date(evt.occurredAt).toLocaleString()}
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
                  disabled={page === 0 || loading}
                  onClick={() => loadAuditEvents(page - 1)}
                  className="h-8 w-8 p-0"
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1 || loading}
                  onClick={() => loadAuditEvents(page + 1)}
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
