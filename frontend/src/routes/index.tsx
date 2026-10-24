import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Navbar, type ActiveTab } from "@/components/Navbar";
import { DashboardView } from "@/components/DashboardView";
import { ImportsView } from "@/components/ImportsView";
import { RunsView } from "@/components/RunsView";
import { ResultsView } from "@/components/ResultsView";
import { AuditView } from "@/components/AuditView";

export const Route = createFileRoute("/")({
  component: Index,
});

function Index() {
  const [activeTab, setActiveTab] = useState<ActiveTab>("dashboard");
  const [selectedRunId, setSelectedRunId] = useState<number>(1);
  const [bankImportId, setBankImportId] = useState<number | undefined>(undefined);
  const [internalImportId, setInternalImportId] = useState<number | undefined>(undefined);

  const handleNavigate = (tab: "imports" | "runs" | "results" | "audit", runId?: number) => {
    if (runId !== undefined) {
      setSelectedRunId(runId);
    }
    setActiveTab(tab);
  };

  const handleProceedToRun = (bId: number, iId: number) => {
    setBankImportId(bId);
    setInternalImportId(iId);
    setActiveTab("runs");
  };

  const handleViewRunResults = (runId: number) => {
    setSelectedRunId(runId);
    setActiveTab("results");
  };

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col">
      <Navbar activeTab={activeTab} onTabChange={setActiveTab} />

      <main className="container mx-auto px-4 sm:px-6 py-6 flex-1 max-w-7xl">
        {activeTab === "dashboard" && <DashboardView onNavigate={handleNavigate} />}
        {activeTab === "imports" && <ImportsView onProceedToRun={handleProceedToRun} />}
        {activeTab === "runs" && (
          <RunsView
            initialBankImportId={bankImportId}
            initialInternalImportId={internalImportId}
            onViewRunResults={handleViewRunResults}
          />
        )}
        {activeTab === "results" && <ResultsView initialRunId={selectedRunId} />}
        {activeTab === "audit" && <AuditView />}
      </main>

      <footer className="border-t border-border py-4 text-center text-xs text-muted-foreground">
        <div className="container mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-2">
          <span>ReconcileX Payment Reconciliation Engine API • Spring Boot 4 + React TanStack</span>
          <span className="font-mono text-[11px]">Strict In-Memory JWT • Port 8081</span>
        </div>
      </footer>
    </div>
  );
}
