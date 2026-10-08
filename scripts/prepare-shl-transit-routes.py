from pathlib import Path
import csv,json,zlib,zipfile,hashlib,collections,datetime
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'app/src/androidTest/java/com/ecostep/app/evaluation/data/transit'
manifest=json.loads((DATA/'source-manifest.json').read_text())
def lines(name):
    decoder=zlib.decompressobj(-15); pending=b''; crc=0; total=0
    with (DATA/(name+'.deflate')).open('rb') as stream:
        while block:=stream.read(65536):
            decoded=decoder.decompress(block); crc=zlib.crc32(decoded,crc);total+=len(decoded)
            pieces=(pending+decoded).split(b'\n');pending=pieces.pop()
            for line in pieces: yield line.decode('utf-8-sig')+'\n'
    tail=decoder.flush();crc=zlib.crc32(tail,crc);total+=len(tail);pending+=tail
    if pending:yield pending.decode('utf8')
    expected=manifest['entries'][name]
    if total!=expected['bytes'] or f'{crc:08x}'!=expected['crc32'] or not decoder.eof:raise RuntimeError('Corrupt source '+name)
def rows(name):return csv.DictReader(lines(name))
# Geographical selection uses GPS only, never labels or predictions.
coords=[]
with zipfile.ZipFile(ROOT/'app/src/androidTest/java/com/ecostep/app/evaluation/data/SHL-preview-evaluation.zip') as z:
    for name in z.namelist():
        if name.endswith('/Hand_Location.txt'):
            for line in z.open(name):
                f=line.split()
                if len(f)==7:
                    lat,lon=float(f[4]),float(f[5])
                    if 49<=lat<=61 and -9<=lon<=3: coords.append((lat,lon))
bounds=[min(p[0] for p in coords)-.05,min(p[1] for p in coords)-.08,max(p[0] for p in coords)+.05,max(p[1] for p in coords)+.08]
del coords
stops={}
for row in rows('stops.txt'):
    try:lat,lon=float(row['stop_lat']),float(row['stop_lon'])
    except ValueError:continue
    if bounds[0]<=lat<=bounds[2] and bounds[1]<=lon<=bounds[3]:stops[row['stop_id']]=(lat,lon,row['stop_name'])
print('Regional stops',len(stops),bounds,flush=True)
routes={r['route_id']:r for r in rows('routes.txt')}
agencies={r['agency_id']:r for r in rows('agency.txt')}
feed=list(rows('feed_info.txt'))
def mode(value):
    x=int(value)
    if x==0 or 900<=x<1000:return 'TRAM'
    if x==1 or 400<=x<500:return 'SUBWAY'
    if x==2 or 100<=x<200:return 'RAIL'
    if x==3 or 200<=x<300 or 700<=x<800:return 'BUS'
    return None
supported={k:mode(v['route_type']) for k,v in routes.items() if mode(v['route_type'])}
trip_routes={}
for index,row in enumerate(rows('trips.txt')):
    if row['route_id'] in supported:trip_routes[row['trip_id']]=row['route_id']
    if index and index%1000000==0:print('Trips read',index,flush=True)
print('Supported trips',len(trip_routes),flush=True)
# Published GTFS stop_times are streamed by trip. Guard against non-contiguous trips.
patterns={}; current=None; seq=[]; finished=set(); rows_seen=0

def finish(trip,points):
    route=trip_routes.get(trip)
    if route is None or len(points)<2:return
    points.sort()
    ids=tuple(sid for order,sid in points)
    if len(set(ids))<2:return
    key=(route,ids)
    if key not in patterns:
        detail=routes[route]
        patterns[key]={'id':route+':'+hashlib.sha256(('\0'.join(ids)).encode()).hexdigest()[:16],'routeId':route,'mode':supported[route],'name':detail.get('route_short_name') or detail.get('route_long_name'),'agency':agencies.get(detail.get('agency_id'),{}).get('agency_name'),'representativeTripId':trip,'stopIds':ids,'stops':[[stops[sid][0],stops[sid][1]] for sid in ids]}
for index,row in enumerate(rows('stop_times.txt')):
    trip=row['trip_id']
    if trip!=current:
        if current is not None:
            finish(current,seq);finished.add(current)
        if trip in finished:raise RuntimeError('Non-contiguous trip in stop_times: '+trip)
        current=trip;seq=[]
    if row['stop_id'] in stops:seq.append((int(row['stop_sequence']),row['stop_id']))
    if index and index%5000000==0:print('Stop times read',index,'unique patterns',len(patterns),flush=True)
    rows_seen=index+1
finish(current,seq)
metadata={'source':manifest,'feedInfo':feed,'regionBounds':bounds,'selection':'All supported BUS, RAIL, SUBWAY and TRAM sequences with at least two published stops inside GPS bounds plus a fixed margin; relative GTFS stop_sequence preserved. No labels used. Patterns deduplicated by route_id and ordered stop IDs. No service-date or time filtering: spatial feasibility experiment only.','regionalStops':len(stops),'allSourceStopTimeRows':rows_seen,'patternsByMode':dict(collections.Counter(p['mode'] for p in patterns.values()))}
output={'metadata':metadata,'routes':sorted(patterns.values(),key=lambda x:x['id'])}
path=DATA/'transit-routes.json'
path.write_text(json.dumps(output,separators=(',',':'),ensure_ascii=True),encoding='utf8')
print(json.dumps({'output':str(path),'bytes':path.stat().st_size,'patterns':len(patterns),'modes':metadata['patternsByMode']}),flush=True)
