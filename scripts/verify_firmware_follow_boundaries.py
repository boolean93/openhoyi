# Requires unicorn==2.1.4; user supplies firmware, never accesses devices/network.
import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--firmware',required=True)
parser.add_argument('--output',required=True)
args=parser.parse_args()
"""Original ARM instructions; finite state/target/stop-cleanup evidence, NOT hardware safety certification."""
import struct,json,hashlib
from pathlib import Path
from unicorn import *
from unicorn.arm_const import *
ROOT=Path(__file__).parent
S=0x20000760; R=S+0xa0; END=0x0801f000
blob=Path(args.firmware).read_bytes()
from firmware_follow_profile import address_profile
code_address=address_profile(blob)
def run(press,state,V,t=0,count=4,time=5,v1=5,v2=10,v3=10,total=100,bloom=0,stored=False):
 R=S+(0x6c if stored else 0xa0)
 u=Uc(UC_ARCH_ARM,UC_MODE_THUMB)
 for a,n in [(0x08000000,0x20000),(0x20000000,0x10000),(0x40000000,0x100000),(0x42200000,0x40000)]:u.mem_map(a,n)
 u.mem_write(0x08005000,blob)
 def w8(a,v):u.mem_write(a,bytes([v]))
 def f(a,v):u.mem_write(a,struct.pack('<f',v))
 def r8(a):return u.mem_read(a,1)[0]
 def rf(a):return struct.unpack('<f',u.mem_read(a,4))[0]
 skipped=[]
 def hook(uc,addr,size,data):
  if addr==END:uc.emu_stop()
  # Only flow-controller initialization is isolated; selectors, pressure init and floating math execute.
  elif addr==code_address(0x0800c970):
   skipped.append(hex(addr));uc.reg_write(UC_ARM_REG_PC,uc.reg_read(UC_ARM_REG_LR))
 u.hook_add(UC_HOOK_CODE,hook)
 for a,v in [(S+0x12,0 if press else 1),(S+0x2f0,0 if stored else 1),(S+0x28,2),(S+0x69,0 if stored else 7),(S+0x6a,0 if stored else 1),(R,press),(R+1,0),(R+2,0),(R+3,bloom),(R+4,count),(R+0x10,time),(S+0x131,state),(S+0x132,t),(S+0x133,1)]:w8(a,v)
 for a,v in [(R+0xc,total),(R+0x14,v1),(R+0x18,v2),(R+0x1c,v3),(R+0x24,3),(R+0x28,4),(R+0x2c,5),(R+0x30,6),(S+0x13c,V),(S+0x12c,80)]:f(a,v)
 u.reg_write(UC_ARM_REG_SP,0x2000f000);u.reg_write(UC_ARM_REG_LR,END|1)
 u.emu_start(code_address(0x0800e385),END,count=100000)
 assert u.reg_read(UC_ARM_REG_PC)==END,'instruction budget exhausted'
 return dict(state=r8(S+0x131),running=r8(S+0x28),timer=r8(S+0x133),temporary=r8(S+0x6a),pump_command=rf(S+0x12c),V=rf(S+0x13c),v1=rf(R+0x14),pressure_target=rf(S+0x190),flow_target=rf(S+0x150),skipped=skipped)
cases=[
 ('initialization',dict(state=0,V=0),1),
 ('first_time_equal',dict(state=1,V=2,t=5,v1=30),1),
 ('first_time_exceeded',dict(state=1,V=2,t=6,v1=30),2),
 ('first_volume_equal',dict(state=1,V=5,t=0),1),
 ('first_volume_exceeded',dict(state=1,V=5.1,t=0),2),
 ('first_total_equal',dict(state=1,V=100,v1=200,t=0),9),
 ('first_max_time_equal',dict(state=1,V=2,v1=30,t=254,time=254),1),
 ('first_max_time_exceeded',dict(state=1,V=2,v1=30,t=255,time=254),2),
 ('bloom_before',dict(state=2,V=5,t=2,bloom=3),2),
 ('bloom_at',dict(state=2,V=5,t=3,bloom=3),3),
 ('second_volume_equal',dict(state=3,V=15),3),
 ('second_volume_exceeded',dict(state=3,V=15.1),4),
 ('second_total_equal',dict(state=3,V=100,v2=200),9),
 ('second_two_segments_total',dict(state=3,V=100,count=2),9),
 ('third_volume_equal',dict(state=4,V=25),5),
 ('third_total_equal',dict(state=4,V=100,v3=200),9),
 ('third_three_segments_total',dict(state=4,V=100,count=3),9),
 ('fourth_below_total',dict(state=5,V=99.9),5),
 ('fourth_total_equal',dict(state=5,V=100),9),
 ('fourth_total_exceeded',dict(state=5,V=101),9),
 ('finish_cleanup',dict(state=9,V=0),0),
]
results=[]
for name,kw,expected in cases:
 a,b=run(0,**kw),run(1,**kw)
 stored_a,stored_b=run(0,stored=True,**kw),run(1,stored=True,**kw)
 assert stored_a==stored_b,(name,stored_a,stored_b)
 assert {k:v for k,v in b.items() if k!="temporary"}=={k:v for k,v in stored_b.items() if k!="temporary"},(name,b,stored_b)
 assert a==b,(name,a,b)
 assert b['state']==expected,(name,b,expected)
 if name=='initialization':assert b['pressure_target']==3 and b['flow_target']==0
 if name=='finish_cleanup':assert all(b[k]==0 for k in ['running','timer','temporary','pump_command'])
 if name=='bloom_before':assert b['pressure_target']==0
 if name=='first_time_exceeded':assert b['v1']==2
 results.append(dict(name=name,inputs=kw,expected=expected,constant=a,follow=b,stored_follow=stored_b,pass_=True))
report=dict(firmware_sha256=hashlib.sha256(blob).hexdigest(),scope='original state machine, selectors and pressure initialization; flow initialization isolated; no physical pump or timer integration',cases=results)
Path(args.output).write_text(json.dumps(report,indent=2))
print('PASS',len(results),'paired constant/follow boundary cases; firmware',report['firmware_sha256'])
