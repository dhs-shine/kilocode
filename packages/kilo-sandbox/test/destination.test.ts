import { describe, expect, test } from "bun:test"
import { isPublicAddress, normalizeDestinations, parseDestination } from "../src/destination"

describe("sandbox network destinations", () => {
  test("normalizes exact DNS hosts and ports", () => {
    expect(parseDestination("GitHub.COM.")).toEqual({
      host: "github.com",
      port: 443,
      authority: "github.com:443",
    })
    expect(parseDestination("api.github.com:8443").authority).toBe("api.github.com:8443")
    expect(normalizeDestinations(["github.com", "GITHUB.com:443", "api.github.com"])).toEqual([
      "api.github.com:443",
      "github.com:443",
    ])
  })

  test("rejects ambiguous and widening inputs", () => {
    for (const value of [
      "https://github.com",
      "*.github.com",
      ".github.com",
      "github.com/path",
      "github.com?x=1",
      "user@github.com",
      " github.com",
      "github.com ",
      "github.com:0",
      "github.com:65536",
      "127.0.0.1",
      "127.1",
      "[::1]",
      "github.com\0.evil.test",
    ]) {
      expect(() => parseDestination(value), value).toThrow("Invalid sandbox network destination")
    }
  })

  test("accepts only globally routable resolved addresses", () => {
    for (const value of ["8.8.8.8", "1.1.1.1", "2606:4700:4700::1111", "2001:4860:4860::8888"]) {
      expect(isPublicAddress(value), value).toBe(true)
    }
    for (const value of [
      "127.0.0.1",
      "10.0.0.1",
      "169.254.169.254",
      "192.168.0.1",
      "100.64.0.1",
      "224.0.0.1",
      "::1",
      "fe80::1",
      "fc00::1",
      "::ffff:169.254.169.254",
      "64:ff9b::a9fe:a9fe",
    ]) {
      expect(isPublicAddress(value), value).toBe(false)
    }
  })
})
