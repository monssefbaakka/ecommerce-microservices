import { describe, expect, it } from "vitest";
import { toQueryString } from "./endpoints";

describe("toQueryString", () => {
  it("encodes every value", () => {
    expect(toQueryString({ name: "café crème", page: 2, sort: "price,desc" })).toBe(
      "?name=caf%C3%A9+cr%C3%A8me&page=2&sort=price%2Cdesc",
    );
  });

  it("leaves out undefined and empty values", () => {
    expect(toQueryString({ name: "", categoryId: undefined, page: 0 })).toBe("?page=0");
  });

  it("is empty without any value", () => {
    expect(toQueryString({})).toBe("");
  });
});
