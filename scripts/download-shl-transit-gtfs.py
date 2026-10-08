from pathlib import Path
import urllib.request, io, zipfile, json, hashlib, datetime, zlib, csv, collections, time
from concurrent.futures import ThreadPoolExecutor
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/src/androidTest/java/com/ecostep/app/evaluation/data/transit'
OUT.mkdir(parents=True,exist_ok=True)
URL='https://beta.aubin.app/gtfs/great_britain_gtfs.zip'
with urllib.request.urlopen(urllib.request.Request(URL,method='HEAD'),timeout=30) as r:
    SIZE=int(r.headers['Content-Length']); ETAG=r.headers['ETag']; MODIFIED=r.headers.get('Last-Modified')
def request_range(start,end):
    request=urllib.request.Request(URL,headers={'Range':f'bytes={start}-{end}','If-Range':ETAG,'User-Agent':'EcoStep/0.1 academic offline evaluation'})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request,timeout=60) as response:
                if response.status!=206: raise RuntimeError('Source changed or Range unsupported')
                data=response.read()
            if len(data)!=end-start+1: raise RuntimeError('Incomplete range')
            return data
        except Exception:
            if attempt==2: raise
            time.sleep(2)
class Remote(io.RawIOBase):
    def __init__(self): self.pos=0
    def seekable(self): return True
    def seek(self,n,w=0): self.pos=n if w==0 else self.pos+n if w==1 else SIZE+n; return self.pos
    def tell(self): return self.pos
    def read(self,n=-1):
        if n<0:n=SIZE-self.pos
        if not n:return b''
        data=request_range(self.pos,min(SIZE-1,self.pos+n-1));self.pos+=len(data);return data
with zipfile.ZipFile(Remote()) as z: infos={i.filename:i for i in z.infolist()}
manifest={'url':URL,'sourceCatalog':'https://raw.githubusercontent.com/public-transport/transitous/main/feeds/gb.json','attribution':'https://transitous.org/sources-great-britain/','etag':ETAG,'lastModified':MODIFIED,'retrievedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'historicalCompatibility':'Current feed, not 2017. Spatial route evidence only; calendar and departures are not used.','entries':{}}
for name in ['feed_info.txt','agency.txt','routes.txt','stops.txt','trips.txt','stop_times.txt']:
    info=infos[name]
    header=request_range(info.header_offset,info.header_offset+29)
    start=info.header_offset+30+int.from_bytes(header[26:28],'little')+int.from_bytes(header[28:30],'little')
    path=OUT/(name+'.deflate')
    offset0=path.stat().st_size if path.exists() else 0
    if offset0>info.compress_size: raise RuntimeError('Oversized cached entry')
    if offset0<info.compress_size:
        offsets=range(offset0,info.compress_size,8*1024*1024)
        def download_chunk(offset):
            return request_range(start+offset,start+min(info.compress_size,offset+8*1024*1024)-1)
        with path.open('ab') as stream, ThreadPoolExecutor(max_workers=4) as pool:
            for offset,data in zip(offsets,pool.map(download_chunk,offsets)):
                stream.write(data); stream.flush()
                print(f'Download {name}: {offset+len(data)}/{info.compress_size}',flush=True)
    sha=hashlib.file_digest(path.open('rb'),'sha256').hexdigest() if hasattr(hashlib,'file_digest') else hashlib.sha256(path.read_bytes()).hexdigest()
    manifest['entries'][name]={'compressedBytes':info.compress_size,'bytes':info.file_size,'crc32':f'{info.CRC:08x}','compressedSha256':sha,'method':info.compress_type}
(OUT/'source-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
print('All selected public GTFS tables downloaded',flush=True)
