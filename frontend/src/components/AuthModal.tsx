import { useState } from "react";
import { useAuth } from "@/lib/auth-context";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Shield, Eye, EyeOff, AlertCircle, Loader2 } from "lucide-react";

interface AuthModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function AuthModal({ open, onOpenChange }: AuthModalProps) {
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isHttps = typeof window !== "undefined" && window.location.protocol === "https:";

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedUsername = username.trim();

    if (!trimmedUsername) {
      setError("Please enter your account username.");
      return;
    }

    if (!password) {
      setError("Please enter your account password.");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      await login(trimmedUsername, password);
      onOpenChange(false);
      setPassword("");
      setUsername("");
      setShowPassword(false);
    } catch (err: unknown) {
      const rawMessage = err instanceof Error ? err.message : "";
      if (
        rawMessage.includes("401") ||
        rawMessage.toLowerCase().includes("unauthorized") ||
        rawMessage.toLowerCase().includes("bad credentials")
      ) {
        setError("Invalid username or password. Please verify your credentials and try again.");
      } else if (
        rawMessage.toLowerCase().includes("failed to fetch") ||
        rawMessage.toLowerCase().includes("network")
      ) {
        setError("Unable to connect to authentication service. Please check your network connection.");
      } else {
        setError(rawMessage || "Authentication failed. Please verify your credentials and try again.");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleClose = (isOpen: boolean) => {
    if (!isOpen) {
      setUsername("");
      setPassword("");
      setShowPassword(false);
      setError(null);
    }
    onOpenChange(isOpen);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="w-[calc(100vw-2rem)] sm:max-w-[400px] p-5 sm:p-6 bg-[#090d16] border-slate-800 text-slate-100 shadow-2xl rounded-xl">
        <DialogHeader className="space-y-2 pb-1">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 rounded-lg bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center shrink-0">
              <Shield className="h-4.5 w-4.5 text-indigo-400" />
            </div>
            <div>
              <DialogTitle className="text-base font-semibold text-white tracking-tight">
                Sign In to ReconcileX
              </DialogTitle>
              <span className="text-[11px] font-mono text-slate-400">
                Financial Reconciliation Engine
              </span>
            </div>
          </div>
          <DialogDescription className="text-xs text-slate-400 leading-relaxed pt-1">
            Sign in with your verified credentials. Your workspace and operational permissions are determined automatically upon authentication.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-3.5 pt-2">
          {error && (
            <div
              role="alert"
              className="flex items-start gap-2 p-3 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs leading-relaxed"
            >
              <AlertCircle className="h-4 w-4 shrink-0 text-rose-400 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="auth-username" className="text-xs font-medium text-slate-300">
              Username
            </Label>
            <Input
              id="auth-username"
              placeholder="Enter your username"
              value={username}
              onChange={(e) => {
                setUsername(e.target.value);
                if (error) setError(null);
              }}
              required
              autoFocus
              className="h-8.5 font-mono text-xs bg-slate-950/60 border-slate-800 text-white"
            />
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="auth-password" className="text-xs font-medium text-slate-300">
                Password
              </Label>
              {isHttps && (
                <span className="text-[10px] text-emerald-400 font-mono">
                  Encrypted (TLS/HTTPS)
                </span>
              )}
            </div>
            <div className="relative">
              <Input
                id="auth-password"
                type={showPassword ? "text" : "password"}
                placeholder="Enter your password"
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value);
                  if (error) setError(null);
                }}
                required
                className="h-8.5 font-mono text-xs bg-slate-950/60 border-slate-800 text-white pr-9"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 p-0.5"
                title={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
              </button>
            </div>
          </div>

          <div className="p-2.5 rounded-lg border border-slate-800/80 bg-slate-950/50 flex items-center justify-between text-[11px] text-slate-400">
            <span className="flex items-center gap-1.5">
              <Shield className="h-3 w-3 text-indigo-400" />
              Role Verification
            </span>
            <span className="font-mono text-[10px] text-slate-300">
              Automatic (ADMIN / VIEWER)
            </span>
          </div>

          <div className="pt-2 flex items-center justify-end gap-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => handleClose(false)}
              className="h-8 text-xs text-slate-400 hover:text-white"
            >
              Cancel
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={loading}
              className="h-8 px-4 text-xs font-semibold gap-1.5 bg-indigo-600 hover:bg-indigo-500 text-white shadow-xs"
            >
              {loading ? (
                <>
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  <span>Signing In...</span>
                </>
              ) : (
                <span>Sign In</span>
              )}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
