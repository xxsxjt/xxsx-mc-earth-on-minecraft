#!/usr/bin/env python3
"""Emit SpecLodeReserves.java: per-lode ore capacity derived from worldgen.jsonl.

The spec gives each lode `lodes_per_region` and `reserve_chunks`. One placed lode block stands
for one ore body, so its capacity is the region reserve divided across the region's lodes. That
keeps a rich vein meaningfully bigger than a lean one without inventing numbers.
"""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")
OUT = ROOT / "neoforge-26.2/src/main/java/com/xxsx/earthonminecraft/SpecLodeReserves.java"

# A chunk of ore body is worth roughly this many ore units before division by lode count.
UNITS_PER_RESERVE_CHUNK = 12
MIN_CAPACITY = 16
MAX_CAPACITY = 512


def capacity_for(row: dict) -> int:
    lodes = int(row.get("lodes_per_region") or 1)
    reserve = float(row.get("reserve_chunks") or 1.0)
    raw = reserve * UNITS_PER_RESERVE_CHUNK / max(1, lodes)
    return max(MIN_CAPACITY, min(MAX_CAPACITY, int(round(raw))))


def main() -> None:
    rows = [
        json.loads(line)
        for line in (SPEC / "worldgen.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]

    entries = []
    for row in rows:
        lode = row["id"].split(":", 1)[1].split("/", 1)[1]
        entries.append((lode, capacity_for(row), int(row.get("lodes_per_region") or 1),
                        float(row.get("reserve_chunks") or 1.0)))
    entries.sort()

    lines = [
        "package com.xxsx.earthonminecraft;",
        "",
        "import java.util.LinkedHashMap;",
        "import java.util.Map;",
        "",
        "/**",
        " * Per-lode ore capacity, generated from {@code spec/eom/worldgen.jsonl}.",
        " *",
        " * <p>A lode block represents one ore body. Its capacity is the region reserve",
        " * ({@code reserve_chunks}) divided across the region's lodes ({@code lodes_per_region}),",
        " * so a rich body outlasts a lean one by the ratio the spec already states. Do not edit by",
        " * hand: regenerate with {@code tools/generate_lode_reserves.py}.</p>",
        " */",
        "public final class SpecLodeReserves {",
        "    private static final Map<String, Integer> CAPACITY = new LinkedHashMap<>();",
        "",
        "    static {",
    ]
    for lode, capacity, lodes, reserve in entries:
        lines.append(
            f'        CAPACITY.put("{lode}", {capacity});'
            f'  // lodes/region={lodes}, reserve_chunks={reserve:g}'
        )
    lines.extend([
        "    }",
        "",
        "    private SpecLodeReserves() {",
        "    }",
        "",
        "    /** Ore units one lode block holds, or {@code null} when the lode is unknown. */",
        "    public static Integer capacity(String lodeId) {",
        "        return CAPACITY.get(lodeId);",
        "    }",
        "",
        "    public static Map<String, Integer> all() {",
        "        return Map.copyOf(CAPACITY);",
        "    }",
        "}",
        "",
    ])
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"SPEC_LODE_RESERVES_OK lodes={len(entries)}")
    for lode, capacity, lodes, reserve in entries[:6]:
        print(f"  {lode:34} {capacity:4} units  ({reserve:g} chunks / {lodes} lodes)")


if __name__ == "__main__":
    main()
