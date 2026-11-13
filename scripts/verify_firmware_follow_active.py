import argparse,hashlib
parser=argparse.ArgumentParser()
parser.add_argument('--firmware',required=True)
parser.add_argument('--output',required=True)
args=parser.parse_args()
from pathlib import Path
from unicorn import *
from unicorn.arm_const import *
import struct,json,math
blob=Path(args.firmware).read_bytes()
from firmware_follow_profile import address_profile
code_address=address_profile(blob)
S=0x20000760;R=S+0xa0;END=0x0801f000
results=[]
for chart,press in [(0,1),(1,0),(1,1)]:
 for target in [0.5,3,9,12]:
  for measured in [0,0.5,3,9,12,15]:
   u=Uc(UC_ARCH_ARM,UC_MODE_THUMB)
   for a,n in [(0x08000000,0x20000),(0x20000000,0x10000),(0x40000000,0x100000),(0x42200000,0x40000)]:u.mem_map(a,n)
   u.mem_write(0x08005000,blob)
   def w8(o,v):u.mem_write(S+o,bytes([v]))
   def f(o,v):u.mem_write(S+o,struct.pack('<f',v))
   for o,v in [(0x12,1),(0x28,2),(0x69,7),(0x6a,1),(0xa0,press),(0xa1,chart),(0xa4,4),(0xb0,5)]:w8(o,v)
   for o,v in [(0xac,100),(0xb4,30),(0xb8,30),(0xbc,30),(0xc4,target),(0xc8,target),(0xcc,target),(0xd0,target),(0x118,measured)]:f(o,v)
   outputs=[];calls=[]
   def hook(uc,addr,size,data):
    if addr==END:uc.emu_stop()
    elif addr==code_address(0x0800c29c):calls.append(uc.reg_read(UC_ARM_REG_R0))
    elif addr==code_address(0x0800878c):
     outputs.append(uc.reg_read(UC_ARM_REG_R0));uc.reg_write(UC_ARM_REG_PC,uc.reg_read(UC_ARM_REG_LR))
    elif addr==code_address(0x0800c970):uc.reg_write(UC_ARM_REG_PC,uc.reg_read(UC_ARM_REG_LR))
   u.hook_add(UC_HOOK_CODE,hook)
   def invoke(start):
    u.reg_write(UC_ARM_REG_SP,0x2000f000);u.reg_write(UC_ARM_REG_LR,END|1);u.emu_start(start|1,END,count=200000);assert u.reg_read(UC_ARM_REG_PC)==END
   invoke(code_address(0x0800e384))
   assert u.mem_read(S+0x131,1)[0]==1
   for _ in range(20):invoke(code_address(0x0800e0a2))
   assert calls==[1]*20,(target,measured,calls)
   assert len(outputs)==20 and all(0<=n<=1000 for n in outputs),(target,measured,outputs)
   count=len(outputs);w8(0x28,0);invoke(code_address(0x0800e0a2))
   assert calls[-1]==0 and len(outputs)==count
   results.append(dict(chart=chart,press=press,target=target,measured=measured,outputs=outputs,inactive_additional_outputs=0))
for target in [0.5,3,9,12]:
 for measured in [0,0.5,3,9,12,15]:
  pair=[r for r in results if r['chart']==1 and r['target']==target and r['measured']==measured]
  assert len(pair)==2 and pair[0]['outputs']==pair[1]['outputs']
Path(args.output).write_text(json.dumps(dict(firmware_sha256=hashlib.sha256(blob).hexdigest(),cases=results),indent=2))
print('PASS',len(results),'follow/mixed-first-pressure cases; first-pressure output independent of flow fast/slow' )
