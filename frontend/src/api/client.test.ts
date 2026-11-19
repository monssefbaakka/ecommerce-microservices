import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, configureApi, request } from "./client";

function answer(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("request", () => {
  const fetchMock = vi.fn<typeof fetch>();
  const onUnauthorized = vi.fn();
  let token: string | null = null;

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    configureApi({ getToken: () => token, onUnauthorized });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    onUnauthorized.mockReset();
    token = null;
  });

  it("sends the body as JSON and the token as a Bearer header", async () => {
    token = "abc";
    fetchMock.mockResolvedValue(answer(200, { id: 1 }));

    const result = await request<{ id: number }>("POST", "/api/cart/items", { productId: 1, quantity: 2 });

    expect(result).toEqual({ id: 1 });
    const [path, init] = fetchMock.mock.calls[0]!;
    expect(path).toBe("/api/cart/items");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(JSON.stringify({ productId: 1, quantity: 2 }));
    expect(init?.headers).toMatchObject({ Authorization: "Bearer abc", "Content-Type": "application/json" });
  });

  it("sends no Authorization header when nobody is logged in", async () => {
    fetchMock.mockResolvedValue(answer(200, []));

    await request("GET", "/api/categories");

    expect(fetchMock.mock.calls[0]![1]?.headers).not.toHaveProperty("Authorization");
  });

  it("returns undefined for a 204", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(request("DELETE", "/api/cart")).resolves.toBeUndefined();
  });

  it("turns an ErrorResponse into an ApiError with the message of each field", async () => {
    fetchMock.mockResolvedValue(
      answer(400, {
        status: 400,
        error: "Bad Request",
        message: "Validation failed",
        path: "/api/auth/register",
        errors: [{ field: "password", message: "Le mot de passe doit contenir entre 8 et 72 caractères" }],
      }),
    );

    const error = await request("POST", "/api/auth/register", {}).catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      message: "Validation failed",
      fieldErrors: { password: "Le mot de passe doit contenir entre 8 et 72 caractères" },
    });
  });

  it("gives a message of its own when the answer is not an ErrorResponse", async () => {
    fetchMock.mockResolvedValue(new Response("<html>Bad Gateway</html>", { status: 502 }));

    await expect(request("GET", "/api/products")).rejects.toMatchObject({
      status: 502,
      message: "Le service est indisponible pour le moment. Réessaie dans quelques instants.",
    });
  });

  it("reports a 401 to a request sent with a token", async () => {
    token = "expired";
    fetchMock.mockResolvedValue(answer(401));

    await expect(request("GET", "/api/cart")).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it("does not report a 401 without a token: a wrong password is not a session that ended", async () => {
    fetchMock.mockResolvedValue(answer(401, { message: "Email ou mot de passe incorrect" }));

    await expect(request("POST", "/api/auth/login", {})).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it("answers status 0 when the server cannot be reached", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(request("GET", "/api/products")).rejects.toMatchObject({ status: 0 });
  });
});
