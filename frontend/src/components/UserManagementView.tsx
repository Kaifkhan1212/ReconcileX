import { useState, useEffect } from "react";
import { useAuth } from "@/lib/auth-context";
import { createUserApi, getUsersApi } from "@/lib/api";
import type { CreateUserRequest, UserResponse, AppUserRole } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Users,
  Shield,
  Eye,
  EyeOff,
  CheckCircle2,
  AlertCircle,
  Copy,
  Check,
  UserPlus,
} from "lucide-react";

export function UserManagementView() {
  const { token, isAdmin, user: currentUser } = useAuth();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<AppUserRole>("VIEWER");
  const [showPassword, setShowPassword] = useState(false);

  const [loading, setLoading] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // STRICT REQUIREMENT: No localStorage, no sessionStorage, no fake DEFAULT_USERS.
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);
  const [usersError, setUsersError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<number | null>(null);

  const fetchUsers = async () => {
    if (!token || !isAdmin) return;
    setUsersLoading(true);
    setUsersError(null);
    try {
      const fetched = await getUsersApi(token);
      setUsers(fetched || []);
    } catch (err: unknown) {
      setUsersError(err instanceof Error ? err.message : "Failed to load application users.");
      setUsers([]);
    } finally {
      setUsersLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, [token, isAdmin]);

  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);

    const cleanUsername = username.trim();
    if (cleanUsername.length < 3 || cleanUsername.length > 100) {
      setErrorMessage("Username must be between 3 and 100 characters.");
      return;
    }

    if (password.length < 12) {
      setErrorMessage("Password must be at least 12 characters long (backend security requirement).");
      return;
    }

    setLoading(true);
    try {
      const payload: CreateUserRequest = {
        username: cleanUsername,
        password,
        role,
      };

      const response = await createUserApi(payload, token);
      setSuccessMessage(`User '${response.username}' created with role ${response.role}.`);
      setUsername("");
      setPassword("");
      // Add newly created user to in-memory state
      setUsers((prev) => [...prev, response]);
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : "Failed to create user.");
    } finally {
      setLoading(false);
    }
  };

  const copyUsername = (id: number, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 p-5 rounded-xl border border-slate-800 bg-slate-900/50 shadow-xs">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5 flex-wrap">
            <h1 className="text-lg font-semibold tracking-tight text-white">
              User Management &amp; Access Control
            </h1>
            <span className="inline-flex items-center rounded-md border border-slate-700 bg-slate-800 text-slate-300 px-2 py-0.5 text-xs font-mono font-medium uppercase">
              ADMIN ONLY
            </span>
          </div>
          <p className="text-xs text-slate-400 max-w-2xl leading-relaxed">
            Provision team accounts with Administrator or Viewer roles and review role-based access permissions.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Column: Create User Form */}
        <div className="lg:col-span-5 rounded-xl border border-slate-800 bg-slate-900/40 p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <div className="flex items-center gap-2">
              <div className="h-7 w-7 rounded-lg bg-slate-800 flex items-center justify-center text-slate-300">
                <UserPlus className="h-4 w-4" />
              </div>
              <h2 className="text-sm font-semibold text-white">Create Application User</h2>
            </div>
            <span className="text-[10px] font-mono text-slate-400">ADMIN Action</span>
          </div>

          <form onSubmit={handleCreateUser} className="space-y-3.5">
            <div className="space-y-1.5">
              <Label htmlFor="create-username" className="text-xs font-medium text-slate-300">
                Username
              </Label>
              <Input
                id="create-username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="e.g. auditor_john"
                disabled={loading}
                className="h-8 font-mono text-xs bg-slate-950/60 border-slate-800 text-white"
                required
              />
              <p className="text-[10px] text-slate-500">3 to 100 characters, unique in system</p>
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <Label htmlFor="create-password" className="text-xs font-medium text-slate-300">
                  Password
                </Label>
                <span className="text-[10px] text-slate-500 font-mono">Min. 12 characters</span>
              </div>
              <div className="relative">
                <Input
                  id="create-password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Minimum 12 characters"
                  disabled={loading}
                  className="h-8 font-mono text-xs bg-slate-950/60 border-slate-800 text-white pr-9"
                  required
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

            <div className="space-y-1.5">
              <Label className="text-xs font-medium text-slate-300">User Role</Label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setRole("VIEWER")}
                  className={`p-2.5 rounded-lg border text-left transition-all ${
                    role === "VIEWER"
                      ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-300"
                      : "border-slate-800 bg-slate-950/40 text-slate-400 hover:bg-slate-850"
                  }`}
                >
                  <div className="text-xs font-semibold font-mono">VIEWER</div>
                  <div className="text-[10px] mt-0.5 text-slate-400">Read-only analytics</div>
                </button>

                <button
                  type="button"
                  onClick={() => setRole("ADMIN")}
                  className={`p-2.5 rounded-lg border text-left transition-all ${
                    role === "ADMIN"
                      ? "border-indigo-500/40 bg-indigo-500/10 text-indigo-300"
                      : "border-slate-800 bg-slate-950/40 text-slate-400 hover:bg-slate-850"
                  }`}
                >
                  <div className="text-xs font-semibold font-mono">ADMIN</div>
                  <div className="text-[10px] mt-0.5 text-slate-400">Full operations authority</div>
                </button>
              </div>
            </div>

            {errorMessage && (
              <div className="flex items-start gap-2 p-3 text-xs rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/20">
                <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                <span>{errorMessage}</span>
              </div>
            )}

            {successMessage && (
              <div className="flex items-center gap-2 p-3 text-xs rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                <CheckCircle2 className="h-4 w-4 shrink-0" />
                <span>{successMessage}</span>
              </div>
            )}

            <Button
              type="submit"
              disabled={loading}
              className="w-full h-8 text-xs font-semibold bg-indigo-600 hover:bg-indigo-500 text-white shadow-xs"
            >
              <span>{loading ? "Provisioning..." : "Provision User"}</span>
            </Button>
          </form>
        </div>

        {/* Right Column: Known Users & RBAC Matrix */}
        <div className="lg:col-span-7 space-y-6">
          {/* Active Application Users */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 shadow-xs overflow-hidden">
            <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/40">
              <div className="flex items-center gap-2">
                <Users className="h-4 w-4 text-indigo-400" />
                <h2 className="text-sm font-semibold text-white">Application Users ({users.length})</h2>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={fetchUsers}
                disabled={usersLoading}
                className="h-7 px-2 text-xs text-slate-400 hover:text-white"
              >
                Refresh
              </Button>
            </div>

            <div>
              {usersLoading ? (
                <div className="p-8 text-center text-xs text-slate-400 font-mono">
                  Loading application users from backend...
                </div>
              ) : usersError ? (
                <div className="p-4 m-4 rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/20 text-xs">
                  {usersError}
                </div>
              ) : users.length === 0 ? (
                <div className="p-8 text-center text-xs text-slate-400">
                  No users found in database.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead>
                      <tr className="border-b border-slate-800 bg-slate-950/60 text-slate-400 font-mono text-[11px]">
                        <th className="py-2.5 px-3 font-semibold">USER ID</th>
                        <th className="py-2.5 px-3 font-semibold">USERNAME</th>
                        <th className="py-2.5 px-3 font-semibold">ROLE</th>
                        <th className="py-2.5 px-3 font-semibold">STATUS</th>
                        <th className="py-2.5 px-3 font-semibold text-right">ACTION</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 font-mono text-xs">
                      {users.map((u) => {
                        const isCurrent = currentUser?.username === u.username;
                        return (
                          <tr key={u.id} className="hover:bg-slate-850/40 transition-colors">
                            <td className="py-2.5 px-3 font-semibold text-white">
                              #{u.id}
                            </td>
                            <td className="py-2.5 px-3">
                              <span className="font-semibold text-white mr-1.5">{u.username}</span>
                              {isCurrent && (
                                <span className="text-[9px] px-1 py-0.2 rounded bg-indigo-500/15 text-indigo-400 border border-indigo-500/30">
                                  current
                                </span>
                              )}
                            </td>
                            <td className="py-2.5 px-3">
                              <span
                                className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold border ${
                                  u.role === "ADMIN"
                                    ? "bg-indigo-500/10 text-indigo-400 border-indigo-500/20"
                                    : "bg-emerald-500/10 text-emerald-400 border-emerald-500/20"
                                }`}
                              >
                                {u.role}
                              </span>
                            </td>
                            <td className="py-2.5 px-3">
                              <span className="inline-flex items-center gap-1 text-[11px] text-emerald-400">
                                <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
                                Active
                              </span>
                            </td>
                            <td className="py-2.5 px-3 text-right">
                              <button
                                type="button"
                                onClick={() => copyUsername(u.id, u.username)}
                                className="p-1 rounded text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                                title="Copy username"
                              >
                                {copiedId === u.id ? (
                                  <Check className="h-3.5 w-3.5 text-emerald-400" />
                                ) : (
                                  <Copy className="h-3.5 w-3.5" />
                                )}
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>

          {/* Role-Based Access Control (RBAC) Matrix */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 shadow-xs space-y-3">
            <div className="flex items-center justify-between pb-2 border-b border-slate-800">
              <h3 className="text-xs font-semibold text-white">
                Role-Based Access Control (RBAC) Matrix
              </h3>
              <div className="flex items-center gap-1.5 font-mono text-[10px]">
                <span className="px-1.5 py-0.5 rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                  ADMIN
                </span>
                <span className="px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  VIEWER
                </span>
              </div>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-800 bg-slate-950/40 text-slate-400 font-mono text-[11px]">
                    <th className="py-2 px-2.5 font-medium">Capability / Endpoint</th>
                    <th className="py-2 px-2.5 font-medium text-center">ADMIN</th>
                    <th className="py-2 px-2.5 font-medium text-center">VIEWER</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-xs">
                  {[
                    { cap: "Financial Dashboard & KPIs", admin: true, viewer: true },
                    { cap: "Reconciliation Runs History", admin: true, viewer: true },
                    { cap: "Transaction Results & Discrepancies", admin: true, viewer: true },
                    { cap: "Download Streamed CSV Reports", admin: true, viewer: true },
                    { cap: "Ingest Bank & Internal CSV Files", admin: true, viewer: false },
                    { cap: "Execute Multi-Pass Reconciliation Run", admin: true, viewer: false },
                    { cap: "Access Append-Only Audit Trail", admin: true, viewer: false },
                    { cap: "Create & Provision Users", admin: true, viewer: false },
                  ].map((row) => (
                    <tr key={row.cap} className="hover:bg-slate-850/30">
                      <td className="py-2 px-2.5 text-slate-300">{row.cap}</td>
                      <td className="py-2 px-2.5 text-center font-mono">
                        {row.admin ? (
                          <span className="text-emerald-400 font-semibold">✓</span>
                        ) : (
                          <span className="text-slate-600">✕</span>
                        )}
                      </td>
                      <td className="py-2 px-2.5 text-center font-mono">
                        {row.viewer ? (
                          <span className="text-emerald-400 font-semibold">✓</span>
                        ) : (
                          <span className="text-rose-400/70 font-semibold">✕</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
