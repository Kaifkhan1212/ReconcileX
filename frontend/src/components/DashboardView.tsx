import { useAuth } from "@/lib/auth-context";
import type { ActiveTab } from "@/components/Navbar";
import { AdminDashboardView } from "./AdminDashboardView";
import { ViewerDashboardView } from "./ViewerDashboardView";
import { GuestDashboardView } from "./GuestDashboardView";

interface DashboardViewProps {
  onNavigate: (tab: ActiveTab, runId?: number) => void;
}

export function DashboardView({ onNavigate }: DashboardViewProps) {
  const { isAuthenticated, isAdmin, isViewer } = useAuth();

  // Role-based experience routing
  if (isAuthenticated && isAdmin) {
    return <AdminDashboardView onNavigate={onNavigate} />;
  }

  if (isAuthenticated && isViewer) {
    return <ViewerDashboardView onNavigate={onNavigate} />;
  }

  return <GuestDashboardView onNavigate={onNavigate} />;
}
