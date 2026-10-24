import { useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { AuthModal } from "./AuthModal";
import { ShieldCheck, UserCheck, LogOut, LogIn, Activity, FileSpreadsheet, PlayCircle, BarChart3, History } from "lucide-react";

export type ActiveTab = "dashboard" | "imports" | "runs" | "results" | "audit";

interface NavbarProps {
  activeTab: ActiveTab;
  onTabChange: (tab: ActiveTab) => void;
}

export function Navbar({ activeTab, onTabChange }: NavbarProps) {
  const { user, isAuthenticated, logout, isAdmin } = useAuth();
  const [authModalOpen, setAuthModalOpen] = useState(false);

  return (
    <>
      <header className="sticky top-0 z-40 w-full border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="container mx-auto flex h-16 items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-6">
            <div className="flex items-center gap-2 cursor-pointer" onClick={() => onTabChange("dashboard")}>
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground font-bold shadow-md">
                RX
              </div>
              <div>
                <span className="text-lg font-bold tracking-tight text-foreground">ReconcileX</span>
                <span className="hidden sm:inline-block ml-2 text-xs font-medium text-muted-foreground">
                  Engine API 1.0.0
                </span>
              </div>
            </div>

            <nav className="hidden md:flex items-center gap-1 text-sm font-medium">
              <button
                onClick={() => onTabChange("dashboard")}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md transition-colors ${
                  activeTab === "dashboard"
                    ? "bg-secondary text-primary font-semibold"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"
                }`}
              >
                <Activity className="h-4 w-4" />
                Dashboard
              </button>
              <button
                onClick={() => onTabChange("imports")}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md transition-colors ${
                  activeTab === "imports"
                    ? "bg-secondary text-primary font-semibold"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"
                }`}
              >
                <FileSpreadsheet className="h-4 w-4" />
                File Imports
              </button>
              <button
                onClick={() => onTabChange("runs")}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md transition-colors ${
                  activeTab === "runs"
                    ? "bg-secondary text-primary font-semibold"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"
                }`}
              >
                <PlayCircle className="h-4 w-4" />
                Reconciliation Runs
              </button>
              <button
                onClick={() => onTabChange("results")}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md transition-colors ${
                  activeTab === "results"
                    ? "bg-secondary text-primary font-semibold"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"
                }`}
              >
                <BarChart3 className="h-4 w-4" />
                Results & Report
              </button>
              <button
                onClick={() => onTabChange("audit")}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md transition-colors ${
                  activeTab === "audit"
                    ? "bg-secondary text-primary font-semibold"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"
                }`}
              >
                <History className="h-4 w-4" />
                Audit Trail
              </button>
            </nav>
          </div>

          <div className="flex items-center gap-3">
            {isAuthenticated && user ? (
              <div className="flex items-center gap-2">
                <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-secondary text-xs font-medium">
                  {isAdmin ? (
                    <ShieldCheck className="h-3.5 w-3.5 text-blue-600 dark:text-blue-400" />
                  ) : (
                    <UserCheck className="h-3.5 w-3.5 text-slate-600 dark:text-slate-400" />
                  )}
                  <span>{user.username}</span>
                  <Badge variant={isAdmin ? "default" : "secondary"} className="ml-1 text-[10px] uppercase">
                    {user.role}
                  </Badge>
                </div>
                <Button variant="ghost" size="sm" onClick={logout} className="h-8 px-2 text-xs text-muted-foreground">
                  <LogOut className="h-3.5 w-3.5 mr-1" />
                  Logout
                </Button>
              </div>
            ) : (
              <Button size="sm" onClick={() => setAuthModalOpen(true)} className="h-8 gap-1.5 text-xs">
                <LogIn className="h-3.5 w-3.5" />
                Sign In
              </Button>
            )}
          </div>
        </div>

        {/* Mobile Nav */}
        <div className="flex md:hidden overflow-x-auto border-t border-border px-4 py-2 gap-1 text-xs">
          <button
            onClick={() => onTabChange("dashboard")}
            className={`px-2.5 py-1 rounded whitespace-nowrap ${activeTab === "dashboard" ? "bg-primary text-primary-foreground font-medium" : "text-muted-foreground"}`}
          >
            Dashboard
          </button>
          <button
            onClick={() => onTabChange("imports")}
            className={`px-2.5 py-1 rounded whitespace-nowrap ${activeTab === "imports" ? "bg-primary text-primary-foreground font-medium" : "text-muted-foreground"}`}
          >
            Imports
          </button>
          <button
            onClick={() => onTabChange("runs")}
            className={`px-2.5 py-1 rounded whitespace-nowrap ${activeTab === "runs" ? "bg-primary text-primary-foreground font-medium" : "text-muted-foreground"}`}
          >
            Runs
          </button>
          <button
            onClick={() => onTabChange("results")}
            className={`px-2.5 py-1 rounded whitespace-nowrap ${activeTab === "results" ? "bg-primary text-primary-foreground font-medium" : "text-muted-foreground"}`}
          >
            Results
          </button>
          <button
            onClick={() => onTabChange("audit")}
            className={`px-2.5 py-1 rounded whitespace-nowrap ${activeTab === "audit" ? "bg-primary text-primary-foreground font-medium" : "text-muted-foreground"}`}
          >
            Audit
          </button>
        </div>
      </header>

      <AuthModal open={authModalOpen} onOpenChange={setAuthModalOpen} />
    </>
  );
}
