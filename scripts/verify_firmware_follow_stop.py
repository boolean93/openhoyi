# Requires unicorn==2.1.4; user supplies firmware, never accesses devices/network.
import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--firmware',required=True)
parser.add_argument('--output',required=True)
args=parser.parse_args()
"""Original 1.1.4 dispatch -> phase end -> idle pump-write chain; no hardware."""
from pathlib import Path
from unicorn import *
from unicorn.arm_const import *
import struct,json
import hashlib
blob=Path(args.firmware).read_bytes()
from firmware_follow_profile import address_profile
code_address=address_profile(blob)
S=0x20000760;R=S+0xa0
results=[]
for press in [0,1]:
 for state in [1,3,4,5]:
  u=Uc(UC_ARCH_ARM,UC_MODE_THUMB)
  for a,n in [(0x08000000,0x20000),(0x20000000,0x10000),(0x40000000,0x100000),(0x42200000,0x40000)]:u.mem_map(a,n)
  u.mem_write(0x08005000,blob)
  def w8(a,v):u.mem_write(a,bytes([v]))
  def f(a,v):u.mem_write(a,struct.pack('<f',v))
  for a,v in [(S+0x12,press),(S+0x28,2),(S+0x69,7),(S+0x6a,1),(R,press),(R+1,0),(R+2,0),(R+4,4),(R+0x10,254),(S+0x131,state),(S+0x133,1)]:w8(a,v)
  for a,v in [(R+0xc,100),(R+0x14,200),(R+0x18,200),(R+0x1c,200),(R+0x24,3),(R+0x28,4),(R+0x2c,5),(R+0x30,6),(S+0x13c,100),(S+0x12c,80)]:f(a,v)
  outputs=[]
  def hook(uc,addr,size,data):
   if addr==code_address(0x0800f3cc):uc.emu_stop()
   elif addr==code_address(0x0800878c):
    outputs.append(uc.reg_read(UC_ARM_REG_R0));uc.reg_write(UC_ARM_REG_PC,uc.reg_read(UC_ARM_REG_LR))
   elif addr==code_address(0x0800c970):uc.reg_write(UC_ARM_REG_PC,uc.reg_read(UC_ARM_REG_LR))
  u.hook_add(UC_HOOK_CODE,hook)
  states=[]
  for cycle in range(3):
   u.reg_write(UC_ARM_REG_SP,0x2000f000)
   u.emu_start(code_address(0x0800f35b),code_address(0x0800f3cc),count=200000)
   assert u.reg_read(UC_ARM_REG_PC)==code_address(0x0800f3cc),'instruction budget exhausted'
   states.append(u.mem_read(S+0x131,1)[0])
  assert states==[9,0,0],(press,state,states)
  assert outputs==[0],(press,state,outputs)
  results.append(dict(press=press,initial_stage=state,states=states,pump_write_arguments=outputs))
Path(args.output).write_text(json.dumps(dict(firmware_sha256=hashlib.sha256(blob).hexdigest(),cases=results),indent=2))
print('PASS',len(results),'original dispatch chains: total reached -> end -> idle output(0)')
