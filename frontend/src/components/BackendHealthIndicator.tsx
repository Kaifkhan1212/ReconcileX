import { useState, useEffect } from "react";
import { getHealthApi } from "@/lib/api";
import { RefreshCw } from "lucide-react";

interface BackendHealthIndicatorProps {
  className?: string;
  showRefresh?: boolean;
}

export function BackendHealthIndicator({
  className = "",
  showRefresh = false,
}: BackendHealthIndicatorProps) {
  const [status, setStatus] = useState<"UP" | "DOWN" | "CHECKING">("CHECKING");
  const [loading, setLoading] = useState(false);

  const checkHealth = async () => {
    setLoading(true);
    try {
      const res = await getHealthApi();
      setStatus(res.status === "UP" ? "UP" : "DOWN");
    } catch {
      setStatus("DOWN");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    checkHealth();
    // Re-check periodically every 30 seconds
    const interval = setInterval(checkHealth, 30000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div
      className={`inline-flex items-center gap-2 px-2.5 py-1 rounded-md border text-xs font-medium transition-colors ${
        status === "UP"
          ? "border-emerald-500/25 bg-emerald-500/10 text-emerald-400"
          : status === "CHECKING"
          ? "border-slate-800 bg-slate-900/60 text-slate-400"
          : "border-rose-500/25 bg-rose-500/10 text-rose-400"
      } ${className}`}
    >
      <span
        className={`h-2 w-2 rounded-full shrink-0 ${
          status === "UP"
            ? "bg-emerald-400 animate-pulse"
            : status === "CHECKING"
            ? "bg-slate-500"
            : "bg-rose-500"
        }`}
      />
      <span>
        {status === "UP"
          ? "Engine Reachable"
          : status === "CHECKING"
          ? "Connecting..."
          : "Engine Unreachable"}
      </span>

      {showRefresh && (
        <button
          type="button"
          onClick={checkHealth}
          disabled={loading}
          aria-label="Refresh connection health"
          className="ml-0.5 p-0.5 rounded text-current hover:opacity-80 transition-opacity"
        >
          <RefreshCw className={`h-3 w-3 ${loading ? "animate-spin" : ""}`} />
        </button>
      )}
    </div>
  );
}

