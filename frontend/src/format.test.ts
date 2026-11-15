import { describe, expect, it } from "vitest";
import { formatPrice, plural } from "./format";

// Intl separates the thousands and the currency with no-break spaces (U+00A0, U+202F)
const spaces = (text: string) => text.replace(/[  ]/g, " ");

describe("formatPrice", () => {
  it("shows euros with a decimal comma", () => {
    expect(spaces(formatPrice(1234.5))).toBe("1 234,50 €");
  });

  it("shows a dash when the price is not known", () => {
    expect(formatPrice(null)).toBe("—");
  });
});

describe("plural", () => {
  it("adds an s from 2", () => {
    expect(plural(1, "article")).toBe("1 article");
    expect(plural(3, "article")).toBe("3 articles");
  });
});
