import { useAuth } from "@/lib/auth-context";
import { AuthModal } from "./AuthModal";
import { BackendHealthIndicator } from "./BackendHealthIndicator";
import type { ActiveTab } from "@/routes/index";
import {
  Activity,
  FileSpreadsheet,
  PlayCircle,
  BarChart3,
  History,
  Users,
  Settings,
  LogIn,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Clock,
  X,
} from "lucide-react";

interface SidebarProps {
  activeTab: ActiveTab;
  onTabChange: (tab: ActiveTab) => void;
  collapsed: boolean;
  onToggleCollapse: () => void;
  mobileOpen?: boolean;
  onCloseMobile?: () => void;
}

export function Sidebar({
  activeTab,
  onTabChange,
  collapsed,
  onToggleCollapse,
  mobileOpen = false,
  onCloseMobile,
}: SidebarProps) {
  const {
    user,
    isAuthenticated,
    logout,
    isAdmin,
    remainingSeconds,
    authModalOpen,
    setAuthModalOpen,
    openLoginModal,
  } = useAuth();

  const formatRemainingMinutes = (seconds: number | null) => {
    if (seconds === null || seconds <= 0) return null;
    const mins = Math.ceil(seconds / 60);
    return `${mins}m`;
  };

  const navItems: Array<{
    id: ActiveTab;
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    adminOnly?: boolean;
  }> = [
    { id: "dashboard", label: "Dashboard", icon: Activity },
    { id: "imports", label: "File Imports", icon: FileSpreadsheet, adminOnly: true },
    { id: "runs", label: "Reconciliation Runs", icon: PlayCircle },
    { id: "results", label: "Results & Reports", icon: BarChart3 },
    { id: "audit", label: "Audit Trail", icon: History, adminOnly: true },
    { id: "users", label: "User Management", icon: Users, adminOnly: true },
    { id: "settings", label: "Settings", icon: Settings, adminOnly: true },
  ];

  const visibleNav = navItems.filter((item) => !item.adminOnly || isAdmin);

  const handleItemClick = (id: ActiveTab) => {
    onTabChange(id);
    if (onCloseMobile) {
      onCloseMobile();
    }
  };

  return (
    <>
      {/* Mobile Backdrop Overlay */}
      {mobileOpen && (
        <div
          className="fixed inset-0 bg-black/60 backdrop-blur-xs z-30 md:hidden transition-opacity"
          onClick={onCloseMobile}
          aria-hidden="true"
        />
      )}

      {/* Responsive Sidebar Rail */}
      <aside
        className={`fixed top-0 bottom-0 left-0 z-40 flex flex-col bg-[#090d16] text-slate-200 border-r border-slate-800/70 transition-transform md:transition-all duration-200 select-none ${
          mobileOpen
            ? "translate-x-0 w-64 shadow-2xl"
            : "-translate-x-full md:translate-x-0"
        } ${collapsed ? "md:w-16" : "md:w-64"}`}
      >
        {/* Workspace Brand Header */}
        <div className="h-16 flex items-center justify-between px-3.5 border-b border-slate-800/70">
          <div
            className="flex items-center gap-3 cursor-pointer overflow-hidden group"
            onClick={() => handleItemClick("dashboard")}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") handleItemClick("dashboard");
            }}
          >
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-slate-900 border border-slate-700/80 text-white font-bold text-sm tracking-tight shadow-sm group-hover:border-slate-500 transition-colors">
              <span className="font-mono text-indigo-400">RX</span>
            </div>
            {(!collapsed || mobileOpen) && (
              <div className="flex flex-col min-w-0">
                <span className="font-bold tracking-tight text-slate-100 text-sm truncate">
                  ReconcileX
                </span>
                <span className="text-[10px] text-slate-400 font-mono tracking-wide truncate">
                  Reconciliation Engine
                </span>
              </div>
            )}
          </div>

          <div className="flex items-center">
            {/* Mobile Close Button */}
            <button
              type="button"
              onClick={onCloseMobile}
              aria-label="Close navigation"
              className="p-1.5 rounded-md text-slate-400 hover:text-white hover:bg-slate-800/60 md:hidden"
            >
              <X className="h-4 w-4" />
            </button>

            {/* Desktop Collapse Toggle */}
            <button
              type="button"
              onClick={onToggleCollapse}
              aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
              className="hidden md:block p-1.5 rounded-md text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors"
            >
              {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
            </button>
          </div>
        </div>

        {/* User Identity Bar */}
        <div className="p-3 border-b border-slate-800/60 bg-slate-900/30">
          {isAuthenticated && user ? (
            <div
              className={`flex items-center ${
                collapsed && !mobileOpen ? "justify-center" : "gap-2.5"
              }`}
            >
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-800 text-white font-semibold text-xs border border-slate-700">
                {user.username.charAt(0).toUpperCase()}
              </div>
              {(!collapsed || mobileOpen) && (
                <div className="flex flex-col min-w-0 flex-1">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-slate-200 truncate">
                      {user.username}
                    </span>
                    <span
                      className={`text-[9px] font-mono uppercase font-medium px-1.5 py-0.5 rounded border ${
                        isAdmin
                          ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/25"
                          : "bg-slate-800 text-slate-300 border-slate-700"
                      }`}
                    >
                      {user.role}
                    </span>
                  </div>
                  <div className="flex items-center gap-1.5 text-[10px] text-slate-400 mt-0.5 font-mono">
                    <Clock className="h-3 w-3 text-slate-500" />
                    <span>
                      {remainingSeconds
                        ? `${formatRemainingMinutes(remainingSeconds)} session`
                        : "Active"}
                    </span>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="flex flex-col items-center">
              {!collapsed || mobileOpen ? (
                <button
                  type="button"
                  onClick={() => {
                    openLoginModal();
                    if (onCloseMobile) onCloseMobile();
                  }}
                  className="w-full flex items-center justify-between p-2 rounded-lg border border-slate-800 bg-slate-900/60 hover:bg-slate-800/60 hover:border-slate-700 transition-all text-xs group"
                >
                  <div className="flex items-center gap-2">
                    <div className="h-6 w-6 rounded-md bg-slate-800 flex items-center justify-center text-slate-400 group-hover:text-white transition-colors">
                      <LogIn className="h-3.5 w-3.5" />
                    </div>
                    <span className="text-slate-300 font-medium group-hover:text-white transition-colors">
                      Guest Session
                    </span>
                  </div>
                  <span className="text-[11px] text-indigo-400 group-hover:text-indigo-300 font-medium">
                    Sign In &rarr;
                  </span>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => {
                    openLoginModal();
                    if (onCloseMobile) onCloseMobile();
                  }}
                  title="Sign In"
                  className="p-2 rounded-lg bg-slate-900 text-slate-400 hover:text-white border border-slate-800 hover:border-slate-700 transition-colors"
                >
                  <LogIn className="h-4 w-4" />
                </button>
              )}
            </div>
          )}
        </div>

        {/* Navigation Category Label */}
        {(!collapsed || mobileOpen) && (
          <div className="px-3.5 pt-3.5 pb-1 text-[10px] font-semibold tracking-wider text-slate-500 uppercase font-mono">
            Navigation
          </div>
        )}

        {/* Main Navigation Items */}
        <nav className="flex-1 px-2 py-1 space-y-1 overflow-y-auto">
          {visibleNav.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => handleItemClick(item.id)}
                title={collapsed && !mobileOpen ? item.label : undefined}
                className={`w-full flex items-center rounded-lg transition-all text-xs font-medium group ${
                  collapsed && !mobileOpen ? "justify-center p-2.5" : "px-3 py-2 gap-2.5"
                } ${
                  isActive
                    ? "bg-slate-800 text-white border border-slate-700/80 shadow-xs"
                    : "text-slate-400 hover:text-slate-100 hover:bg-slate-850/60"
                }`}
              >
                <Icon
                  className={`h-4 w-4 shrink-0 transition-colors ${
                    isActive ? "text-indigo-400" : "text-slate-400 group-hover:text-slate-200"
                  }`}
                />
                {(!collapsed || mobileOpen) && (
                  <span className="truncate flex-1 text-left">{item.label}</span>
                )}
              </button>
            );
          })}
        </nav>

        {/* Bottom Status & Sign Out */}
        <div className="p-3 border-t border-slate-800/70 space-y-2.5 bg-slate-900/20">
          {(!collapsed || mobileOpen) && (
            <div className="flex items-center justify-between">
              <BackendHealthIndicator className="w-full justify-between" />
            </div>
          )}

          {isAuthenticated && (
            <button
              type="button"
              onClick={() => {
                logout();
                if (onCloseMobile) onCloseMobile();
              }}
              title={collapsed && !mobileOpen ? "Sign Out" : undefined}
              className={`w-full flex items-center rounded-lg text-xs font-medium text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors ${
                collapsed && !mobileOpen ? "justify-center p-2.5" : "px-3 py-1.5 gap-2"
              }`}
            >
              <LogOut className="h-3.5 w-3.5 shrink-0" />
              {(!collapsed || mobileOpen) && <span>Sign Out</span>}
            </button>
          )}
        </div>
      </aside>

      <AuthModal open={authModalOpen} onOpenChange={setAuthModalOpen} />
    </>
  );
}
