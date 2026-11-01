import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { AuthModal } from "./AuthModal";
import {
  ShieldCheck,
  UserCheck,
  LogOut,
  LogIn,
  Activity,
  FileSpreadsheet,
  PlayCircle,
  BarChart3,
  History,
  Users,
  Settings,
  Clock,
  AlertTriangle,
  X,
} from "lucide-react";

export type ActiveTab =
  "dashboard" | "imports" | "runs" | "results" | "audit" | "users" | "settings";

interface NavbarProps {
  activeTab: ActiveTab;
  onTabChange: (tab: ActiveTab) => void;
}

export function Navbar({ activeTab, onTabChange }: NavbarProps) {
  const {
    user,
    isAuthenticated,
    logout,
    isAdmin,
    isViewer,
    remainingSeconds,
    sessionExpired,
    dismissSessionExpired,
    authModalOpen,
    setAuthModalOpen,
    openLoginModal,
  } = useAuth();

  const formatRemainingMinutes = (seconds: number | null) => {
    if (seconds === null || seconds <= 0) return null;
    const mins = Math.ceil(seconds / 60);
    return `${mins}m`;
  };

  return (
    <>
      {sessionExpired && (
        <div className="bg-amber-500/15 border-b border-amber-500/30 px-4 py-2 text-xs text-amber-900 dark:text-amber-200 flex items-center justify-between">
          <div className="container mx-auto flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400" />
            <span className="font-medium">
              Your session has expired. To perform authenticated operations, please sign in again.
            </span>
            <Button
              size="sm"
              variant="outline"
              className="h-6 px-2 text-[11px] ml-2 border-amber-600/40 text-amber-900 dark:text-amber-100 hover:bg-amber-500/20"
              onClick={openLoginModal}
            >
              Sign In
            </Button>
          </div>
          <button
            onClick={dismissSessionExpired}
            className="text-amber-700 dark:text-amber-300 hover:text-foreground p-0.5"
            title="Dismiss"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      <header className="sticky top-0 z-40 w-full border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="container mx-auto flex h-16 items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-6">
            <div
              className="flex items-center gap-2.5 cursor-pointer group"
              onClick={() => onTabChange("dashboard")}
            >
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground font-bold shadow-sm ring-1 ring-border/20 transition-transform group-hover:scale-[1.02]">
                RX
              </div>
              <div className="flex flex-col">
                <div className="flex items-center gap-1.5">
                  <span className="text-base font-bold tracking-tight text-foreground">ReconcileX</span>
                </div>
                <span className="hidden sm:inline-block text-[11px] text-muted-foreground font-medium">
                  Financial Reconciliation
                </span>
              </div>
            </div>

            <nav className="hidden lg:flex items-center gap-1 text-sm font-medium">
              <button
                onClick={() => onTabChange("dashboard")}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                  activeTab === "dashboard"
                    ? "bg-secondary text-foreground shadow-xs font-semibold ring-1 ring-border/50"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
                }`}
              >
                <Activity className="h-3.5 w-3.5 text-muted-foreground" />
                Dashboard
              </button>

              {isAdmin && (
                <button
                  onClick={() => onTabChange("imports")}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                    activeTab === "imports"
                      ? "bg-secondary text-foreground shadow-xs font-semibold ring-1 ring-border/50"
                      : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
                  }`}
                >
                  <FileSpreadsheet className="h-3.5 w-3.5" />
                  File Imports
                </button>
              )}

              <button
                onClick={() => onTabChange("runs")}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                  activeTab === "runs"
                    ? "bg-secondary text-foreground shadow-xs font-semibold ring-1 ring-border/50"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
                }`}
              >
                <PlayCircle className="h-3.5 w-3.5" />
                Reconciliation Runs
              </button>

              <button
                onClick={() => onTabChange("results")}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                  activeTab === "results"
                    ? "bg-secondary text-foreground shadow-xs font-semibold ring-1 ring-border/50"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
                }`}
              >
                <BarChart3 className="h-3.5 w-3.5" />
                Results & Report
              </button>

              {isAdmin && (
                <>
                  <button
                    onClick={() => onTabChange("audit")}
                    className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                      activeTab === "audit"
                        ? "bg-secondary text-foreground shadow-xs font-semibold ring-1 ring-border/50"
                        : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
                    }`}
                  >
                    <History className="h-3.5 w-3.5" />
                    Audit Trail
                  </button>

                  <button
                    onClick={() => onTabChange("users")}
                    className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                      activeTab === "users"
                        ? "bg-secondary text-foreground shadow-xs font-semibold ring-1 ring-border/50"
                        : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
                    }`}
                  >
                    <Users className="h-3.5 w-3.5" />
                    Users
                  </button>

                  <button
                    onClick={() => onTabChange("settings")}
                    className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                      activeTab === "settings"
                        ? "bg-secondary text-foreground shadow-xs font-semibold ring-1 ring-border/50"
                        : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
                    }`}
                  >
                    <Settings className="h-3.5 w-3.5" />
                    Settings
                  </button>
                </>
              )}
            </nav>
          </div>

          <div className="flex items-center gap-2.5">
            {isAuthenticated && user ? (
              <div className="flex items-center gap-2">
                {remainingSeconds !== null && remainingSeconds > 0 && (
                  <div
                    className="hidden sm:inline-flex items-center gap-1.5 text-[11px] font-mono text-muted-foreground border border-border/80 px-2.5 py-1 rounded-md bg-secondary/30"
                    title={`JWT token active in-memory (${Math.floor(remainingSeconds / 60)} minutes remaining)`}
                  >
                    <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
                    <span>{formatRemainingMinutes(remainingSeconds)} left</span>
                  </div>
                )}

                <div className="flex items-center gap-2 px-2.5 py-1 rounded-md border border-border/70 bg-card text-xs font-medium shadow-xs">
                  <div className="flex h-5 w-5 items-center justify-center rounded-full bg-primary/10 text-primary font-bold text-[10px]">
                    {user.username.charAt(0).toUpperCase()}
                  </div>
                  <span className="font-semibold text-foreground">{user.username}</span>
                  <span
                    className={`ml-1 text-[10px] font-mono uppercase px-1.5 py-0.5 rounded border ${
                      isAdmin
                        ? "bg-primary/10 text-primary border-primary/20"
                        : "bg-secondary text-secondary-foreground border-border"
                    }`}
                  >
                    {user.role}
                  </span>
                </div>

                <Button
                  variant="ghost"
                  size="sm"
                  onClick={logout}
                  className="h-8 px-2 text-xs text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors"
                >
                  <LogOut className="h-3.5 w-3.5 mr-1" />
                  Logout
                </Button>
              </div>
            ) : (
              <Button size="sm" onClick={openLoginModal} className="h-8 gap-1.5 text-xs font-medium shadow-xs">
                <LogIn className="h-3.5 w-3.5" />
                Sign In
              </Button>
            )}
          </div>
        </div>

        {/* Mobile Nav */}
        <div className="flex lg:hidden overflow-x-auto border-t border-border px-4 py-2 gap-1 text-xs">
          <button
            onClick={() => onTabChange("dashboard")}
            className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium transition-colors ${
              activeTab === "dashboard"
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
            }`}
          >
            Dashboard
          </button>

          {isAdmin && (
            <button
              onClick={() => onTabChange("imports")}
              className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium transition-colors ${
                activeTab === "imports"
                  ? "bg-primary text-primary-foreground"
                  : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
              }`}
            >
              Imports
            </button>
          )}

          <button
            onClick={() => onTabChange("runs")}
            className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium transition-colors ${
              activeTab === "runs"
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
            }`}
          >
            Runs
          </button>

          <button
            onClick={() => onTabChange("results")}
            className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium transition-colors ${
              activeTab === "results"
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
            }`}
          >
            Results
          </button>

          {isAdmin && (
            <>
              <button
                onClick={() => onTabChange("audit")}
                className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium transition-colors ${
                  activeTab === "audit"
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
                }`}
              >
                Audit
              </button>

              <button
                onClick={() => onTabChange("users")}
                className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium transition-colors ${
                  activeTab === "users"
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
                }`}
              >
                Users
              </button>

              <button
                onClick={() => onTabChange("settings")}
                className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium transition-colors ${
                  activeTab === "settings"
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
                }`}
              >
                Settings
              </button>
            </>
          )}
        </div>
      </header>

      <AuthModal open={authModalOpen} onOpenChange={setAuthModalOpen} />
    </>
  );
}
