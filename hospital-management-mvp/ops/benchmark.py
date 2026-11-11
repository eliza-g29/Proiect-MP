"""Read-only latency/concurrency probe. Use an existing token; never embeds credentials."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import os
import statistics
import time
from urllib.request import Request, urlopen

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--base',default='http://localhost:3000')
    parser.add_argument('--requests',type=int,default=40)
    parser.add_argument('--concurrency',type=int,default=8)
    args=parser.parse_args()
    token=os.environ.get('MEDFLOW_TOKEN')
    if not token: parser.error('Set MEDFLOW_TOKEN to an existing authenticated token')
    def probe(i):
        path=['/api/dashboard','/api/patients','/api/appointments','/api/encounters'][i%4]
        start=time.perf_counter()
        try:
            with urlopen(Request(args.base+path,headers={'Authorization':'Bearer '+token}),timeout=10) as response:
                response.read()
                return time.perf_counter()-start,response.status
        except Exception as error:
            return time.perf_counter()-start,str(error)
    with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        results=list(executor.map(probe,range(args.requests)))
    times=sorted(t for t,_ in results)
    failed=[status for _,status in results if status!=200]
    output={'requests':len(times),'concurrency':args.concurrency,'maxSeconds':max(times),
            'medianSeconds':statistics.median(times),'p95Seconds':times[max(0,int(len(times)*.95)-1)],
            'errors':failed,'within3Seconds':not failed and max(times)<=3}
    print(json.dumps(output,indent=2))
    raise SystemExit(0 if output['within3Seconds'] else 1)
if __name__=='__main__': main()
