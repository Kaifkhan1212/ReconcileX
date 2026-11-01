import { useAuth } from "@/lib/auth-context";
import type { ActiveTab } from "@/routes/index";
import { Button } from "@/components/ui/button";
import {
  Shield,
  Eye,
  FileSpreadsheet,
  LogIn,
  CheckCircle2,
  Zap,
  TrendingUp,
  ClipboardList,
} from "lucide-react";

interface GuestDashboardViewProps {
  onNavigate: (tab: ActiveTab) => void;
}

export function GuestDashboardView({ onNavigate }: GuestDashboardViewProps) {
  const { openLoginModal } = useAuth();

  return (
    <div className="space-y-8 max-w-5xl mx-auto">
      {/* Hero Banner */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 sm:p-8 shadow-xs">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
          <div className="space-y-3 max-w-xl">
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-white leading-tight">
              Automated Financial Reconciliation<br />
              <span className="text-indigo-400">Deterministic &amp; Auditable</span>
            </h1>
            <p className="text-sm text-slate-300 leading-relaxed">
              ReconcileX matches external bank statements against internal accounting ledgers
              through an ordered multi-pass matching pipeline — classifying exact matches,
              settlement tolerances, and ledger discrepancies with complete audit traceability.
            </p>
          </div>

          <div className="flex flex-col items-start lg:items-end gap-2 shrink-0">
            <Button
              size="lg"
              onClick={openLoginModal}
              className="h-10 px-6 text-xs font-semibold gap-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-md shadow-sm transition-all"
            >
              <LogIn className="h-4 w-4" />
              Sign In to ReconcileX
            </Button>
            <p className="text-[11px] text-slate-400">
              Access permissions are assigned automatically after sign in.
            </p>
          </div>
        </div>
      </div>

      {/* Workspace Access by Role (ADMIN & VIEWER) */}
      <div className="space-y-3">
        <div className="text-xs font-semibold tracking-wider text-slate-400 uppercase font-mono">
          Workspace Access by Role
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* ADMIN Workspace Card */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 space-y-4 hover:border-slate-700 transition-colors">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-3">
                <div className="h-9 w-9 rounded-lg bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center shrink-0">
                  <Shield className="h-4 w-4 text-indigo-400" />
                </div>
                <div>
                  <h3 className="text-sm font-semibold text-white">ADMIN Workspace</h3>
                  <span className="text-xs text-slate-400">Administrative &amp; Governance Operations</span>
                </div>
              </div>
              <span className="text-[10px] font-mono font-medium tracking-wider px-2 py-0.5 rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                ADMIN
              </span>
            </div>

            <ul className="space-y-2 text-xs text-slate-300">
              {[
                "Financial KPI dashboard and operational overview",
                "Upload bank statements and internal accounting CSV ledgers",
                "Execute and monitor deterministic multi-pass reconciliation runs",
                "Manage application users, role assignments, and platform settings",
                "Inspect the append-only compliance audit trail",
              ].map((item) => (
                <li key={item} className="flex items-start gap-2 leading-relaxed">
                  <CheckCircle2 className="h-3.5 w-3.5 text-indigo-400 shrink-0 mt-0.5" />
                  <span>{item}</span>
                </li>
              ))}
            </ul>

            <div className="pt-3 border-t border-slate-800/80 flex flex-col sm:flex-row sm:items-center justify-between gap-1.5 sm:gap-0 text-xs text-slate-400">
              <span>Access Permissions</span>
              <span className="text-[11px] font-mono bg-slate-800/80 px-2 py-0.5 rounded text-slate-300 border border-slate-700 w-fit">
                Assigned Automatically After Sign In
              </span>
            </div>
          </div>

          {/* VIEWER Workspace Card */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 space-y-4 hover:border-slate-700 transition-colors">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-3">
                <div className="h-9 w-9 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center shrink-0">
                  <Eye className="h-4 w-4 text-emerald-400" />
                </div>
                <div>
                  <h3 className="text-sm font-semibold text-white">VIEWER Workspace</h3>
                  <span className="text-xs text-slate-400">Financial Reporting &amp; Analytics</span>
                </div>
              </div>
              <span className="text-[10px] font-mono font-medium tracking-wider px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                VIEWER
              </span>
            </div>

            <ul className="space-y-2 text-xs text-slate-300">
              {[
                "Financial KPI dashboard with reconciliation metrics",
                "Browse complete reconciliation run history",
                "Inspect matched transactions, variance analysis, and exceptions",
                "Analyze discrepancy categories and variance explanations",
                "Download streamed reconciliation reports as CSV",
              ].map((item) => (
                <li key={item} className="flex items-start gap-2 leading-relaxed">
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>{item}</span>
                </li>
              ))}
            </ul>

            <div className="pt-3 border-t border-slate-800/80 flex flex-col sm:flex-row sm:items-center justify-between gap-1.5 sm:gap-0 text-xs text-slate-400">
              <span>Access Permissions</span>
              <span className="text-[11px] font-mono bg-slate-800/80 px-2 py-0.5 rounded text-slate-300 border border-slate-700 w-fit">
                Assigned Automatically After Sign In
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Platform Capabilities */}
      <div className="space-y-3">
        <div className="text-xs font-semibold tracking-wider text-slate-400 uppercase font-mono">
          Platform Capabilities
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
          {[
            {
              icon: FileSpreadsheet,
              title: "Ledger Ingestion",
              desc: "Upload external bank statements and internal accounting CSV ledgers with SHA-256 duplicate payload detection.",
            },
            {
              icon: Zap,
              title: "Multi-Pass Matching Pipeline",
              desc: "Ordered deterministic matching evaluates exact transactions, settlement tolerances, and batch payouts in INR.",
            },
            {
              icon: TrendingUp,
              title: "Variance & Outcome Reports",
              desc: "Granular categorization of matched values, timing variances, and missing records with streaming CSV export.",
            },
            {
              icon: ClipboardList,
              title: "Append-Only Audit Trail",
              desc: "Every data ingestion, match outcome, and administrative action is recorded chronologically in an immutable log.",
            },
          ].map(({ icon: Icon, title, desc }) => (
            <div
              key={title}
              className="rounded-xl border border-slate-800 bg-slate-900/30 p-4 space-y-2.5 hover:border-slate-700 transition-colors"
            >
              <div className="h-8 w-8 rounded-lg bg-slate-800 flex items-center justify-center text-slate-300">
                <Icon className="h-4 w-4" />
              </div>
              <div>
                <h4 className="text-xs font-semibold text-white">{title}</h4>
                <p className="text-[11px] text-slate-400 mt-1 leading-relaxed">{desc}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
