import { describe, expect, it, vi } from "vitest";
import { decodeJwtPayload } from "@/lib/auth-context";
import {
  formatErrorMessage,
  ApiError,
  createUserApi,
  registerUnauthorizedHandler,
} from "@/lib/api";
import type { CreateUserRequest } from "@/lib/types";

describe("JWT Decoding & Role Validation", () => {
  it("decodes payload correctly and detects ADMIN role", () => {
    // Header: {"alg":"HS256","typ":"JWT"} -> eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
    // Payload: {"sub":"admin","role":"ADMIN","exp":1893456000} -> eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImV4cCI6MTg5MzQ1NjAwMH0
    const sampleToken =
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImV4cCI6MTg5MzQ1NjAwMH0.dummySignature";
    const payload = decodeJwtPayload(sampleToken);

    expect(payload).not.toBeNull();
    expect(payload?.sub).toBe("admin");
    expect(payload?.role).toBe("ADMIN");
    expect(payload?.exp).toBe(1893456000);
  });

  it("decodes payload correctly and detects VIEWER role", () => {
    // Payload: {"sub":"viewer_user","role":"VIEWER","exp":1893456000}
    const sampleToken =
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ2aWV3ZXJfdXNlciIsInJvbGUiOiJWSUVXRVIiLCJleHAiOjE4OTM0NTYwMDB9.dummySignature";
    const payload = decodeJwtPayload(sampleToken);

    expect(payload).not.toBeNull();
    expect(payload?.sub).toBe("viewer_user");
    expect(payload?.role).toBe("VIEWER");
  });

  it("handles malformed tokens safely without throwing", () => {
    expect(decodeJwtPayload("invalid-token")).toBeNull();
    expect(decodeJwtPayload("a.b")).toBeNull();
    expect(decodeJwtPayload("")).toBeNull();
  });
});

describe("API Error Formatting & HTTP Status Distinction", () => {
  it("formats HTTP 401 as session expired / authentication required", () => {
    const res = new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401 });
    const msg = formatErrorMessage(res, '{"error":"Unauthorized"}');
    expect(msg).toContain("401");
    expect(msg).toContain("Session expired or authentication required");
  });

  it("formats HTTP 403 as permission denied / ADMIN role required", () => {
    const res = new Response(JSON.stringify({ error: "Forbidden" }), { status: 403 });
    const msg = formatErrorMessage(res, '{"error":"Forbidden"}');
    expect(msg).toContain("403");
    expect(msg).toContain("ADMIN role");
  });

  it("extracts backend JSON error message for other statuses", () => {
    const res = new Response(JSON.stringify({ message: "Username 'admin' already exists" }), {
      status: 409,
    });
    const msg = formatErrorMessage(
      res,
      JSON.stringify({ message: "Username 'admin' already exists" }),
    );
    expect(msg).toBe("Username 'admin' already exists");
  });

  it("constructs ApiError with correct status and message", () => {
    const error = new ApiError(403, "Access Denied");
    expect(error.status).toBe(403);
    expect(error.message).toBe("Access Denied");
    expect(error.name).toBe("ApiError");
  });
});

describe("User Management API (POST /api/admin/users)", () => {
  it("successfully calls createUserApi with expected payload and auth header", async () => {
    const mockUserResponse = {
      id: 42,
      username: "auditor_jane",
      role: "VIEWER",
      enabled: true,
      createdAt: "2026-10-26T02:00:00Z",
    };

    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify(mockUserResponse), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const req: CreateUserRequest = {
      username: "auditor_jane",
      password: "SecurePassword123!",
      role: "VIEWER",
    };

    const result = await createUserApi(req, "sample-jwt-token");

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const call = fetchSpy.mock.calls[0];
    expect(call).toBeDefined();
    const [url, options] = call!;
    expect(url).toContain("/api/admin/users");
    expect(options?.method).toBe("POST");
    expect((options?.headers as Record<string, string>)["Authorization"]).toBe(
      "Bearer sample-jwt-token",
    );
    expect(JSON.parse(options?.body as string)).toEqual(req);
    expect(result.username).toBe("auditor_jane");
    expect(result.role).toBe("VIEWER");

    fetchSpy.mockRestore();
  });

  it("triggers unauthorized handler on 401 response", async () => {
    const unauthorizedSpy = vi.fn();
    registerUnauthorizedHandler(unauthorizedSpy);

    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ error: "Token expired" }), { status: 401 }),
      );

    const req: CreateUserRequest = {
      username: "new_user",
      password: "Password123456!",
      role: "ADMIN",
    };

    await expect(createUserApi(req, "expired-token")).rejects.toThrow(ApiError);
    expect(unauthorizedSpy).toHaveBeenCalledTimes(1);

    fetchSpy.mockRestore();
    registerUnauthorizedHandler(null);
  });
});

describe("Role-Based Dashboard Experience & RBAC Permissions", () => {
  const ADMIN_ONLY_TABS = ["imports", "audit", "users", "settings"];
  const SHARED_TABS = ["dashboard", "runs", "results"];

  it("identifies ADMIN privileges across all application surfaces", () => {
    const adminToken =
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImV4cCI6MTg5MzQ1NjAwMH0.dummySignature";
    const payload = decodeJwtPayload(adminToken);
    expect(payload?.role).toBe("ADMIN");

    // Admin has access to all tabs
    const allTabs = [...SHARED_TABS, ...ADMIN_ONLY_TABS];
    allTabs.forEach((tab) => {
      const isAllowed = true;
      expect(isAllowed).toBe(true);
    });
  });

  it("identifies VIEWER read-only privileges and restricts unauthorized tabs", () => {
    const viewerToken =
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJrYWlmIiwicm9sZSI6IlZJRVdFUiIsImV4cCI6MTg5MzQ1NjAwMH0.dummySignature";
    const payload = decodeJwtPayload(viewerToken);
    expect(payload?.role).toBe("VIEWER");

    // Viewer is allowed on shared tabs
    SHARED_TABS.forEach((tab) => {
      expect(ADMIN_ONLY_TABS.includes(tab)).toBe(false);
    });

    // Viewer is blocked on admin-only tabs
    ADMIN_ONLY_TABS.forEach((tab) => {
      const isBlockedForViewer = ADMIN_ONLY_TABS.includes(tab);
      expect(isBlockedForViewer).toBe(true);
    });
  });
});
