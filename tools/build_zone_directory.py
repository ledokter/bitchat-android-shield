#!/usr/bin/env python3
"""Builds app/src/main/assets/zones.tsv, the offline zone directory (continent > country > city > district).

Source: GeoNames (https://www.geonames.org, CC BY 4.0): cities1000.txt and countryInfo.txt from
https://download.geonames.org/export/dump/
Usage: build_zone_directory.py cities1000.txt countryInfo.txt app/src/main/assets/zones.tsv

Output lines (tab separated):
  C  <iso2> <continent>                       one per country
  P  <name> <iso2> <geohash7> <population> <parent>   parent = index of the city a district belongs to, or -1
  K  <iso2>                                   starts the tiles of a country
  T  <tile> <names>                           a geohash tile (1 to 4 characters) holding inhabited places of
                                              that country, with its largest places ("|" separated)
"""
import math, re, sys
from collections import defaultdict

CITY_MIN_POP = 15000        # a place listed as a city
COUNTRY_MIN_CITIES = 10     # small countries still get their largest places
PARENT_MIN_POP = 100000     # cities large enough to list their districts
DISTRICT_RADIUS_KM = 20

B32 = "0123456789bcdefghjkmnpqrstuvwxyz"
def geohash(lat, lon, n=7):
    la, lo, out, bits, ch, even = [-90.0, 90.0], [-180.0, 180.0], [], 0, 0, True
    while len(out) < n:
        r, v = (lo, lon) if even else (la, lat)
        mid = (r[0] + r[1]) / 2
        if v >= mid: ch = ch * 2 + 1; r[0] = mid
        else: ch = ch * 2; r[1] = mid
        even = not even; bits += 1
        if bits == 5: out.append(B32[ch]); bits = ch = 0
    return "".join(out)

def km(a, b):
    p = math.pi / 180
    x = (b[1] - a[1]) * p * math.cos((a[0] + b[0]) / 2 * p); y = (b[0] - a[0]) * p
    return 6371 * math.hypot(x, y)

cities_txt, country_txt, out_path = sys.argv[1:4]
continent = {}
for line in open(country_txt, encoding="utf-8"):
    if line.startswith("#"): continue
    f = line.rstrip("\n").split("\t")
    if len(f) > 8: continent[f[0]] = f[8]

places = []
tile_places = defaultdict(list)   # (cc, tile) -> [(pop, name)] over every inhabited place, small ones included
for line in open(cities_txt, encoding="utf-8"):
    f = line.rstrip("\n").split("\t")
    cc, fcode, pop = f[8], f[7], int(f[14] or 0)
    if cc not in continent: continue
    places.append(dict(name=f[1], cc=cc, lat=float(f[4]), lon=float(f[5]), pop=pop, fcode=fcode))
    if fcode != "PPLX":
        gh = geohash(float(f[4]), float(f[5]), 4)
        for n in range(1, 5): tile_places[(cc, gh[:n])].append((pop, f[1]))

by_cc = defaultdict(list)
for p in places: by_cc[p["cc"]].append(p)

rows = []   # (place, parent_place or None)
for cc, ps in sorted(by_cc.items()):
    ps.sort(key=lambda p: -p["pop"])
    big = [p for p in ps if p["pop"] >= PARENT_MIN_POP and p["fcode"] != "PPLX"]
    # "Paris 15 Vaugirard" is big too, but it is a part of "Paris", not a parent
    def part_of(p, q): return p is not q and p["name"].startswith(q["name"] + " ") and km((p["lat"], p["lon"]), (q["lat"], q["lon"])) <= DISTRICT_RADIUS_KM
    parents = [p for p in big if not any(part_of(p, q) for q in big)]
    district_of = {}
    for p in ps:
        if p in parents: continue
        best = None
        for q in parents:
            d = km((p["lat"], p["lon"]), (q["lat"], q["lon"]))
            if d > DISTRICT_RADIUS_KM: continue
            # PPLX = section of a populated place; "Paris 15 Vaugirard" = arrondissement of "Paris"
            if p["fcode"] == "PPLX" or p["name"].startswith(q["name"] + " "):
                if best is None or d < best[0]: best = (d, q)
        if best: district_of[id(p)] = best[1]
    cities = [p for p in ps if id(p) not in district_of and p["fcode"] != "PPLX"]
    keep = [p for i, p in enumerate(cities) if p["pop"] >= CITY_MIN_POP or i < COUNTRY_MIN_CITIES or p["fcode"] == "PPLC"]
    kept = {id(p) for p in keep}
    for c in keep:
        rows.append((c, None))
        ds = [p for p in ps if district_of.get(id(p)) is c]
        # GeoNames often has the same district twice ("Paris 10 Entrepôt", "Paris 10e Arrondissement")
        def number(p):
            m = re.match(re.escape(c["name"]) + r" 0*(\d+)", p["name"])
            return m.group(1) if m else None
        best = {}
        for p in ds:
            n = number(p)
            if n and (n not in best or p["pop"] > best[n]["pop"]): best[n] = p
        ds = [p for p in ds if number(p) is None or best[number(p)] is p]
        ds.sort(key=lambda p: p["name"])
        rows += [(d, c) for d in ds]

index = {}
with open(out_path, "w", encoding="utf-8") as out:
    out.write("# chatbit zone directory, data (c) GeoNames, CC BY 4.0\n")
    for cc in sorted(by_cc): out.write(f"C\t{cc}\t{continent[cc]}\n")
    for i, (p, parent) in enumerate(rows):
        index[id(p)] = i
        par = index[id(parent)] if parent else -1
        name = p["name"].replace("\t", " ")
        out.write(f"P\t{name}\t{p['cc']}\t{geohash(p['lat'], p['lon'])}\t{p['pop']}\t{par}\n")
    for cc in sorted(by_cc):
        out.write(f"K\t{cc}\n")
        for (c2, tile), lst in sorted(tile_places.items()):
            if c2 != cc: continue
            lst.sort(reverse=True)
            names = []
            for _, n in lst:
                if n not in names: names.append(n.replace("|", " ").replace("\t", " "))
                if len(names) == (3 if len(tile) < 4 else 2): break
            out.write(f"T\t{tile}\t{'|'.join(names)}\n")
print(len(tile_places), "tiles,", len(by_cc), "countries,", sum(1 for r in rows if r[1] is None), "cities,", sum(1 for r in rows if r[1]), "districts")
