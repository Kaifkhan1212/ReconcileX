import { useState, useEffect } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { useAuth } from "@/lib/auth-context";
import { Sidebar } from "@/components/Sidebar";
import { DashboardView } from "@/components/DashboardView";
import { ImportsView } from "@/components/ImportsView";
import { RunsView } from "@/components/RunsView";
import { ResultsView } from "@/components/ResultsView";
import { AuditView } from "@/components/AuditView";
import { UserManagementView } from "@/components/UserManagementView";
import { SettingsView } from "@/components/SettingsView";
import { BackendHealthIndicator } from "@/components/BackendHealthIndicator";
import { Shield, AlertTriangle, X, Menu } from "lucide-react";
import { Button } from "@/components/ui/button";

export type ActiveTab =
  | "dashboard"
  | "imports"
  | "runs"
  | "results"
  | "audit"
  | "users"
  | "settings";

export const Route = createFileRoute("/")({
  component: Index,
});

const ADMIN_ONLY_TABS: ActiveTab[] = ["imports", "audit", "users", "settings"];

function Index() {
  const {
    isAdmin,
    user,
    isAuthenticated,
    sessionExpired,
    dismissSessionExpired,
    openLoginModal,
  } = useAuth();
  const [activeTab, setActiveTab] = useState<ActiveTab>("dashboard");
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(false);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState<boolean>(false);
  const [selectedRunId, setSelectedRunId] = useState<number | undefined>(undefined);
  const [bankImportId, setBankImportId] = useState<number | undefined>(undefined);
  const [internalImportId, setInternalImportId] = useState<number | undefined>(undefined);

  // Automatic RBAC redirect: if non-admin attempts an ADMIN-only tab, redirect to dashboard
  useEffect(() => {
    if (!isAdmin && ADMIN_ONLY_TABS.includes(activeTab)) {
      setActiveTab("dashboard");
    }
  }, [isAdmin, activeTab]);

  const handleNavigate = (tab: ActiveTab, runId?: number) => {
    if (!isAdmin && ADMIN_ONLY_TABS.includes(tab)) {
      setActiveTab("dashboard");
      return;
    }
    if (runId !== undefined) {
      setSelectedRunId(runId);
    }
    setActiveTab(tab);
    setMobileSidebarOpen(false);
  };

  const handleProceedToRun = (bId: number, iId: number) => {
    setBankImportId(bId);
    setInternalImportId(iId);
    setActiveTab("runs");
    setMobileSidebarOpen(false);
  };

  const handleViewRunResults = (runId: number) => {
    setSelectedRunId(runId);
    setActiveTab("results");
    setMobileSidebarOpen(false);
  };

  const pageTitles: Record<ActiveTab, { title: string; subtitle: string }> = {
    dashboard: {
      title: "Financial Dashboard",
      subtitle: "Reconciliation operations, performance metrics & recent activity",
    },
    imports: {
      title: "File Imports",
      subtitle: "Upload bank statements and internal accounting ledgers",
    },
    runs: {
      title: "Reconciliation Runs",
      subtitle: "Execute and monitor multi-pass transaction matching runs",
    },
    results: {
      title: "Results & Reports",
      subtitle: "Transaction outcomes, financial variance breakdown and CSV export",
    },
    audit: {
      title: "Audit Trail",
      subtitle: "Append-only compliance log of all reconciliation and administrative events",
    },
    users: {
      title: "User Management",
      subtitle: "Provision application accounts with role-based access control",
    },
    settings: {
      title: "Platform Specifications",
      subtitle: "Engine architecture, matching rule hierarchy and system constraints",
    },
  };

  const currentMeta = pageTitles[activeTab] || pageTitles.dashboard;
  const isGuestDashboard = !isAuthenticated && activeTab === "dashboard";
  const displayTitle = isGuestDashboard ? "ReconcileX Workspace" : currentMeta.title;
  const displaySubtitle = isGuestDashboard
    ? "Automated financial reconciliation & multi-pass matching engine"
    : currentMeta.subtitle;
  const displayTag = isGuestDashboard ? "GUEST" : activeTab.toUpperCase();

  return (
    <div className="min-h-screen bg-[#07090e] text-slate-100 flex flex-row antialiased overflow-x-hidden">
      {/* Responsive Command Sidebar */}
      <Sidebar
        activeTab={activeTab}
        onTabChange={handleNavigate}
        collapsed={sidebarCollapsed}
        onToggleCollapse={() => setSidebarCollapsed(!sidebarCollapsed)}
        mobileOpen={mobileSidebarOpen}
        onCloseMobile={() => setMobileSidebarOpen(false)}
      />

      {/* Main Content Arena with Responsive Margins */}
      <div
        className={`flex-1 flex flex-col min-w-0 transition-all duration-200 ml-0 ${
          sidebarCollapsed ? "md:ml-16" : "md:ml-64"
        }`}
      >
        {/* Session Expired Banner */}
        {sessionExpired && (
          <div className="bg-amber-500/10 border-b border-amber-500/30 px-4 sm:px-6 py-2.5 text-xs text-amber-200 flex items-center justify-between shrink-0 z-30">
            <div className="flex items-center gap-2 flex-wrap">
              <AlertTriangle className="h-4 w-4 shrink-0 text-amber-400" />
              <span className="font-medium">
                Your session has expired. Please sign in again to continue.
              </span>
              <Button
                size="sm"
                variant="outline"
                className="h-6 px-2.5 text-[11px] ml-2 border-amber-500/40 text-amber-200 hover:bg-amber-500/20 bg-transparent"
                onClick={openLoginModal}
              >
                Sign In
              </Button>
            </div>
            <button
              onClick={dismissSessionExpired}
              className="text-amber-400 hover:text-amber-100 p-1 rounded hover:bg-amber-500/20 transition-colors"
              title="Dismiss"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}

        {/* Top Header Command Rail */}
        <header className="sticky top-0 z-20 h-16 border-b border-slate-800/80 bg-[#07090e]/95 backdrop-blur-sm px-4 sm:px-6 flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            {/* Mobile Hamburger Toggle Button */}
            <button
              type="button"
              onClick={() => setMobileSidebarOpen(true)}
              className="p-1.5 rounded-lg border border-slate-800 bg-slate-900 text-slate-300 hover:text-white md:hidden shrink-0"
              aria-label="Open navigation menu"
            >
              <Menu className="h-5 w-5" />
            </button>

            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h1 className="text-sm font-semibold tracking-tight text-slate-100 truncate">
                  {displayTitle}
                </h1>
                <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 border border-slate-700 shrink-0">
                  {displayTag}
                </span>
              </div>
              <p className="text-[12px] text-slate-400 truncate max-w-xl hidden sm:block">
                {displaySubtitle}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2.5 shrink-0">
            {isAuthenticated && user && (
              <div className="hidden md:flex items-center gap-2 px-2.5 py-1 rounded-md bg-slate-900 border border-slate-800 text-[11px] font-mono text-slate-300">
                <Shield className="h-3.5 w-3.5 text-indigo-400" />
                <span className="font-medium text-slate-100">{user.username}</span>
                <span className="text-slate-600">•</span>
                <span
                  className={`inline-flex items-center px-1.5 py-0.2 rounded font-semibold text-[10px] ${
                    isAdmin
                      ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                      : "bg-slate-800 text-slate-300 border border-slate-700"
                  }`}
                >
                  {user.role}
                </span>
              </div>
            )}
            <BackendHealthIndicator />
          </div>
        </header>

        {/* Dynamic Screen Viewport with Responsive Padding */}
        <main className="flex-1 p-3.5 sm:p-6 overflow-x-hidden">
          {activeTab === "dashboard" && <DashboardView onNavigate={handleNavigate} />}
          {activeTab === "imports" && isAdmin && (
            <ImportsView onProceedToRun={handleProceedToRun} />
          )}
          {activeTab === "runs" && (
            <RunsView
              initialBankImportId={bankImportId}
              initialInternalImportId={internalImportId}
              onViewRunResults={handleViewRunResults}
            />
          )}
          {activeTab === "results" && <ResultsView initialRunId={selectedRunId} />}
          {activeTab === "audit" && isAdmin && <AuditView />}
          {activeTab === "users" && isAdmin && <UserManagementView />}
          {activeTab === "settings" && isAdmin && <SettingsView />}
        </main>

        {/* Global Footer */}
        <footer className="border-t border-slate-800/80 py-3 px-4 sm:px-6 bg-[#07090e] text-[12px] text-slate-400 flex flex-col sm:flex-row items-center justify-between gap-2">
          <div className="flex items-center gap-2 text-center sm:text-left">
            <span className="font-semibold text-slate-200">ReconcileX</span>
            <span className="text-slate-600">—</span>
            <span>Deterministic Multi-Pass Financial Reconciliation Engine</span>
          </div>
          <div className="text-[11px] font-mono text-slate-500">
            Append-Only Audit Enforced
          </div>
        </footer>
      </div>
    </div>
  );
}
