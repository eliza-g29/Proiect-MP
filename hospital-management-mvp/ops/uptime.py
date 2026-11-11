"""External health sampling, with monthly observed availability (not an SLA guarantee)."""
import argparse
import calendar
import datetime as dt
import json
from pathlib import Path
import sqlite3
import time
from urllib.request import urlopen

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--base',default='http://localhost:3000')
    parser.add_argument('--database',default='data/uptime.sqlite')
    parser.add_argument('--interval',type=int,default=60)
    parser.add_argument('--month',help='Report UTC month YYYY-MM; planned maintenance samples are excluded')
    parser.add_argument('--maintenance',action='store_true',help='Mark these samples as planned maintenance')
    parser.add_argument('--once',action='store_true')
    args=parser.parse_args()
    if args.interval<10:parser.error('Minimum interval is 10 seconds')
    Path(args.database).parent.mkdir(parents=True,exist_ok=True)
    db=sqlite3.connect(args.database)
    db.execute('CREATE TABLE IF NOT EXISTS samples (at INTEGER PRIMARY KEY,ok INTEGER NOT NULL,maintenance INTEGER NOT NULL)')
    if args.month:
        start=dt.datetime.strptime(args.month,'%Y-%m').replace(tzinfo=dt.timezone.utc)
        end=(start+dt.timedelta(days=calendar.monthrange(start.year,start.month)[1])).timestamp()
        total,up=db.execute('SELECT count(*),COALESCE(sum(ok),0) FROM samples WHERE at>=? AND at<? AND maintenance=0',(start.timestamp(),end)).fetchone()
        print(json.dumps({'month':args.month,'observedSamples':total,'observedAvailabilityPercent':100*up/total if total else None,'targetPercent':99.5,'samplingIntervalSeconds':args.interval,'measurementIsNotAnSLAGuarantee':True},indent=2))
        return
    while True:
        ok=False
        try:
            with urlopen(args.base+'/api/health',timeout=5) as response:ok=response.status==200
        except Exception:pass
        db.execute('INSERT OR REPLACE INTO samples VALUES (?,?,?)',(int(time.time()),int(ok),int(args.maintenance)));db.commit()
        print(json.dumps({'at':dt.datetime.now(dt.timezone.utc).isoformat(),'ok':ok,'maintenance':args.maintenance}),flush=True)
        if args.once:break
        time.sleep(args.interval)
if __name__=='__main__':main()
